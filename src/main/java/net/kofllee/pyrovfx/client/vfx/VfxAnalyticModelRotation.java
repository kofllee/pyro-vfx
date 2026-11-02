package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxRotationDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxRotationMode;
import net.minecraft.world.phys.Vec3;

/** Shared angular motion for one analytic model cohort, evaluated once per draw. */
public final class VfxAnalyticModelRotation {
    private final Vec3 start;
    private final Vec3 velocityPerTick;
    private final Vec3 accelerationPerTickSquared;
    private final VfxAnalyticPlan coefficients;

    private VfxAnalyticModelRotation(Vec3 start, Vec3 velocityPerTick,
                                     Vec3 accelerationPerTickSquared, double drag) {
        this.start = start;
        this.velocityPerTick = velocityPerTick;
        this.accelerationPerTickSquared = accelerationPerTickSquared;
        this.coefficients = new VfxAnalyticPlan(true, "angular", 0, 0, 0,
                Math.exp(-drag * VfxTime.SECONDS_PER_TICK), 1);
    }

    public static boolean supports(VfxRotationDefinition definition) {
        if (definition.mode() == VfxRotationMode.NONE) return true;
        if (definition.mode() != VfxRotationMode.DYNAMIC) return false;
        var dynamic = definition.dynamic();
        return dynamic.startRotation().isConstant()
                && dynamic.angularVelocity().isConstant()
                && dynamic.angularAcceleration().isConstant()
                && dynamic.angularDrag().isConstant();
    }

    public static VfxAnalyticModelRotation from(VfxRotationDefinition definition) {
        if (!supports(definition)) {
            throw new IllegalArgumentException("Model rotation depends on per-particle or runtime state");
        }
        if (definition.mode() == VfxRotationMode.NONE) {
            return new VfxAnalyticModelRotation(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 0);
        }
        var dynamic = definition.dynamic();
        double dt = VfxTime.SECONDS_PER_TICK;
        return new VfxAnalyticModelRotation(
                dynamic.startRotation().evaluate(null).toVec3(),
                dynamic.angularVelocity().evaluate(null).toVec3().scale(dt),
                dynamic.angularAcceleration().evaluate(null).toVec3().scale(dt * dt),
                dynamic.angularDrag().evaluateDouble(null));
    }

    public Vec3 angles(int age, float partial) {
        double[] coefficientsAtAge = coefficients.coefficients(age, partial);
        return start.add(velocityPerTick.scale(coefficientsAtAge[0]))
                .add(accelerationPerTickSquared.scale(coefficientsAtAge[1]));
    }
}
