package net.kofllee.pyrovfx.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvResolver;
import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvState;
import net.kofllee.pyrovfx.client.render.SpriteVfxParticleRenderer;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.expression.VfxVariableLayout;
import net.kofllee.pyrovfx.vfx.type.VfxMotionMode;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.kofllee.pyrovfx.vfx.type.VfxRotationMode;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

/**
 * Ordered, capacity-reused SoA for sprites with no world collision, particle
 * callbacks, dynamic rotation or environment-light probes. Dynamic-only
 * channels are never allocated for static sprites.
 *
 * One reusable expression scope per emitter tick; no per-particle Java object
 * or temporary Vec3 is created by physics or compaction.
 * All updates remain on Minecraft's client tick thread.
 */
public final class VfxDenseSpritePool {
    private static final double TICK_SECONDS = VfxTime.SECONDS_PER_TICK;
    private static final double TICK_SECONDS_SQUARED = TICK_SECONDS * TICK_SECONDS;
    private final VfxEmitterDefinition definition;
    private final boolean dynamic;
    private final Cursor cursor = new Cursor();

    private double[] px = new double[0];
    private double[] py = new double[0];
    private double[] pz = new double[0];
    private double[] spawnX = new double[0];
    private double[] spawnY = new double[0];
    private double[] spawnZ = new double[0];
    private double[] scaleX = new double[0];
    private double[] scaleY = new double[0];
    private double[] scaleZ = new double[0];
    private double[] prevScaleX = new double[0];
    private double[] prevScaleY = new double[0];
    private double[] prevScaleZ = new double[0];
    private double[] r = new double[0];
    private double[] g = new double[0];
    private double[] b = new double[0];
    private double[] a = new double[0];
    private double[] prevR = new double[0];
    private double[] prevG = new double[0];
    private double[] prevB = new double[0];
    private double[] prevA = new double[0];
    private double[] particleRandom = new double[0];
    private double[] prevX;
    private double[] prevY;
    private double[] prevZ;
    private double[] vx;
    private double[] vy;
    private double[] vz;
    private int[] age = new int[0];
    private int[] lifetime = new int[0];
    private ClientVfxSpriteUvState[] uv = new ClientVfxSpriteUvState[0];
    private int size;

    public VfxDenseSpritePool(VfxEmitterDefinition definition) {
        this.definition = definition;
        this.dynamic = definition.motion().mode() == VfxMotionMode.DYNAMIC;
    }

    public static boolean supports(VfxEmitterDefinition definition) {
        return !Boolean.getBoolean("pyrovfx.cpu.forceLegacy")
                && definition.render().type() == VfxRenderType.SPRITE
                && definition.render().sprite() != null
                && !definition.render().environmentLighting()
                && definition.particleTriggers().isEmpty()
                && definition.rotation().mode() == VfxRotationMode.NONE
                && (definition.motion().mode() == VfxMotionMode.STATIC
                    || (definition.motion().mode() == VfxMotionMode.DYNAMIC
                        && !definition.motion().collision().collide()));
    }

    public boolean dynamic() { return dynamic; }
    public int size() { return size; }
    public int capacity() { return age.length; }

    public void add(Vec3 spawn, Vec3 velocity, int life, double random,
                    Vec3 initialScale, VfxColor initialColor, VfxExpressionContext spawnContext) {
        ensureCapacity(size + 1);
        int i = size++;
        px[i] = spawn.x; py[i] = spawn.y; pz[i] = spawn.z;
        spawnX[i] = spawn.x; spawnY[i] = spawn.y; spawnZ[i] = spawn.z;
        if (dynamic) {
            prevX[i] = spawn.x; prevY[i] = spawn.y; prevZ[i] = spawn.z;
            vx[i] = velocity.x; vy[i] = velocity.y; vz[i] = velocity.z;
        }
        scaleX[i] = prevScaleX[i] = initialScale.x;
        scaleY[i] = prevScaleY[i] = initialScale.y;
        scaleZ[i] = prevScaleZ[i] = initialScale.z;
        r[i] = prevR[i] = initialColor.r();
        g[i] = prevG[i] = initialColor.g();
        b[i] = prevB[i] = initialColor.b();
        a[i] = prevA[i] = initialColor.a();
        particleRandom[i] = random;
        age[i] = 0;
        lifetime[i] = Math.max(1, life);
        uv[i] = ClientVfxSpriteUvResolver.resolve(definition.render().sprite().uv(), spawnContext, random);
    }

