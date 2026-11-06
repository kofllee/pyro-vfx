package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
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
                && (d.render().sprite().blendMode() == VfxBlendMode.ADDITIVE
                    || d.render().sprite().blendMode() == VfxBlendMode.ALPHA)
                // Shared point light is exact with one probe. Spatially
                // spread and moving GPU sprites use an exact, bounded block
                // light volume when its voxel cost is acceptable, otherwise
                // the renderer draws through Minecraft's lit fallback.
                && (d.render().facing() == VfxFacingMode.CAMERA
                    || d.render().facing() == VfxFacingMode.CAMERA_HORIZONTAL
                    || d.render().facing() == VfxFacingMode.WORLD_X
                    || d.render().facing() == VfxFacingMode.WORLD_Y
                    || d.render().facing() == VfxFacingMode.WORLD_Z
                    || d.render().facing() == VfxFacingMode.VELOCITY);
        if (!sprite && !model)
            return reject("GPU analytic path requires a compatible alpha/additive sprite or an unlit baked opaque/cutout/additive model");
        if (model && (!d.render().appearance().color().isSharedParticleAgeOnly()
                || d.render().model().renderLayer() != VfxModelRenderLayer.ADDITIVE
                && !d.render().appearance().color().hasConstantOpaqueAlpha()))
            return reject("resident model colors must share particle age; solid meshes need opaque alpha");
        // Emitter lifetime and emission scheduling remain CPU-side. Each GPU
        // cohort retains a separate birth tick, so LOOPING and MANUAL births
        // have the same particle-age semantics as ONCE/STEADY.
        if (!d.triggers().isEmpty() || !d.particleTriggers().isEmpty())
            return reject("emitter/particle triggers require ordered CPU events");
        if (d.rotation().mode() != VfxRotationMode.NONE
                && !VfxAnalyticModelRotation.supports(d.rotation()))
            return reject("rotation needs per-particle or runtime evaluation");
        if (d.motion().collision().collide())
            return reject("collision queries require Minecraft world state");
        var uv = sprite ? d.render().sprite().uv() : null;
        // Every cohort owns one immutable UV sheet configuration, sampled
        // from emitter parameters at birth. A random start frame depends
        // only on the already-sampled particle.random and is calculated by
        // the sprite vertex shader without per-frame instance uploads.
        if (sprite && uv.mode() != VfxUvMode.FULL
                && (!uv.textureSize().isEmitterBirthOnly()
                    || !uv.uvStart().isEmitterBirthOnly()
                    || !uv.uvSize().isEmitterBirthOnly()
                    || !uv.uvStep().isEmitterBirthOnly()
                    || !uv.frameCount().isEmitterBirthOnly()
                    || !uv.fps().isEmitterBirthOnly()))
            return reject("UV sheet coordinates or timing require runtime/per-particle evaluation");
        boolean sharedAppearance = d.render().appearance().color().isSharedParticleAgeOnly()
                && d.render().appearance().scale().isSharedParticleAgeOnly();
        boolean gpuBirthAppearance = sprite
                && d.render().appearance().color().isGpuBirthOrAgeOnly()
                && d.render().appearance().scale().isGpuBirthOrAgeOnly();
        if (!sharedAppearance && !gpuBirthAppearance)
            return reject("appearance requires unsupported mutable, curve or world-dependent evaluation");
        if (d.motion().mode() == VfxMotionMode.PARAMETRIC && !sharedAppearance)
            return reject("parametric motion with per-particle GPU appearance needs position-aware shader evaluation");
        boolean perParticleLifetime = d.particleLifetime().maxAgeTicks().isPerParticleBirthLifetime();
        if (!d.particleLifetime().maxAgeTicks().isEmitterBirthOnly() && !perParticleLifetime)
            return reject("particle lifetime depends on individual particle or runtime RNG");
        // A per-particle expiration mask is supported for additive sprites
        // with constant appearance and a full UV sheet. Per-lifetime curves,
        // animated UV and models need separate per-particle shader semantics.
        if (perParticleLifetime && (!sprite
                || !d.render().appearance().color().isConstant()
                || !d.render().appearance().scale().isConstant()
                || d.render().sprite().uv().mode() != VfxUvMode.FULL))
            return reject("random lifetimes require constant sprite appearance and full UV");
        if (perParticleLifetime && d.render().sprite() != null
                && d.render().sprite().blendMode() == VfxBlendMode.ALPHA)
            return reject("analytic alpha requires shared lifetime for stable depth sorting");
        // Spawn geometry is sampled once on the CPU and uploaded as immutable
        // birth positions. It does NOT need to be constant or GLSL-compatible.
        // This includes expressions using emitter/param/particle.random values.
        // MODEL geometry is sampled by the same CPU birth sampler as Dense,
        // then uploaded as immutable positions. Resource reload invalidates
        // the sampler cache; already-born GPU points retain their positions.
        double ax=0, ay=0, az=0, q=1;
        if (d.motion().mode() == VfxMotionMode.DYNAMIC) {
            var motion = d.motion().dynamic();
            // These are sampled once per birth cohort, not each rendered
            // frame: mutable parameters may change for subsequent cohorts.
            // Per-particle RNG, age or world-dependent coefficients still
            // require the CPU solver.
            if (!motion.acceleration().isEmitterBirthOnly()
                    || !motion.linearDrag().isEmitterBirthOnly())
                return reject("dynamic acceleration and drag require emitter birth-only coefficients");
            if (motion.acceleration().isConstant()) {
                var a = motion.acceleration().evaluate(null);
                ax = a.x() * VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK;
                ay = a.y() * VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK;
                az = a.z() * VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK;
            }
            if (motion.linearDrag().isConstant())
                q = Math.exp(-motion.linearDrag().evaluateDouble(null) * VfxTime.SECONDS_PER_TICK);
        } else if (d.motion().mode() == VfxMotionMode.PARAMETRIC) {
            // Every particle in a cohort has the same lifetime/age; an
            // immutable age-only parametric offset can be sampled once per
            // render and uploaded as a vec3 uniform. No per-particle CPU tick.
            if (!d.motion().parametric().offset().isSharedParticleAgeOnly())
                return reject("parametric offset must depend only on shared particle age/lifetime");
        } else if (d.motion().mode() != VfxMotionMode.STATIC) {
            return reject("unsupported motion mode");
        }
        if (!Double.isFinite(q) || !Double.isFinite(ax)
                || !Double.isFinite(ay) || !Double.isFinite(az))
            return reject("nonfinite motion coefficients");
        int life = d.particleLifetime().maxAgeTicks().isConstant()
                ? Math.max(1, (int)Math.round(d.particleLifetime().maxAgeTicks().evaluateDouble(null)))
                : 1; // actual emitter-scoped lifetime evaluated for every birth cohort
        return new VfxAnalyticPlan(true,"per-birth-tick shared-age uniforms and closed-form or parametric motion",
                ax, ay, az, q, life);
    }

    /** Capture all shared mutable birth coefficients, not just lifetime. */
    public VfxAnalyticPlan forBirth(VfxEmitterDefinition emitter, VfxExpressionContext birthScope) {
        if (!supported) return this;
        int sampled = (emitter.particleLifetime().maxAgeTicks().isConstant()
                || emitter.particleLifetime().maxAgeTicks().isPerParticleBirthLifetime()) ? lifetime
                : Math.max(1, (int) Math.round(
                        emitter.particleLifetime().maxAgeTicks().evaluateDouble(birthScope)));
        double ax = accelerationX, ay = accelerationY, az = accelerationZ, drag = dragMultiplier;
        if (emitter.motion().mode() == VfxMotionMode.DYNAMIC) {
            var motion = emitter.motion().dynamic();
            if (!motion.acceleration().isConstant()) {
                var a = motion.acceleration().evaluate(birthScope);
                ax = a.x() * VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK;
                ay = a.y() * VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK;
                az = a.z() * VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK;
            }
            if (!motion.linearDrag().isConstant())
                drag = Math.exp(-motion.linearDrag().evaluateDouble(birthScope)
                        * VfxTime.SECONDS_PER_TICK);
        }
        if (!Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(az)
                || !Double.isFinite(drag))
            throw new IllegalArgumentException("Nonfinite GPU birth motion coefficients");
        return sampled == lifetime && ax == accelerationX && ay == accelerationY
                && az == accelerationZ && drag == dragMultiplier ? this
                : new VfxAnalyticPlan(true, reason, ax, ay, az, drag, sampled);
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
