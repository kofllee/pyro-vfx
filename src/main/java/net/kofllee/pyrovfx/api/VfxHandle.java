package net.kofllee.pyrovfx.api;

import net.kofllee.pyrovfx.network.VfxRemoteHandle;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One instance handle for client-local and server-broadcast effects.
 * The server delegate holds only a weak reference to its level, never a particle or entity.
 * Call controls on the owning logical game thread.
 */
public final class VfxHandle {
    private final long id;
    private final VfxRemoteHandle remote;

    VfxHandle(long id) {
        this.id = id;
        this.remote = null;
    }

    VfxHandle(VfxRemoteHandle remote) {
        this.id = 0;
        this.remote = remote;
    }

    static VfxHandle inactive() { return new VfxHandle(0); }

    /** False for unknown effects, rejected starts, finished instances and stopped effects. */
    public boolean isActive() { return remote != null ? remote.isActive() : id != 0 && Vfx.active(id); }

    /** Stops immediately and frees runtime resources. Repeated calls return false. */
    public boolean stop() { return remote != null ? remote.stop() : id != 0 && Vfx.stop(id); }

    /** Changes an existing numeric parameter; unknown names or non-finite values return false. */
    public boolean setParameter(String name, double value) {
        return remote != null ? remote.setParameter(name, value) : id != 0 && Vfx.parameter(id, name, value);
    }

    /** Queues a named effect event for the next client tick, subject to runtime budgets. */
    public boolean trigger(String eventName) {
        return remote != null ? remote.trigger(eventName) : id != 0 && Vfx.trigger(id, eventName);
    }

    /** Arbitrary Java providers are client-local; use an Entity or explicit pose on the server. */
    public boolean attach(VfxTransformProvider source) {
        return remote == null && id != 0 && Vfx.attach(id, source);
    }

    public boolean attach(Entity entity) {
        return remote != null ? remote.attach(entity) : id != 0 && Vfx.attach(id, entity);
    }

    public boolean attach(BlockPos block) {
        return remote != null ? remote.attach(block) : id != 0 && Vfx.attach(id, block);
    }

    public boolean attach(BlockEntity blockEntity) {
        return remote != null ? remote.attach(blockEntity) : id != 0 && Vfx.attach(id, blockEntity);
    }

    public boolean attach(Vec3 position) {
        return position != null && attach(VfxTransform.at(position));
    }

    public boolean attach(VfxTransform pose) {
        return remote != null ? remote.attach(pose)
                : id != 0 && pose != null && Vfx.attach(id, partialTick -> pose);
    }

    /** Explicit streaming for custom server poses; unnecessary for Entity/Block attachments. */
    public boolean attachStreamed(VfxTransform pose) {
        return remote != null && remote.attachStreamed(pose);
    }

    /** Sends a new pose for a streamed source, without a per-particle network packet. */
    public boolean updatePose(VfxTransform pose) {
        return remote != null && remote.updatePose(pose);
    }

    /** Removes attachment while keeping the last position and rotation, without stopping the effect. */
    public boolean detach() { return remote != null ? remote.detach() : id != 0 && Vfx.detach(id); }
}