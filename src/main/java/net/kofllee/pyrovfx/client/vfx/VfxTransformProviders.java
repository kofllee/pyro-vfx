package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.function.BiFunction;

/** Built-in strategies. Model-part animation poses require a real renderer/animation callback. */
public final class VfxTransformProviders {
    private VfxTransformProviders() {}

    public static VfxTransformProvider fixed(Vec3 position, Quaternionf rotation) {
        VfxTransform pose = new VfxTransform(position, rotation);
        return partial -> pose;
    }

    public static VfxTransformProvider fixed(Vec3 position) {
        VfxTransform pose = VfxTransform.at(position);
        return partial -> pose;
    }

    public static VfxTransformProvider entity(Entity entity) {
        if (!(entity.level() instanceof ClientLevel level))
            throw new IllegalArgumentException("Entity VFX attachment requires a client entity");
        WeakReference<ClientLevel> world = new WeakReference<>(level);
        UUID uuid = entity.getUUID();
        int id = entity.getId();
        return partial -> {
            ClientLevel active = world.get();
            if (active == null || net.minecraft.client.Minecraft.getInstance().level != active)
                return null;
            Entity tracked = active.getEntity(id);
            if (tracked == null || tracked.isRemoved() || !uuid.equals(tracked.getUUID()))
                return null;
            float t = Mth.clamp(partial, 0.0f, 1.0f);
            Vec3 position = new Vec3(
                    Mth.lerp(t, tracked.xo, tracked.getX()),
                    Mth.lerp(t, tracked.yo, tracked.getY()),
                    Mth.lerp(t, tracked.zo, tracked.getZ()));
            float yaw = Mth.rotLerp(t, tracked.yRotO, tracked.getYRot());
            float pitch = Mth.rotLerp(t, tracked.xRotO, tracked.getXRot());
            Quaternionf rotation = new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(-yaw), (float) Math.toRadians(pitch), 0.0f);
            return new VfxTransform(position, rotation);
        };
    }

    public static VfxTransformProvider block(ClientLevel level, BlockPos position) {
        WeakReference<ClientLevel> world = new WeakReference<>(level);
        BlockPos pos = position.immutable();
        var attachedBlock = level.getBlockState(pos).getBlock();
        return partial -> {
            ClientLevel active = world.get();
            if (active == null || net.minecraft.client.Minecraft.getInstance().level != active
                    || !loadedClientChunk(active, pos) || active.getBlockState(pos).isAir()
                    || active.getBlockState(pos).getBlock() != attachedBlock) return null;
            return VfxTransform.at(Vec3.atCenterOf(pos));
        };
    }

    /**
     * ClientLevel.hasChunk(int,int) unconditionally returns true in 1.21.1;
     * it cannot be used to check whether a client-side attachment was unloaded.
     */
    private static boolean loadedClientChunk(ClientLevel world, BlockPos position) {
        return world.getChunkSource().getChunk(
                position.getX() >> 4, position.getZ() >> 4,
                ChunkStatus.FULL, false) != null;
    }

    public static VfxTransformProvider blockEntity(BlockEntity entity) {
        if (!(entity.getLevel() instanceof ClientLevel level))
            throw new IllegalArgumentException("Block entity VFX attachment requires a client level");
        WeakReference<ClientLevel> world = new WeakReference<>(level);
        WeakReference<BlockEntity> reference = new WeakReference<>(entity);
        BlockPos pos = entity.getBlockPos().immutable();
        return partial -> {
            ClientLevel active = world.get();
            BlockEntity target = reference.get();
            if (active == null || target == null || target.isRemoved()
                    || net.minecraft.client.Minecraft.getInstance().level != active
                    || !loadedClientChunk(active, pos) || active.getBlockEntity(pos) != target) return null;
            return VfxTransform.at(Vec3.atCenterOf(pos));
        };
    }

    /**
     * For a specific animated model part, the owning renderer supplies its
     * actual world-space pose after animation setup. No named bone lookup is
     * claimed: vanilla ModelPart has no universal entity-to-bone transform API.
     * Third-party animation mods may implement the callback themselves.
     */
    public static VfxTransformProvider modelPart(Entity entity,
                                                 BiFunction<Entity, Float, VfxTransform> samplePartPose) {
        VfxTransformProvider owner = entity(entity);
        WeakReference<Entity> reference = new WeakReference<>(entity);
        return partial -> {
            if (owner.get(partial) == null) return null;
            Entity live = reference.get();
            return live == null ? null : samplePartPose.apply(live, partial);
        };
    }
}