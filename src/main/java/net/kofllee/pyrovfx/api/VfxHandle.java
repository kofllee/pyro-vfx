package net.kofllee.pyrovfx.api;

import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** An instance-specific control token. Does not retain the effect, its world or its source. */
public final class VfxHandle {
    private final long id;

    VfxHandle(long id) {
        this.id = id;
    }

    /** False for unknown effects, rejected starts, finished instances and stopped effects. */
    public boolean isActive() { return id != 0 && Vfx.active(id); }

    /** Stops immediately and frees runtime resources. Repeated calls return false. */
    public boolean stop() { return id != 0 && Vfx.stop(id); }

    /** Changes an existing numeric parameter; unknown names or non-finite values return false. */
    public boolean setParameter(String name, double value) { return id != 0 && Vfx.parameter(id, name, value); }

    /** Queues a named effect event for the next client tick, subject to runtime budgets. */
    public boolean trigger(String eventName) { return id != 0 && Vfx.trigger(id, eventName); }

    /** Switches to another source. The new source must be available now. */
    public boolean attach(VfxTransformProvider source) { return id != 0 && Vfx.attach(id, source); }

    public boolean attach(Entity entity) { return id != 0 && Vfx.attach(id, entity); }

    public boolean attach(BlockPos block) { return id != 0 && Vfx.attach(id, block); }

    public boolean attach(BlockEntity blockEntity) { return id != 0 && Vfx.attach(id, blockEntity); }

    public boolean attach(Vec3 position) { return id != 0 && Vfx.attach(id, position); }

    /** Removes attachment while keeping the last position and rotation, without stopping the effect. */
    public boolean detach() { return id != 0 && Vfx.detach(id); }
}