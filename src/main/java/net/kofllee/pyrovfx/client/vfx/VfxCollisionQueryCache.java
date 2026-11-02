package net.kofllee.pyrovfx.client.vfx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.Arrays;

/**
 * A bounded per-client-tick cache of exact voxel-shape boxes. Full cubes use
 * one shared singleton; multipart blocks are tested box-by-box after a cheap
 * union-bounds broadphase. No shapes/box arrays are rebuilt on cache hits.
 *
 * The VFX tick runs synchronously on the client thread. No data survives from
 * one tick into another and no Minecraft world reads occur off-thread.
 */
public final class VfxCollisionQueryCache {
    private static final int CAPACITY = 8192; // power of two; direct-mapped, no growth
    private static final boolean DISABLED = Boolean.getBoolean("pyrovfx.collision.disableCache");
    private static final long[] KEYS = new long[CAPACITY];
    private record CachedShape(AABB bounds, AABB[] parts) {}
    private static final CachedShape FULL_CUBE =
            new CachedShape(new AABB(0,0,0,1,1,1), null);
    private static final CachedShape[] SHAPES = new CachedShape[CAPACITY];
    private static final int[] EPOCHS = new int[CAPACITY];

    private static ClientLevel world;
    private static int epoch;
    private static long hits;
    private static long misses;

    private VfxCollisionQueryCache() {}

    /** Call before updating any effect on each Minecraft client tick. */
    public static void beginTick(ClientLevel level) {
        world = level;
        if (++epoch <= 0) {
            Arrays.fill(EPOCHS, 0);
            epoch = 1;
        }
        hits = misses = 0;
    }

    public static void clear() {
        world = null;
        epoch = 0;
        Arrays.fill(EPOCHS, 0);
        Arrays.fill(SHAPES, null);
        hits = misses = 0;
    }

    static AABB bounds(ClientLevel level, BlockPos.MutableBlockPos pos) {
        CachedShape shape = cachedShape(level, pos);
        return shape == null ? null : shape.bounds();
    }

    static boolean intersectsWorld(ClientLevel level, BlockPos.MutableBlockPos pos, AABB box) {
        CachedShape shape = cachedShape(level, pos);
        return shape != null && intersectsShape(shape.bounds(), shape.parts(),
                pos.getX(), pos.getY(), pos.getZ(), box);
    }

    private static CachedShape cachedShape(ClientLevel level, BlockPos.MutableBlockPos pos) {
        if (world != level || epoch == 0 || DISABLED) {
            misses++;
            return queryWorld(level, pos);
        }
        long key = pos.asLong();
        long mix = (key ^ (key >>> 30)) * 0xbf58476d1ce4e5b9L;
        int index = (int) (mix ^ (mix >>> 27)) & (CAPACITY - 1);
        if (EPOCHS[index] == epoch && KEYS[index] == key) {
            hits++;
            return SHAPES[index];
        }
        misses++;
        CachedShape result = queryWorld(level, pos);
        KEYS[index] = key;
        SHAPES[index] = result;
        EPOCHS[index] = epoch;
        return result;
    }

    private static CachedShape queryWorld(ClientLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return null;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return null;
        if (shape == Shapes.block()) return FULL_CUBE;
        var parts = shape.toAabbs();
        if (parts.isEmpty()) return null;
        // A complex voxel shape can have disjoint sub-boxes: its union
        // bounding box alone would report collisions inside hollow gaps.
        return new CachedShape(shape.bounds(),
                parts.size() == 1 ? null : parts.toArray(AABB[]::new));
    }

    /** Broadphase followed by exact intersection with every sub-box if needed. */
    public static boolean intersectsShape(AABB bounds, AABB[] parts,
                                          int x, int y, int z, AABB box) {
        if (!intersects(bounds, x, y, z, box)) return false;
        if (parts == null) return true;
        for (AABB part : parts) {
            if (intersects(part, x, y, z, box)) return true;
        }
        return false;
    }

    /** Local AABB vs world AABB, no per-query temporary translated box. */
    public static boolean intersects(AABB local, int x, int y, int z, AABB box) {
        return local != null
                && box.minX < local.maxX + x && box.maxX > local.minX + x
                && box.minY < local.maxY + y && box.maxY > local.minY + y
                && box.minZ < local.maxZ + z && box.maxZ > local.minZ + z;
    }

    public static long hits() { return hits; }
    public static long misses() { return misses; }
}