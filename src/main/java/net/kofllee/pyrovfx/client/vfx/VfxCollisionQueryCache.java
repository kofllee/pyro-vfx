package net.kofllee.pyrovfx.client.vfx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;

/**
 * A bounded, allocation-free *per-client-tick* cache for exact block collision
 * shape bounds. The old collision rule used VoxelShape.bounds(), not voxel
 * iteration; preserve precisely that rule for the optimized path.
 *
 * The VFX tick runs synchronously on the client thread. No data survives from
 * one tick into another and no Minecraft world reads occur off-thread.
 */
public final class VfxCollisionQueryCache {
    private static final int CAPACITY = 8192; // power of two; direct-mapped, no growth
    private static final boolean DISABLED = Boolean.getBoolean("pyrovfx.collision.disableCache");
    private static final long[] KEYS = new long[CAPACITY];
    private static final AABB[] LOCAL_BOUNDS = new AABB[CAPACITY]; // null means air/empty
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
        Arrays.fill(LOCAL_BOUNDS, null);
        hits = misses = 0;
    }

    static AABB bounds(ClientLevel level, BlockPos.MutableBlockPos pos) {
        if (world != level || epoch == 0 || DISABLED) {
            misses++;
            return queryWorld(level, pos);
        }
        long key = pos.asLong();
        long mix = (key ^ (key >>> 30)) * 0xbf58476d1ce4e5b9L;
        int index = (int) (mix ^ (mix >>> 27)) & (CAPACITY - 1);
        if (EPOCHS[index] == epoch && KEYS[index] == key) {
            hits++;
            return LOCAL_BOUNDS[index];
        }
        misses++;
        AABB result = queryWorld(level, pos);
        KEYS[index] = key;
        LOCAL_BOUNDS[index] = result;
        EPOCHS[index] = epoch;
        return result;
    }

    private static AABB queryWorld(ClientLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir()) return null;
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty() ? null : shape.bounds();
    }

    /** Exact AABB intersects(bound.move(blockPos)) equivalent without a temporary AABB. */
    public static boolean intersects(AABB local, int x, int y, int z, AABB box) {
        return local != null
                && box.minX < local.maxX + x && box.maxX > local.minX + x
                && box.minY < local.maxY + y && box.maxY > local.minY + y
                && box.minZ < local.maxZ + z && box.maxZ > local.minZ + z;
    }

    public static long hits() { return hits; }
    public static long misses() { return misses; }
}