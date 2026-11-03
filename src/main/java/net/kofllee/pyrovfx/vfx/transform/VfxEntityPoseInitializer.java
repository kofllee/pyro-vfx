package net.kofllee.pyrovfx.vfx.transform;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Entity model particles render a dummy entity without running its AI or
 * entity ticks. Initial interpolation history must therefore be synchronized
 * once at creation: otherwise vanilla interpolates e.g. 0 -> randomized
 * head yaw every render frame, then snaps back to 0 next game tick.
 */
public final class VfxEntityPoseInitializer {
    private VfxEntityPoseInitializer() {}

    public static void synchronizeUntickedPose(Entity entity) {
        entity.xRotO = entity.getXRot();
        entity.yRotO = entity.getYRot();
        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
        }
    }
}