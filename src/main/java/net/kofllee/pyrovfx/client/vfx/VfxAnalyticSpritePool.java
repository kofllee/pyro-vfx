package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.VfxAnalyticSpriteRenderer;
import net.kofllee.pyrovfx.client.render.SpriteVfxParticleRenderer;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Six birth-state floats per particle; no per-particle work after spawning. */
public final class VfxAnalyticSpritePool {
    public static final int STRIDE = 24;
    private final VfxEmitterDefinition definition;
    private final VfxAnalyticPlan plan;
    private Vec3 scale;
    private VfxColor color;
    private final ClientVfxSpriteUvState uvState;
    private final VfxExpressionContext sharedAgeContext = VfxExpressionContext.root();
    private final boolean animatedAppearance;
    private ByteBuffer states;
    private int count, age, gpuBuffer;
    private int gpuGeneration = -1;
    private boolean dirty = true;

    public VfxAnalyticSpritePool(VfxEmitterDefinition d, VfxAnalyticPlan p) {
        definition = d;
        plan = p;
        animatedAppearance = !d.render().appearance().scale().isConstant()
                || !d.render().appearance().color().isConstant();
        if (animatedAppearance) {
            evaluateSharedAge(0);
        } else {
            scale = d.render().appearance().scale().evaluate(null).toVec3();
            color = d.render().appearance().color().evaluate(null);
        }
        uvState = ClientVfxSpriteUvResolver.resolve(d.render().sprite().uv(), null, 0.0);
    }

    public void spawn(int amount, Vec3 origin, VfxExpressionContext scope, RandomSource random) {
        if (amount <= 0) return;
        int newCount = Math.addExact(count, amount);
        int needed = Math.multiplyExact(newCount, STRIDE);
        if (states == null || needed > states.capacity()) {
            int capacity = Math.max(needed, states == null ? 4096 :
                    Math.min(Integer.MAX_VALUE - 16, states.capacity() + states.capacity() / 2));
            ByteBuffer next = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
            if (states != null) {
                states.position(0);
                states.limit(count * STRIDE);
                next.put(states);
            }
            states = next;
        }
        states.clear();
        states.position(count * STRIDE);
        for (int i = 0; i < amount; i++) {
            random.nextDouble();
            Vec3 pos = VfxSpawnPositionSampler.sample(origin, definition.spawnShape(), scope, random);
            Vec3 vel = VfxMotionSampler.sampleInitialVelocity(definition.motion(), origin, pos, scope, random);
            states.putFloat((float) pos.x).putFloat((float) pos.y).putFloat((float) pos.z);
            states.putFloat((float) vel.x).putFloat((float) vel.y).putFloat((float) vel.z);
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
    /** Direct buffer capacity (not just currently live bytes). */
    public long allocatedStateBytes() { return states == null ? 0 : states.capacity(); }
    /** Uploaded birth-state payload only; excludes driver bookkeeping and mesh VBOs. */
    public long residentBufferBytes() { return gpuBuffer == 0 ? 0 : (long) count * STRIDE; }
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
        view.limit(count * STRIDE);
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
            int offset = index * STRIDE;
            return new Vec3(
                    states.getFloat(offset) + states.getFloat(offset+12)*cv + plan.accelerationX()*ca,
                    states.getFloat(offset+4) + states.getFloat(offset+16)*cv + plan.accelerationY()*ca,
                    states.getFloat(offset+8) + states.getFloat(offset+20)*cv + plan.accelerationZ()*ca);
        }
        @Override public Vec3 interpolatedRotation(float partial) { return Vec3.ZERO; }
        @Override public Vec3 interpolatedScale(float partial) { return scale; }
        @Override public VfxColor interpolatedColor(float partial) { return color; }
        @Override public Vec3 velocity() {
            int offset = index*STRIDE;
            return new Vec3(states.getFloat(offset+12),states.getFloat(offset+16),states.getFloat(offset+20));
        }
        @Override public ClientVfxSpriteUvState spriteUv() { return uvState; }
        @Override public int age() { return age; }
        @Override public double ageNormalized() { return Math.min(1, age/(double)plan.lifetime()); }
    }
}
