package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.VfxAnalyticSpriteRenderer;
import net.kofllee.pyrovfx.client.render.SpriteVfxParticleRenderer;
import net.kofllee.pyrovfx.client.render.ModelVfxParticleRenderer;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvState;
import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvResolver;
import net.kofllee.pyrovfx.client.render.VfxSpriteUvRect;
import net.kofllee.pyrovfx.client.render.VfxSpriteUvSampler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxMotionSampler;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxSpawnPositionSampler;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.kofllee.pyrovfx.client.render.VfxRenderVisibility;
import org.joml.Matrix4f;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Six birth-state floats per particle; no per-particle work after spawning. */
public final class VfxAnalyticParticlePool {
    public static final int STRIDE = 24;
    private final VfxEmitterDefinition definition;
    private net.kofllee.pyrovfx.vfx.transform.VfxTransform sourceTransform =
            net.kofllee.pyrovfx.vfx.transform.VfxTransform.at(Vec3.ZERO);
    private net.kofllee.pyrovfx.vfx.transform.VfxTransform birthTransform =
            net.kofllee.pyrovfx.vfx.transform.VfxTransform.at(Vec3.ZERO);
    private final VfxAnalyticPlan plan;
    private final VfxAnalyticModelRotation sharedModelRotation;
    private Vec3 scale;
    private VfxColor color;
    private final ClientVfxSpriteUvState uvState;
    private final VfxExpressionContext sharedAgeContext = VfxExpressionContext.root();
    private final boolean animatedAppearance;
    private final boolean gpuBirthAppearance;
    private final boolean birthRandomAttribute;
    private final int stride;
    private ByteBuffer states;
    private int count, age, gpuBuffer;
    private int gpuGeneration = -1;
    private boolean dirty = true;
    // Conservative birth-state envelope, updated only when particles are born.
    // Analytic particles share motion coefficients, so the full cohort can be
    // rejected before GPU upload without walking every particle at render time.
    private double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY,
            minZ = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY,
            maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
    private double minVx = Double.POSITIVE_INFINITY, minVy = Double.POSITIVE_INFINITY,
            minVz = Double.POSITIVE_INFINITY, maxVx = Double.NEGATIVE_INFINITY,
            maxVy = Double.NEGATIVE_INFINITY, maxVz = Double.NEGATIVE_INFINITY;

    public VfxAnalyticParticlePool(VfxEmitterDefinition d, VfxAnalyticPlan p) {
        definition = d;
        plan = p;
        gpuBirthAppearance = d.render().type() == VfxRenderType.SPRITE
                && (!d.render().appearance().color().isSharedParticleAgeOnly()
                    || !d.render().appearance().scale().isSharedParticleAgeOnly())
                && d.render().appearance().color().isGpuBirthOrAgeOnly()
                && d.render().appearance().scale().isGpuBirthOrAgeOnly();
        birthRandomAttribute = gpuBirthAppearance
                && (d.render().appearance().color().glsl().contains("vfxRandom")
                    || d.render().appearance().scale().glslXY().contains("vfxRandom"));
        stride = birthRandomAttribute ? STRIDE + Float.BYTES : STRIDE;
        sharedModelRotation = d.render().type() == VfxRenderType.MODEL
                ? VfxAnalyticModelRotation.from(d.rotation()) : null;
        animatedAppearance = !gpuBirthAppearance
                && (!d.render().appearance().scale().isConstant()
                    || !d.render().appearance().color().isConstant());
        if (gpuBirthAppearance) {
            scale = new Vec3(1, 1, 1);
            color = new VfxColor(1, 1, 1, 1);
        } else if (animatedAppearance) {
            evaluateSharedAge(0);
        } else {
            scale = d.render().appearance().scale().evaluate(null).toVec3();
            color = d.render().appearance().color().evaluate(null);
        }
        uvState = d.render().sprite() == null ? null
                : ClientVfxSpriteUvResolver.resolve(d.render().sprite().uv(), null, 0.0);
    }

    public void spawn(int amount, Vec3 origin, VfxExpressionContext scope, RandomSource random) {
        spawn(amount, origin, scope, random,
                net.kofllee.pyrovfx.vfx.transform.VfxTransform.at(Vec3.ZERO));
    }

