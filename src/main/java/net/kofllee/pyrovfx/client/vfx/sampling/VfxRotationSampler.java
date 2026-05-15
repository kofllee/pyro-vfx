package net.kofllee.pyrovfx.client.vfx.sampling;

import net.kofllee.pyrovfx.client.vfx.VfxTime;
import net.kofllee.pyrovfx.vfx.definition.VfxRotationDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxRotationMode;
import net.minecraft.world.phys.Vec3;

public final class VfxRotationSampler {
    private VfxRotationSampler() {}

    public static Vec3 sampleInitialRotation(VfxRotationDefinition rotation, VfxExpressionContext context) {
        if(rotation.mode() == VfxRotationMode.NONE)
            return Vec3.ZERO;

        if(rotation.mode() == VfxRotationMode.PARAMETRIC){
            return Vec3.ZERO;
        }

        if(rotation.mode() == VfxRotationMode.DYNAMIC)
            return rotation.dynamic().startRotation().evaluate(context).toVec3();

        return Vec3.ZERO;
    }

    public static Vec3 sampleInitialAngularVelocity(VfxRotationDefinition rotation, VfxExpressionContext context) {
        if(rotation.mode() == VfxRotationMode.NONE)
            return Vec3.ZERO;

        Vec3 degreesPerSecond = rotation.dynamic().angularVelocity().evaluate(context).toVec3();

        return new Vec3(
                VfxTime.degreesPerSecondToDegreesPerTick(degreesPerSecond.x),
                VfxTime.degreesPerSecondToDegreesPerTick(degreesPerSecond.y),
                VfxTime.degreesPerSecondToDegreesPerTick(degreesPerSecond.z)
        );
    }
}
