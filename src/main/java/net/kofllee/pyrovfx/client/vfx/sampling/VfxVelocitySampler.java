package net.kofllee.pyrovfx.client.vfx.sampling;

import net.kofllee.pyrovfx.vfx.definition.VfxVelocityDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxVelocityMode;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VfxVelocitySampler {
    private VfxVelocitySampler() {}

    public static Vec3 sample(VfxVelocityDefinition velocity, Vec3 emitterPosition, Vec3 particlePosition, RandomSource random) {
        if(velocity.mode() == VfxVelocityMode.NONE){
            return Vec3.ZERO;
        }

        if(velocity.mode() == VfxVelocityMode.CONSTANT){
            return sampleConstant(velocity, random);
        }

        if(velocity.mode() == VfxVelocityMode.RANDOM){
            VfxRandom.direction(random).scale(sampleSpeed(velocity, random));
        }

        if(velocity.mode() == VfxVelocityMode.SPHERICAL){
            Vec3 direction = particlePosition.subtract(emitterPosition);

            if(direction.lengthSqr() < 0.0001){
                direction = VfxRandom.direction(random);
            }
            else{
                direction = direction.normalize();
            }

            return direction.scale(sampleSpeed(velocity, random));
        }

        return Vec3.ZERO;
    }

    private static Vec3 sampleConstant(VfxVelocityDefinition velocity, RandomSource random) {
        Vec3 direction = velocity.direction().toVec3();

        if(direction.lengthSqr() < 0.0001){
            return Vec3.ZERO;
        }

        return direction.normalize().scale(sampleSpeed(velocity, random));
    }

    private static double sampleSpeed(VfxVelocityDefinition velocity, RandomSource random) {
        return velocity.speed() + VfxRandom.triangle(random) * velocity.speedRandom();
    }
}