    public void spawn(int amount, Vec3 origin, VfxExpressionContext scope, RandomSource random,
                      net.kofllee.pyrovfx.vfx.transform.VfxTransform source) {
        if (amount <= 0) return;
        if (count == 0) {
            birthTransform = source;
            resetEnvelope();
        }
        int newCount = Math.addExact(count, amount);
        int needed = Math.multiplyExact(newCount, stride);
        if (states == null || needed > states.capacity()) {
            int capacity = Math.max(needed, states == null ? 4096 :
                    Math.min(Integer.MAX_VALUE - 16, states.capacity() + states.capacity() / 2));
            ByteBuffer next = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
            if (states != null) {
                states.position(0);
                states.limit(count * stride);
                next.put(states);
            }
            states = next;
        }
        states.clear();
        states.position(count * stride);
        for (int i = 0; i < amount; i++) {
            double particleRandom = random.nextDouble();
            Vec3 localOrigin = source.inverseTransform(origin);
            Vec3 pos = VfxSpawnPositionSampler.sample(localOrigin, definition.spawnShape(), scope, random);
            Vec3 vel = VfxMotionSampler.sampleInitialVelocity(definition.motion(), localOrigin, pos, scope, random);
            if (!localSpace()) {
                pos = source.transform(pos);
                vel = source.rotate(vel);
            }
            float x = (float) pos.x, y = (float) pos.y, z = (float) pos.z;
            float vx = (float) vel.x, vy = (float) vel.y, vz = (float) vel.z;
            states.putFloat(x).putFloat(y).putFloat(z);
            states.putFloat(vx).putFloat(vy).putFloat(vz);
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            minVx = Math.min(minVx, vx); minVy = Math.min(minVy, vy); minVz = Math.min(minVz, vz);
            maxVx = Math.max(maxVx, vx); maxVy = Math.max(maxVy, vy); maxVz = Math.max(maxVz, vz);
            if (birthRandomAttribute) states.putFloat((float) particleRandom);
        }
        count = newCount;
        dirty = true;
    }

    public void tick() {
        if (count != 0 && ++age >= plan.lifetime()) {
            count = 0;
            close();
        }
    }
    public int liveCount() { return count; }