    private void ensureCapacity(int required) {
        if (required <= age.length) return;
        int cap = Math.max(16, Math.max(required, age.length + (age.length >>> 1)));
        px = Arrays.copyOf(px, cap);
        py = Arrays.copyOf(py, cap);
        pz = Arrays.copyOf(pz, cap);
        spawnX = Arrays.copyOf(spawnX, cap);
        spawnY = Arrays.copyOf(spawnY, cap);
        spawnZ = Arrays.copyOf(spawnZ, cap);
        scaleX = Arrays.copyOf(scaleX, cap);
        scaleY = Arrays.copyOf(scaleY, cap);
        scaleZ = Arrays.copyOf(scaleZ, cap);
        prevScaleX = Arrays.copyOf(prevScaleX, cap);
        prevScaleY = Arrays.copyOf(prevScaleY, cap);
        prevScaleZ = Arrays.copyOf(prevScaleZ, cap);
        r = Arrays.copyOf(r, cap);
        g = Arrays.copyOf(g, cap);
        b = Arrays.copyOf(b, cap);
        a = Arrays.copyOf(a, cap);
        prevR = Arrays.copyOf(prevR, cap);
        prevG = Arrays.copyOf(prevG, cap);
        prevB = Arrays.copyOf(prevB, cap);
        prevA = Arrays.copyOf(prevA, cap);
        particleRandom = Arrays.copyOf(particleRandom, cap);
        if (dynamic) {
            prevX = Arrays.copyOf(prevX == null ? new double[0] : prevX, cap);
            prevY = Arrays.copyOf(prevY == null ? new double[0] : prevY, cap);
            prevZ = Arrays.copyOf(prevZ == null ? new double[0] : prevZ, cap);
            vx = Arrays.copyOf(vx == null ? new double[0] : vx, cap);
            vy = Arrays.copyOf(vy == null ? new double[0] : vy, cap);
            vz = Arrays.copyOf(vz == null ? new double[0] : vz, cap);
        }
        age = Arrays.copyOf(age, cap);
        lifetime = Arrays.copyOf(lifetime, cap);
        uv = Arrays.copyOf(uv, cap);
    }

    /**
     * Ordered compact-after-update. Nothing mutates or spawns during this
     * traversal: particle collision and callbacks are routed to legacy CPU.
     * RNG functions are evaluated in the same particle insertion order.
     */
    public void tick(VfxExpressionContext emitterContext) {
        if (size == 0) return;
        VfxExpressionContext scope = emitterContext.child();
        int out = 0;
        for (int i = 0, end = size; i < end; i++) {
            prevScaleX[i] = scaleX[i];
            prevScaleY[i] = scaleY[i];
            prevScaleZ[i] = scaleZ[i];
            prevR[i] = r[i]; prevG[i] = g[i]; prevB[i] = b[i]; prevA[i] = a[i];

            double oldX = px[i], oldY = py[i], oldZ = pz[i];
            if (dynamic) {
                prevX[i] = oldX; prevY[i] = oldY; prevZ[i] = oldZ;
            }
            setContext(scope, i, oldX, oldY, oldZ);
            if (dynamic) {
                VfxVec3 acceleration = definition.motion().dynamic().acceleration().evaluate(scope);
                double drag = definition.motion().dynamic().linearDrag().evaluateDouble(scope);
                double multiplier = Math.exp(-drag * TICK_SECONDS);
                vx[i] = (vx[i] + acceleration.x() * TICK_SECONDS_SQUARED) * multiplier;
                vy[i] = (vy[i] + acceleration.y() * TICK_SECONDS_SQUARED) * multiplier;
                vz[i] = (vz[i] + acceleration.z() * TICK_SECONDS_SQUARED) * multiplier;
                px[i] += vx[i]; py[i] += vy[i]; pz[i] += vz[i];
            }
            setContext(scope, i, oldX, oldY, oldZ);
            VfxVec3 scale = definition.render().appearance().scale().evaluate(scope);
            VfxColor color = definition.render().appearance().color().evaluate(scope);
            scaleX[i] = scale.x(); scaleY[i] = scale.y(); scaleZ[i] = scale.z();
            r[i] = color.r(); g[i] = color.g(); b[i] = color.b(); a[i] = color.a();
            age[i]++;
            if (age[i] < lifetime[i]) {
                if (out != i) copy(i, out);
                out++;
            }
        }
        Arrays.fill(uv, out, size, null);
        size = out;
    }

