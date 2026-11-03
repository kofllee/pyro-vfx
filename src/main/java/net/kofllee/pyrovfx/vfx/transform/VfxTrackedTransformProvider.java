package net.kofllee.pyrovfx.vfx.transform;

import net.kofllee.pyrovfx.client.ClientVfxAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;

/**
 * Built-in source plus a composable local pose. Unlike an arbitrary Java callback,
 * this retains the source identity needed for client-side network reconstruction.
 * The sampler is used locally, never serialized.
 */
public final class VfxTrackedTransformProvider implements VfxTransformProvider {
    public enum Kind { ENTITY, BLOCK, BLOCK_ENTITY, FIXED }

    private final Kind kind;
    private final WeakReference<Level> level;
    private final WeakReference<Entity> entity;
    private final WeakReference<BlockEntity> blockEntity;
    private final BlockPos blockPos;
    private final VfxTransform fixed;
    private final VfxTransformProvider sampler;
    private final VfxTransform local;
    private final String socket;

    private VfxTrackedTransformProvider(Kind kind, Level level, Entity entity,
                                        BlockEntity blockEntity, BlockPos blockPos,
                                        VfxTransform fixed, VfxTransformProvider sampler,
                                        VfxTransform local, String socket) {
        this.kind = kind;
        this.level = new WeakReference<>(level);
        this.entity = new WeakReference<>(entity);
        this.blockEntity = new WeakReference<>(blockEntity);
        this.blockPos = blockPos == null ? null : blockPos.immutable();
        this.fixed = fixed;
        this.sampler = sampler;
        this.local = local;
        this.socket = socket;
    }

    public static VfxTrackedTransformProvider entity(Entity entity, VfxTransformProvider sampler) {
        return new VfxTrackedTransformProvider(Kind.ENTITY, entity.level(), entity, null,
                null, null, sampler, VfxTransform.at(Vec3.ZERO), null);
    }

    public static VfxTrackedTransformProvider block(Level level, BlockPos pos, VfxTransformProvider sampler) {
        return new VfxTrackedTransformProvider(Kind.BLOCK, level, null, null,
                pos, null, sampler, VfxTransform.at(Vec3.ZERO), null);
    }

    public static VfxTrackedTransformProvider blockEntity(BlockEntity entity, VfxTransformProvider sampler) {
        return new VfxTrackedTransformProvider(Kind.BLOCK_ENTITY, entity.getLevel(), null,
                entity, entity.getBlockPos(), null, sampler, VfxTransform.at(Vec3.ZERO), null);
    }

    public static VfxTrackedTransformProvider fixed(Level level, VfxTransform pose) {
        return new VfxTrackedTransformProvider(Kind.FIXED, level, null, null, null,
                pose, partialTick -> pose, VfxTransform.at(Vec3.ZERO), null);
    }

    public VfxTrackedTransformProvider withLocal(VfxTransform next) {
        return new VfxTrackedTransformProvider(kind, level.get(), entity.get(),
                blockEntity.get(), blockPos, fixed, sampler, local.compose(next), socket);
    }

    public VfxTrackedTransformProvider withSocket(String name) {
        if (kind != Kind.ENTITY && kind != Kind.BLOCK_ENTITY)
            throw new IllegalStateException("Named sockets require an Entity or BlockEntity");
        if (name == null || name.isBlank() || name.length() > 128
                || !name.matches("[a-z0-9_.:-]+(?:/[a-z0-9_.:-]+)*"))
            throw new IllegalArgumentException("Invalid VFX socket: " + name);
        return new VfxTrackedTransformProvider(kind, level.get(), entity.get(),
                blockEntity.get(), blockPos, fixed, sampler, local, name);
    }

    public Kind kind() { return kind; }
    public Level level() { return level.get(); }
    public Entity entity() { return entity.get(); }
    public BlockEntity blockEntity() { return blockEntity.get(); }
    public BlockPos blockPos() { return blockPos; }
    public VfxTransform fixedPose() { return fixed; }
    public VfxTransform local() { return local; }
    public String socketName() { return socket; }

    @Override public VfxTransform get(float partialTick) {
        VfxTransform root = sampler.get(partialTick);
        if (root != null && socket != null && level.get() != null && level.get().isClientSide())
            root = ClientVfxAccess.sampleSocket(entity.get(), blockEntity.get(), socket, partialTick);
        return root == null ? null : root.compose(local);
    }
}