    /**
     * Conservative cohort frustum test for constant/shared-age sprite sizes.
     * GPU birth-randomized sizes are deliberately not culled: no safe uniform
     * maximum can be inferred. Models have arbitrary baked geometry as well.
     * No particle lifetime, event or physics state is touched by this test.
     */
    public boolean mayBeVisible(Matrix4f mvp, Vec3 cameraPosition, float partialTick) {
        if (count == 0) return false;
        if (model() || gpuBirthAppearance || Boolean.getBoolean("pyrovfx.render.debugNoCulling"))
            return true;
        double[] c = plan.coefficients(age, partialTick);
        Vec3 acceleration = effectiveAcceleration();
        double x0 = minX + (c[0] >= 0 ? minVx : maxVx) * c[0] + acceleration.x * c[1];
        double y0 = minY + (c[0] >= 0 ? minVy : maxVy) * c[0] + acceleration.y * c[1];
        double z0 = minZ + (c[0] >= 0 ? minVz : maxVz) * c[0] + acceleration.z * c[1];
        double x1 = maxX + (c[0] >= 0 ? maxVx : minVx) * c[0] + acceleration.x * c[1];
        double y1 = maxY + (c[0] >= 0 ? maxVy : minVy) * c[0] + acceleration.y * c[1];
        double z1 = maxZ + (c[0] >= 0 ? maxVz : minVz) * c[0] + acceleration.z * c[1];
        double halfX = (x1 - x0) * 0.5, halfY = (y1 - y0) * 0.5,
                halfZ = (z1 - z0) * 0.5;
        // Recompute shared-age size only once per cohort, not per particle.
        updateAppearance(partialTick);
        double spriteRadius = Math.hypot(scale.x, scale.y) * 0.5;
        double radius = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ)
                + spriteRadius + 0.1; // float storage / interpolation margin
        if (!Double.isFinite(radius) || !Double.isFinite(x0 + x1 + y0 + y1 + z0 + z1))
            return true;
        Vec3 center = new Vec3((x0 + x1) * 0.5, (y0 + y1) * 0.5,
                (z0 + z1) * 0.5);
        if (localSpace()) center = sourceTransform.transform(center);
        return VfxRenderVisibility.visible(mvp, center.x - cameraPosition.x,
                center.y - cameraPosition.y, center.z - cameraPosition.z, radius);
    }

    private void resetEnvelope() {
        minX = minY = minZ = minVx = minVy = minVz = Double.POSITIVE_INFINITY;
        maxX = maxY = maxZ = maxVx = maxVy = maxVz = Double.NEGATIVE_INFINITY;
    }
    public boolean localSpace() {
        return definition.space() == net.kofllee.pyrovfx.vfx.type.VfxSpace.LOCAL;
    }
    public net.kofllee.pyrovfx.vfx.transform.VfxTransform sourceTransform() {
        return sourceTransform;
    }
    public void setSourceTransform(net.kofllee.pyrovfx.vfx.transform.VfxTransform transform) {
        sourceTransform = transform;
    }
    public Vec3 effectiveAcceleration() {
        Vec3 original = new Vec3(plan.accelerationX(), plan.accelerationY(), plan.accelerationZ());
        return localSpace() || !birthTransform.hasRotation()
                ? original : birthTransform.rotate(original);
    }
    public boolean model() { return definition.render().type() == VfxRenderType.MODEL; }
    public boolean gpuBirthAppearance() { return gpuBirthAppearance; }
    public boolean birthRandomAttribute() { return birthRandomAttribute; }
    public int stride() { return stride; }
    public String gpuColorGlsl() { return definition.render().appearance().color().glsl(); }
    public String gpuScaleGlsl() { return definition.render().appearance().scale().glslXY(); }
    public Vec3 modelRotation(float partial) {
        Vec3 particleRotation = sharedModelRotation == null
                ? Vec3.ZERO : sharedModelRotation.angles(age, partial);
        return !localSpace() && birthTransform.hasRotation()
                ? birthTransform.rotateAngles(particleRotation) : particleRotation;
    }
    /** Direct buffer capacity (not just currently live bytes). */
    public long allocatedStateBytes() { return states == null ? 0 : states.capacity(); }
    /** Uploaded birth-state payload only; excludes driver bookkeeping and mesh VBOs. */
    public long residentBufferBytes() { return gpuBuffer == 0 ? 0 : (long) count * stride; }
    public int age() { return age; }
    public VfxAnalyticPlan plan() { return plan; }
    public VfxEmitterDefinition definition() { return definition; }
    public Vec3 scale() { return scale; }
    public VfxColor color() { return color; }
    /**
     * Evaluate at most two shared ages per *emitter draw*, never per particle.
     * CPU particles begin with appearance(age=0), then tick evaluations use
     * pre-increment age: after tick 1 they still have age=0 appearance.
     * Match their previous/current interpolation after subsequent ticks.
     */
    public void updateAppearance(float partial) {
        if (!animatedAppearance) return;
        int currentAge = Math.max(0, age - 1);
        int previousAge = Math.max(0, age - 2);
        float factor = Math.max(0.0f, Math.min(1.0f, partial));
        evaluateSharedAge(previousAge);
        Vec3 previousScale = scale;
        VfxColor previousColor = color;
        evaluateSharedAge(currentAge);
        Vec3 currentScale = scale;
        VfxColor currentColor = color;
        scale = new Vec3(
                Math.fma(currentScale.x - previousScale.x, factor, previousScale.x),
                Math.fma(currentScale.y - previousScale.y, factor, previousScale.y),
                Math.fma(currentScale.z - previousScale.z, factor, previousScale.z));
        color = new VfxColor(
                Math.fma(currentColor.r() - previousColor.r(), factor, previousColor.r()),
                Math.fma(currentColor.g() - previousColor.g(), factor, previousColor.g()),
                Math.fma(currentColor.b() - previousColor.b(), factor, previousColor.b()),
                Math.fma(currentColor.a() - previousColor.a(), factor, previousColor.a()));
    }

    private void evaluateSharedAge(int sampledAge) {
        sharedAgeContext.setNumber("particle.age", sampledAge);
        sharedAgeContext.setNumber("particle.lifetime", plan.lifetime());
        sharedAgeContext.setNumber("particle.normalized_age",
                Math.min(1.0, sampledAge / (double) plan.lifetime()));
        scale = definition.render().appearance().scale().evaluate(sharedAgeContext).toVec3();
        color = definition.render().appearance().color().evaluate(sharedAgeContext);
    }
    /** One UV rectangle for this entire burst. The original frame scheduler is reused. */
    public VfxSpriteUvRect uvRect() {
        return VfxSpriteUvSampler.sample(new FallbackCursor(0, 0));
    }
    public ByteBuffer data() {
        if (states == null) return null;
        ByteBuffer view = states.asReadOnlyBuffer();
        view.position(0);
        view.limit(count * stride);
        return view;
    }
    public int gpuBuffer() { return gpuBuffer; }
    public int gpuGeneration() { return gpuGeneration; }
    public void setGpuBuffer(int id, int generation) {
        gpuBuffer = id; gpuGeneration = generation; dirty = false;
    }
    public boolean dirty() { return dirty; }
    public void reload() { gpuBuffer = 0; gpuGeneration = -1; dirty = true; }
    public void close() {
        if (gpuBuffer != 0) VfxAnalyticSpriteRenderer.retireBuffer(gpuBuffer);
        gpuBuffer = 0;
        gpuGeneration = -1;
        states = null;
        dirty = true;
    }

    /**
     * RenderType fallback, including for GL init failures after spawning.
     * Materializes only one reusable cursor; CPU per-particle work is limited
     * to this compatibility path, not the normal GPU analytic backend.
     */
    public void renderFallback(PoseStack pose, MultiBufferSource buffer,
                               Camera camera, float partial) {
        if (states == null || count == 0) return;
        updateAppearance(partial);
        double[] c = plan.coefficients(age, partial);
        Vec3 acceleration = effectiveAcceleration();
        if (model()) {
            Vec3 modelAngles = modelRotation(partial);
            if (localSpace() && sourceTransform.hasRotation()) {
                modelAngles = sourceTransform.rotateAngles(modelAngles);
            }
            for (int i = 0; i < count; i++) {
                int offset = i * STRIDE;
                Vec3 birth = new Vec3(states.getFloat(offset), states.getFloat(offset + 4),
                        states.getFloat(offset + 8));
                Vec3 velocity = new Vec3(states.getFloat(offset + 12),
                        states.getFloat(offset + 16), states.getFloat(offset + 20));
                Vec3 current = birth.add(velocity.scale(c[0])).add(
                        acceleration.x * c[1], acceleration.y * c[1],
                        acceleration.z * c[1]);
                if (localSpace()) {
                    birth = sourceTransform.transform(birth);
                    current = sourceTransform.transform(current);
                    velocity = sourceTransform.rotate(velocity);
                }
                ClientVfxParticle snapshot = ClientVfxParticle.denseSnapshot(definition,
                        birth, current, current, velocity, modelAngles, modelAngles,
                        scale, scale, color, color, 0, plan.lifetime(), age, null);
                ModelVfxParticleRenderer.render(snapshot, pose, buffer, camera, partial);
            }
            return;
        }
        FallbackCursor cursor = new FallbackCursor(c[0], c[1]);
        for (int i = 0; i < count; i++) {
            cursor.index = i;
            SpriteVfxParticleRenderer.render(cursor, pose, buffer, camera, partial);
        }
    }

    private final class FallbackCursor implements VfxSpriteParticleView {
        private final double cv, ca;
        private int index;
        private FallbackCursor(double cv, double ca) { this.cv=cv; this.ca=ca; }
        @Override public VfxEmitterDefinition emitterDefinition() { return definition; }
        @Override public Vec3 interpolatedPosition(float partial) {
            int offset = index * stride;
            Vec3 acceleration = effectiveAcceleration();
            Vec3 value = new Vec3(
                    states.getFloat(offset) + states.getFloat(offset+12)*cv + acceleration.x*ca,
                    states.getFloat(offset+4) + states.getFloat(offset+16)*cv + acceleration.y*ca,
                    states.getFloat(offset+8) + states.getFloat(offset+20)*cv + acceleration.z*ca);
            return localSpace() ? sourceTransform.transform(value) : value;
        }
        @Override public Vec3 interpolatedRotation(float partial) { return Vec3.ZERO; }
        private VfxExpressionContext particleAppearanceContext(int sampledAge, int positionTick) {
            VfxExpressionContext context = VfxExpressionContext.root();
            context.setNumber("particle.age", sampledAge);
            context.setNumber("particle.lifetime", plan.lifetime());
            context.setNumber("particle.normalized_age",
                    Math.min(1.0, sampledAge / (double) plan.lifetime()));
            context.setNumber("particle.random",
                    birthRandomAttribute ? states.getFloat(index * stride + STRIDE) : 0);
            int ticks = positionTick;
            var cv = plan.coefficientsAt(ticks);
            var previous = plan.coefficientsAt(Math.max(0, ticks - 1));
            int offset = index * stride;
            double bx = states.getFloat(offset), by = states.getFloat(offset + 4),
                    bz = states.getFloat(offset + 8);
            double initialVx = states.getFloat(offset + 12), initialVy = states.getFloat(offset + 16),
                    initialVz = states.getFloat(offset + 20);
            double q = Math.pow(plan.dragMultiplier(), ticks);
            context.setNumber("spawn.pos.x", bx);
            context.setNumber("spawn.pos.y", by);
            context.setNumber("spawn.pos.z", bz);
            context.setNumber("particle.pos.x", bx + initialVx * cv[0] + plan.accelerationX() * cv[1]);
            context.setNumber("particle.pos.y", by + initialVy * cv[0] + plan.accelerationY() * cv[1]);
            context.setNumber("particle.pos.z", bz + initialVz * cv[0] + plan.accelerationZ() * cv[1]);
            context.setNumber("particle.prev_pos.x", bx + initialVx * previous[0] + plan.accelerationX() * previous[1]);
            context.setNumber("particle.prev_pos.y", by + initialVy * previous[0] + plan.accelerationY() * previous[1]);
            context.setNumber("particle.prev_pos.z", bz + initialVz * previous[0] + plan.accelerationZ() * previous[1]);
            double vx = initialVx * q + plan.accelerationX() * cv[0];
            double vy = initialVy * q + plan.accelerationY() * cv[0];
            double vz = initialVz * q + plan.accelerationZ() * cv[0];
            context.setNumber("particle.vel.x", vx);
            context.setNumber("particle.vel.y", vy);
            context.setNumber("particle.vel.z", vz);
            context.setNumber("particle.speed", Math.sqrt(vx*vx + vy*vy + vz*vz));
            return context;
        }
        @Override public Vec3 interpolatedScale(float partial) {
            if (!gpuBirthAppearance) return scale;
            var appearance = definition.render().appearance().scale();
            Vec3 prev = appearance.evaluate(particleAppearanceContext(
                    Math.max(0, age - 2), Math.max(0, age - 1))).toVec3();
            Vec3 curr = appearance.evaluate(particleAppearanceContext(
                    Math.max(0, age - 1), age)).toVec3();
            return prev.lerp(curr, partial);
        }
        @Override public VfxColor interpolatedColor(float partial) {
            if (!gpuBirthAppearance) return color;
            var appearance = definition.render().appearance().color();
            VfxColor prev = appearance.evaluate(particleAppearanceContext(
                    Math.max(0, age - 2), Math.max(0, age - 1)));
            VfxColor curr = appearance.evaluate(particleAppearanceContext(
                    Math.max(0, age - 1), age));
            return new VfxColor(Math.fma(curr.r()-prev.r(), partial, prev.r()),
                    Math.fma(curr.g()-prev.g(), partial, prev.g()),
                    Math.fma(curr.b()-prev.b(), partial, prev.b()),
                    Math.fma(curr.a()-prev.a(), partial, prev.a()));
        }
        @Override public Vec3 velocity() {
            int offset = index*stride;
            Vec3 value = new Vec3(states.getFloat(offset+12),states.getFloat(offset+16),
                    states.getFloat(offset+20));
            return localSpace() ? sourceTransform.rotate(value) : value;
        }
        @Override public ClientVfxSpriteUvState spriteUv() { return uvState; }
        @Override public int age() { return age; }
        @Override public double ageNormalized() { return Math.min(1, age/(double)plan.lifetime()); }
    }
}