    private void setContext(VfxExpressionContext scope, int i, double oldX, double oldY, double oldZ) {
        scope.setSlot(VfxVariableLayout.PARTICLE_AGE, "particle.age", age[i]);
        scope.setSlot(VfxVariableLayout.PARTICLE_LIFETIME, "particle.lifetime", lifetime[i]);
        scope.setSlot(VfxVariableLayout.PARTICLE_NORMALIZED_AGE, "particle.normalized_age",
                Math.min(1.0, age[i] / (double) lifetime[i]));
        scope.setSlot(VfxVariableLayout.PARTICLE_RANDOM, "particle.random", particleRandom[i]);
        scope.setVec3Slots(VfxVariableLayout.SPAWN_POS, "spawn.pos", spawnX[i], spawnY[i], spawnZ[i]);
        scope.setVec3Slots(VfxVariableLayout.PARTICLE_POS, "particle.pos", px[i], py[i], pz[i]);
        scope.setVec3Slots(VfxVariableLayout.PARTICLE_PREV_POS, "particle.prev_pos", oldX, oldY, oldZ);
        scope.setVec3Slots(VfxVariableLayout.PARTICLE_VEL, "particle.vel",
                dynamic ? vx[i] : 0.0, dynamic ? vy[i] : 0.0, dynamic ? vz[i] : 0.0);
        scope.setSlot(VfxVariableLayout.PARTICLE_SPEED, "particle.speed",
                dynamic ? Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]) : 0.0);
        scope.setVec3Slots(VfxVariableLayout.PARTICLE_SCALE, "particle.scale",
                prevScaleX[i], prevScaleY[i], prevScaleZ[i]);
        scope.setVec3Slots(VfxVariableLayout.PARTICLE_ROTATION, "particle.rotation", 0, 0, 0);
        scope.setVec3Slots(VfxVariableLayout.PARTICLE_ANGULAR_VELOCITY, "particle.angular_velocity", 0, 0, 0);
    }

    private void copy(int from, int to) {
        px[to] = px[from];
        py[to] = py[from];
        pz[to] = pz[from];
        spawnX[to] = spawnX[from];
        spawnY[to] = spawnY[from];
        spawnZ[to] = spawnZ[from];
        scaleX[to] = scaleX[from];
        scaleY[to] = scaleY[from];
        scaleZ[to] = scaleZ[from];
        prevScaleX[to] = prevScaleX[from];
        prevScaleY[to] = prevScaleY[from];
        prevScaleZ[to] = prevScaleZ[from];
        r[to] = r[from];
        g[to] = g[from];
        b[to] = b[from];
        a[to] = a[from];
        prevR[to] = prevR[from];
        prevG[to] = prevG[from];
        prevB[to] = prevB[from];
        prevA[to] = prevA[from];
        particleRandom[to] = particleRandom[from];
        if (dynamic) {
            prevX[to] = prevX[from];
            prevY[to] = prevY[from];
            prevZ[to] = prevZ[from];
            vx[to] = vx[from];
            vy[to] = vy[from];
            vz[to] = vz[from];
        }
        age[to] = age[from];
        lifetime[to] = lifetime[from];
        uv[to] = uv[from];
    }

    public void render(PoseStack stack, MultiBufferSource buffer, Camera camera, float partialTick) {
        for (int i = 0; i < size; i++) {
            cursor.index = i;
            SpriteVfxParticleRenderer.render(cursor, stack, buffer, camera, partialTick);
        }
    }

    /** Compatibility/debug-only accessor. Materializes a legacy read-only snapshot on demand. */
    public ClientVfxParticle snapshot(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return ClientVfxParticle.denseSnapshot(
                definition, new Vec3(spawnX[index], spawnY[index], spawnZ[index]),
                new Vec3(px[index], py[index], pz[index]),
                dynamic ? new Vec3(prevX[index], prevY[index], prevZ[index])
                        : new Vec3(px[index], py[index], pz[index]),
                dynamic ? new Vec3(vx[index], vy[index], vz[index]) : Vec3.ZERO,
                new Vec3(scaleX[index], scaleY[index], scaleZ[index]),
                new Vec3(prevScaleX[index], prevScaleY[index], prevScaleZ[index]),
                new VfxColor(r[index], g[index], b[index], a[index]),
                new VfxColor(prevR[index], prevG[index], prevB[index], prevA[index]),
                particleRandom[index], lifetime[index], age[index], uv[index]);
    }

    private final class Cursor implements VfxSpriteParticleView {
        private int index;

        @Override public VfxEmitterDefinition emitterDefinition() { return definition; }
        @Override public Vec3 interpolatedPosition(float t) {
            int i = index;
            if (!dynamic) return new Vec3(px[i], py[i], pz[i]);
            return new Vec3(prevX[i] + (px[i] - prevX[i]) * t,
                    prevY[i] + (py[i] - prevY[i]) * t, prevZ[i] + (pz[i] - prevZ[i]) * t);
        }
        @Override public Vec3 interpolatedRotation(float t) { return Vec3.ZERO; }
        @Override public Vec3 interpolatedScale(float t) {
            int i = index;
            return new Vec3(prevScaleX[i] + (scaleX[i] - prevScaleX[i]) * t,
                    prevScaleY[i] + (scaleY[i] - prevScaleY[i]) * t,
                    prevScaleZ[i] + (scaleZ[i] - prevScaleZ[i]) * t);
        }
        @Override public VfxColor interpolatedColor(float t) {
            int i = index;
            return new VfxColor(prevR[i] + (r[i] - prevR[i]) * t,
                    prevG[i] + (g[i] - prevG[i]) * t,
                    prevB[i] + (b[i] - prevB[i]) * t,
                    prevA[i] + (a[i] - prevA[i]) * t);
        }
        @Override public Vec3 velocity() {
            int i = index;
            return dynamic ? new Vec3(vx[i], vy[i], vz[i]) : Vec3.ZERO;
        }
        @Override public ClientVfxSpriteUvState spriteUv() { return uv[index]; }
        @Override public int age() { return age[index]; }
        @Override public double ageNormalized() {
            int i = index;
            return Math.min(1.0, age[i] / (double) lifetime[i]);
        }
    }
}
