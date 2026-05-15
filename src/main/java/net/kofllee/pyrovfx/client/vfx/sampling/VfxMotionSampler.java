package net.kofllee.pyrovfx.client.vfx.sampling;

import net.kofllee.pyrovfx.client.vfx.VfxTime;
import net.kofllee.pyrovfx.vfx.definition.VfxDynamicMotionDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxMotionDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxVelocityDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxDirectionMode;
import net.kofllee.pyrovfx.vfx.type.VfxMotionMode;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VfxMotionSampler {
    private VfxMotionSampler() {}

    public static Vec3 sampleInitialVelocity(
            VfxMotionDefinition motion,
            Vec3 emitterPosition,
            Vec3 particlePosition,
            VfxExpressionContext context,
            RandomSource random
    ) {
        if (motion.mode() == VfxMotionMode.STATIC || motion.mode() == VfxMotionMode.PARAMETRIC) {
            return Vec3.ZERO;
        }

        if (motion.mode() == VfxMotionMode.DYNAMIC) {
            return sampleDynamic(motion.dynamic(), emitterPosition, particlePosition, context, random);
        }

        return Vec3.ZERO;
    }


    private static Vec3 sampleDynamic(
            VfxDynamicMotionDefinition motion,
            Vec3 emitterPosition,
            Vec3 particlePosition,
            VfxExpressionContext context,
            RandomSource random
    ) {
        Vec3 direction = sampleDirection(motion, emitterPosition, particlePosition, context, random);

        double speedPerSecond = motion.speed().evaluate(context);
        double speedPerTick = VfxTime.blocksPerSecondToBlocksPerTick(speedPerSecond);

        return direction.normalize().scale(speedPerTick);
    }

    private static Vec3 sampleDirection(VfxDynamicMotionDefinition motion, Vec3 emitterPosition, Vec3 particlePosition, VfxExpressionContext context, RandomSource random) {
        VfxDirectionMode direction = motion.direction();

        if (direction == VfxDirectionMode.CUSTOM) {
            return motion.customDirection().evaluate(context).toVec3();
        }

        if(direction == VfxDirectionMode.OUTWARD) {
            Vec3 outward = particlePosition.subtract(emitterPosition);
            if (outward.lengthSqr() < 1e-6) {
                return VfxRandom.direction(random);
            }

            return outward;
        }

        if (direction == VfxDirectionMode.INWARD) {
            Vec3 inward = emitterPosition.subtract(particlePosition);

            if (inward.lengthSqr() < 0.0001) {
                return VfxRandom.direction(random);
            }

            return inward;
        }

        if (direction == VfxDirectionMode.UP) {
            return new Vec3(0.0, 1.0, 0.0);
        }

        if (direction == VfxDirectionMode.DOWN) {
            return new Vec3(0.0, -1.0, 0.0);
        }

        return Vec3.ZERO;
    }

    private static double sampleSpeed(VfxVelocityDefinition velocity, RandomSource random) {
        return velocity.speed() + VfxRandom.triangle(random) * velocity.speedRandom();
    }
}
