package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.type.*;

/**
 * Explicit conservative eligibility analysis: no GPU expression evaluation is
 * claimed for expressions not translatable to this closed-form motion kernel.
 */
public record VfxAnalyticPlan(boolean supported, String reason,
                              double accelerationX, double accelerationY,
                              double accelerationZ, double dragMultiplier,
                              int lifetime) {
    private static VfxAnalyticPlan reject(String why) {
        return new VfxAnalyticPlan(false, why, 0, 0, 0, 1, 0);
    }
    public static VfxAnalyticPlan analyse(VfxEmitterDefinition d, boolean gpuAvailable) {
        if (!gpuAvailable) return reject("GPU instancing unavailable, renderer disabled or incompatible mod");
        boolean model = d.render().type() == VfxRenderType.MODEL
                && d.render().model() != null
                && d.render().model().source() != VfxModelSourceType.ENTITY
                && d.render().model().renderLayer() != VfxModelRenderLayer.TRANSLUCENT
                && !d.render().environmentLighting();
        boolean sprite = d.render().type() == VfxRenderType.SPRITE
                && d.render().sprite() != null
                && d.render().sprite().blendMode() == VfxBlendMode.ADDITIVE
                && !d.render().environmentLighting()
                && (d.render().facing() == VfxFacingMode.CAMERA
                    || d.render().facing() == VfxFacingMode.CAMERA_HORIZONTAL
                    || d.render().facing() == VfxFacingMode.WORLD_Z);
        if (!sprite && !model)
            return reject("GPU resident path requires an additive sprite or unlit baked opaque/cutout/additive model");
        if (model && (!d.render().appearance().color().isConstant()
                    || d.render().appearance().color().evaluate(null).a() != 1.0))
            return reject("model resident kernel requires constant opaque color");
        if (sprite && (d.render().facing() != VfxFacingMode.CAMERA
                    && d.render().facing() != VfxFacingMode.CAMERA_HORIZONTAL
                    && d.render().facing() != VfxFacingMode.WORLD_Z))
            return reject("additive full-bright camera-facing sprite required");
        if (d.emitterLifetime().mode() != VfxEmitterLifetimeMode.ONCE
                || (d.spawnAmount().mode() != VfxSpawnAmountMode.INSTANT
                    && d.spawnAmount().mode() != VfxSpawnAmountMode.STEADY))
            return reject("analytic backend supports once-lived instant or steady emitters");
        if (!d.triggers().isEmpty() || !d.particleTriggers().isEmpty())
            return reject("emitter/particle triggers require ordered CPU events");
        if (d.rotation().mode() != VfxRotationMode.NONE
                && (!model || !VfxAnalyticModelRotation.supports(d.rotation())))
            return reject("rotation needs per-particle or runtime evaluation");
        if (d.motion().collision().collide())
            return reject("collision queries require Minecraft world state");
        var uv = sprite ? d.render().sprite().uv() : null;
        // An instant burst has a shared age. Deterministic static/animated
        // frames can therefore be updated by one uniform, not per particle.
        // Per-particle random start frames still require the dense CPU path.
        if (sprite && uv.mode() != VfxUvMode.FULL
                && (uv.randomStartFrame()
                    || !uv.textureSize().isConstant()
                    || !uv.uvStart().isConstant()
                    || !uv.uvSize().isConstant()
                    || !uv.uvStep().isConstant()
                    || !uv.frameCount().isConstant()
                    || !uv.fps().isConstant()))
            return reject("UV animation requires identical constant sheet parameters and no random start frame");
        boolean sharedAppearance = d.render().appearance().color().isSharedParticleAgeOnly()
                && d.render().appearance().scale().isSharedParticleAgeOnly();
        boolean gpuBirthAppearance = sprite
                && d.render().appearance().color().isGpuBirthOrAgeOnly()
                && d.render().appearance().scale().isGpuBirthOrAgeOnly();
        if (!sharedAppearance && !gpuBirthAppearance)
            return reject("appearance requires unsupported mutable, curve or world-dependent evaluation");
        if (!d.particleLifetime().maxAgeTicks().isConstant())
            return reject("particle lifetime is not constant");
        if (!d.spawnShape().radius().isConstant() || !d.spawnShape().innerRadius().isConstant()
                || !d.spawnShape().height().isConstant()
                || !d.spawnShape().halfExtents().isConstant()
                || !d.spawnShape().axis().isConstant()
                || !d.spawnShape().edgeThickness().isConstant()
                || !d.spawnShape().scale().isConstant()
                || d.spawnShape().type() == VfxSpawnShapeType.MODEL)
            return reject("spawn shape needs per-particle expression or model sampling");
        double ax=0, ay=0, az=0, q=1;
        if (d.motion().mode() == VfxMotionMode.DYNAMIC) {
            var motion = d.motion().dynamic();
            if (!motion.speed().isConstant() || !motion.acceleration().isConstant()
                    || !motion.linearDrag().isConstant()
                    || !motion.customDirection().isConstant())
                return reject("motion depends on runtime expressions or parameters");
            var a = motion.acceleration().evaluate(null);
            ax = a.x() * VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK;
            ay = a.y() * VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK;
            az = a.z() * VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK;
            q = Math.exp(-motion.linearDrag().evaluateDouble(null) * VfxTime.SECONDS_PER_TICK);
        } else if (d.motion().mode() != VfxMotionMode.STATIC) {
            return reject("parametric motion needs CPU evaluation");
        }
        if (!Double.isFinite(q) || !Double.isFinite(ax)
                || !Double.isFinite(ay) || !Double.isFinite(az))
            return reject("nonfinite motion coefficients");
        int life = Math.max(1, (int)Math.round(d.particleLifetime().maxAgeTicks().evaluateDouble(null)));
        return new VfxAnalyticPlan(true,"per-birth-tick shared-age uniforms and closed-form discrete motion",
                ax, ay, az, q, life);
    }

    /**
     * Position after n ticks: initial + v0*C_v(n) + a_tick2*C_a(n).
     * Identical recurrence to the dense runtime: v=(v+a_tick2)*q; p+=v.
     * Interpolation uses the previous and current integer positions.
     */
    public double[] coefficients(int age, float partial) {
        double[] previous = coefficientsAt(Math.max(0, age - 1));
        double[] current = coefficientsAt(Math.max(0, age));
        return new double[]{
            Math.fma(current[0] - previous[0], partial, previous[0]),
            Math.fma(current[1] - previous[1], partial, previous[1])
        };
    }

    public double[] coefficientsAt(int n) {
        double cv, ca;
        double q = dragMultiplier;
        if (Math.abs(q - 1.0) < 1e-9) {
            cv=n; ca=n*(n+1.0)/2.0;
        } else {
            double pow = Math.pow(q,n);
            cv=q*(1.0-pow)/(1.0-q);
            ca=q*(n-cv)/(1.0-q);
        }
        return new double[]{cv,ca};
    }

    public boolean useGpu() { return supported; }
}
