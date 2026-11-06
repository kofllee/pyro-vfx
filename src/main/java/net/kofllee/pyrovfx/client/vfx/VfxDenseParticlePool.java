package net.kofllee.pyrovfx.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvResolver;
import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvState;
import net.kofllee.pyrovfx.client.render.SpriteVfxParticleRenderer;
import net.kofllee.pyrovfx.client.render.ModelVfxParticleRenderer;
import net.kofllee.pyrovfx.client.render.VfxSpriteUvSampler;
import net.kofllee.pyrovfx.client.render.VfxSpriteUvRect;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.expression.VfxVariableLayout;
import net.kofllee.pyrovfx.vfx.type.VfxMotionMode;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer;
import net.kofllee.pyrovfx.vfx.type.VfxModelSourceType;
import net.kofllee.pyrovfx.vfx.type.VfxRotationMode;
import net.kofllee.pyrovfx.vfx.type.VfxTriggerType;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

/**
 * Ordered, capacity-reused SoA for sprites and baked models, including
 * synchronous on_collision callbacks. Movement, collision and rotation
 * channels are allocated only when those components are enabled.
 *
 * One reusable expression scope per emitter tick; no per-particle Java object
 * or temporary Vec3 is created by physics or compaction.
 * All updates remain on Minecraft's client tick thread.
 */
public final class VfxDenseParticlePool {
    private static final double TICK_SECONDS = VfxTime.SECONDS_PER_TICK;
    private static final double TICK_SECONDS_SQUARED = TICK_SECONDS * TICK_SECONDS;
    private final VfxEmitterDefinition definition;
    private net.kofllee.pyrovfx.vfx.transform.VfxTransform localSource;
    private boolean sourceMotionApplied;
    private final boolean dynamic;
    private final boolean moving;
    private final boolean rotating;
    private final boolean dynamicRotation;
    private final boolean colliding;
    private final BlockPos.MutableBlockPos blockCursor = new BlockPos.MutableBlockPos();
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
    private double[] rx, ry, rz, prevRx, prevRy, prevRz, wx, wy, wz;
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
    private long collisionContactsLastTick;

    public VfxDenseParticlePool(VfxEmitterDefinition definition) {
        this.definition = definition;
        this.dynamic = definition.motion().mode() == VfxMotionMode.DYNAMIC;
        this.moving = definition.motion().mode() != VfxMotionMode.STATIC
                || definition.space() == net.kofllee.pyrovfx.vfx.type.VfxSpace.LOCAL;
        this.rotating = definition.rotation().mode() != VfxRotationMode.NONE
                || definition.space() == net.kofllee.pyrovfx.vfx.type.VfxSpace.LOCAL
                && definition.render().type() == VfxRenderType.MODEL;
        this.dynamicRotation = definition.rotation().mode() == VfxRotationMode.DYNAMIC;
        this.colliding = definition.motion().mode() == VfxMotionMode.DYNAMIC
                && definition.motion().collision().collide();
    }

    public static boolean supports(VfxEmitterDefinition definition) {
        boolean sprite = definition.render().type() == VfxRenderType.SPRITE
                && definition.render().sprite() != null;
        boolean model = definition.render().type() == VfxRenderType.MODEL
                && definition.render().model() != null
                && definition.render().model().source() != VfxModelSourceType.ENTITY;
        return !Boolean.getBoolean("pyrovfx.cpu.forceLegacy")
                && (sprite || model)
                && (definition.particleTriggers().isEmpty()
                    || definition.motion().mode() == VfxMotionMode.DYNAMIC
                    && definition.motion().collision().collide()
                    && definition.particleTriggers().stream()
                        .allMatch(trigger -> trigger.type() == VfxTriggerType.ON_COLLISION))
                && (!definition.motion().collision().collide()
                    || definition.motion().mode() == VfxMotionMode.DYNAMIC);
    }

    public boolean dynamic() { return dynamic; }
    public boolean model() { return definition.render().type() == VfxRenderType.MODEL; }
    public VfxEmitterDefinition definition() { return definition; }
    public int size() { return size; }
    public int capacity() { return age.length; }
    public long collisionContactsLastTick() { return collisionContactsLastTick; }

    /** Lower-bound SoA payload bytes (array storage only, excluding JVM headers). */
    public long allocatedStateBytes() {
        long doubles = 0;
        double[][] arrays = {px,py,pz,spawnX,spawnY,spawnZ,scaleX,scaleY,scaleZ,
                prevScaleX,prevScaleY,prevScaleZ,r,g,b,a,prevR,prevG,prevB,prevA,
                particleRandom,prevX,prevY,prevZ,vx,vy,vz,
                rx,ry,rz,prevRx,prevRy,prevRz,wx,wy,wz};
        for (double[] channel : arrays) if (channel != null) doubles += channel.length;
        return doubles * Double.BYTES
                + (long) age.length * Integer.BYTES
                + (long) lifetime.length * Integer.BYTES
                + (long) uv.length * Long.BYTES;
    }

    @FunctionalInterface
    public interface InstanceConsumer {
        void append(double x, double y, double z, float sizeX, float sizeY,
                    float r, float g, float b, float a,
                    float u0, float v0, float u1, float v1,
                    float rotationX, float rotationY, float rotationZ,
                    float blockLight, float skyLight,
                    float velocityX, float velocityY, float velocityZ);
    }

    @FunctionalInterface
    public interface ModelInstanceConsumer {
        void append(double x, double y, double z, float sx, float sy, float sz,
                    float r, float g, float b, float a,
                    float rotationX, float rotationY, float rotationZ);
    }

    /** Dense model stream: primitive arrays directly to packed instancing, no snapshots. */
    public void writeModelInstances(ModelInstanceConsumer writer, float partialTick) {
        for (int i = 0; i < size; i++) {
            writeModelInstance(i, writer, partialTick);
        }
    }

    /** Single sorted translucent model, without walking the full pool. */
    public void writeModelInstance(int i, ModelInstanceConsumer writer, float partialTick) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException(i);
            double x = moving ? prevX[i] + (px[i] - prevX[i]) * partialTick : px[i];
            double y = moving ? prevY[i] + (py[i] - prevY[i]) * partialTick : py[i];
            double z = moving ? prevZ[i] + (pz[i] - prevZ[i]) * partialTick : pz[i];
            writer.append(x, y, z,
                    (float) (prevScaleX[i] + (scaleX[i] - prevScaleX[i]) * partialTick),
                    (float) (prevScaleY[i] + (scaleY[i] - prevScaleY[i]) * partialTick),
                    (float) (prevScaleZ[i] + (scaleZ[i] - prevScaleZ[i]) * partialTick),
                    (float) (prevR[i] + (r[i] - prevR[i]) * partialTick),
                    (float) (prevG[i] + (g[i] - prevG[i]) * partialTick),
                    (float) (prevB[i] + (b[i] - prevB[i]) * partialTick),
                    (float) (prevA[i] + (a[i] - prevA[i]) * partialTick),
                    rotating ? (float) (prevRx[i] + (rx[i] - prevRx[i]) * partialTick) : 0,
                    rotating ? (float) (prevRy[i] + (ry[i] - prevRy[i]) * partialTick) : 0,
                    rotating ? (float) (prevRz[i] + (rz[i] - prevRz[i]) * partialTick) : 0);
    }

    /** Cheap scale sample for conservative foreground occlusion routing. */
    public Vec3 interpolatedScale(int i, float partialTick) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException(i);
        return new Vec3(prevScaleX[i] + (scaleX[i] - prevScaleX[i]) * partialTick,
                prevScaleY[i] + (scaleY[i] - prevScaleY[i]) * partialTick,
                prevScaleZ[i] + (scaleZ[i] - prevScaleZ[i]) * partialTick);
    }

    public boolean fullyOpaque(float partialTick) {
        for (int i = 0; i < size; i++) {
            double alpha = prevA[i] + (a[i] - prevA[i]) * partialTick;
            if (Math.abs(alpha - 1.0) > 1.e-6) return false;
        }
        return true;
    }

    /** No particle objects or snapshots; converts dense simulation data to packed GPU instances. */
    public void writeInstances(InstanceConsumer writer, Camera camera, float partialTick) {
        for (int i = 0; i < size; i++) {
            double x = moving ? prevX[i] + (px[i] - prevX[i]) * partialTick : px[i];
            double y = moving ? prevY[i] + (py[i] - prevY[i]) * partialTick : py[i];
            double z = moving ? prevZ[i] + (pz[i] - prevZ[i]) * partialTick : pz[i];
            float sx = (float) (prevScaleX[i] + (scaleX[i] - prevScaleX[i]) * partialTick);
            float sy = (float) (prevScaleY[i] + (scaleY[i] - prevScaleY[i]) * partialTick);
            float cr = (float) (prevR[i] + (r[i] - prevR[i]) * partialTick);
            float cg = (float) (prevG[i] + (g[i] - prevG[i]) * partialTick);
            float cb = (float) (prevB[i] + (b[i] - prevB[i]) * partialTick);
            float ca = (float) (prevA[i] + (a[i] - prevA[i]) * partialTick);
            cursor.index = i;
            VfxSpriteUvRect rect = VfxSpriteUvSampler.sample(cursor);
            // World light is sampled once per instance, not once per quad
            // vertex; the per-frame block-position light cache is shared
            // with the Minecraft RenderType path and model instancing.
            int light = definition.render().environmentLighting()
                    ? net.kofllee.pyrovfx.client.render.VfxRenderLight.getParticleLight(x, y, z)
                    : net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            writer.append(x, y, z, sx, sy, cr, cg, cb, ca,
                    rect.u0(), rect.v0(), rect.u1(), rect.v1(),
                    rotating ? (float) (prevRx[i] + (rx[i] - prevRx[i]) * partialTick) : 0,
                    rotating ? (float) (prevRy[i] + (ry[i] - prevRy[i]) * partialTick) : 0,
                    rotating ? (float) (prevRz[i] + (rz[i] - prevRz[i]) * partialTick) : 0,
                    (((light >> 4) & 15) + 0.5f) / 16f,
                    (((light >> 20) & 15) + 0.5f) / 16f,
                    dynamic ? (float) vx[i] : 0,
                    dynamic ? (float) vy[i] : 0,
                    dynamic ? (float) vz[i] : 0);
        }
    }

    public void add(Vec3 spawn, Vec3 velocity, int life, double random,
                    Vec3 initialScale, VfxColor initialColor, VfxExpressionContext spawnContext) {
        add(spawn, velocity, Vec3.ZERO, Vec3.ZERO, life, random,
                initialScale, initialColor, spawnContext);
    }

    public void add(Vec3 spawn, Vec3 velocity, Vec3 rotation, Vec3 angularVelocity,
                    int life, double random, Vec3 initialScale, VfxColor initialColor,
                    VfxExpressionContext spawnContext) {
        ensureCapacity(size + 1);
        int i = size++;
        px[i] = spawn.x; py[i] = spawn.y; pz[i] = spawn.z;
        spawnX[i] = spawn.x; spawnY[i] = spawn.y; spawnZ[i] = spawn.z;
        if (moving) {
            prevX[i] = spawn.x; prevY[i] = spawn.y; prevZ[i] = spawn.z;
        }
        if (dynamic) {
            vx[i] = velocity.x; vy[i] = velocity.y; vz[i] = velocity.z;
        }
        if (rotating) {
            rx[i] = prevRx[i] = rotation.x;
            ry[i] = prevRy[i] = rotation.y;
            rz[i] = prevRz[i] = rotation.z;
            if (dynamicRotation) {
                wx[i] = angularVelocity.x; wy[i] = angularVelocity.y; wz[i] = angularVelocity.z;
            }
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
        uv[i] = model() ? null : ClientVfxSpriteUvResolver.resolve(
                definition.render().sprite().uv(), spawnContext, random);
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
        if (moving) {
            prevX = Arrays.copyOf(prevX == null ? new double[0] : prevX, cap);
            prevY = Arrays.copyOf(prevY == null ? new double[0] : prevY, cap);
            prevZ = Arrays.copyOf(prevZ == null ? new double[0] : prevZ, cap);
        }
        if (dynamic) {
            vx = Arrays.copyOf(vx == null ? new double[0] : vx, cap);
            vy = Arrays.copyOf(vy == null ? new double[0] : vy, cap);
            vz = Arrays.copyOf(vz == null ? new double[0] : vz, cap);
        }
        if (rotating) {
            rx = Arrays.copyOf(rx == null ? new double[0] : rx, cap);
            ry = Arrays.copyOf(ry == null ? new double[0] : ry, cap);
            rz = Arrays.copyOf(rz == null ? new double[0] : rz, cap);
            prevRx = Arrays.copyOf(prevRx == null ? new double[0] : prevRx, cap);
            prevRy = Arrays.copyOf(prevRy == null ? new double[0] : prevRy, cap);
            prevRz = Arrays.copyOf(prevRz == null ? new double[0] : prevRz, cap);
        }
        if (dynamicRotation) {
            wx = Arrays.copyOf(wx == null ? new double[0] : wx, cap);
            wy = Arrays.copyOf(wy == null ? new double[0] : wy, cap);
            wz = Arrays.copyOf(wz == null ? new double[0] : wz, cap);
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
        tick(null, emitterContext);
    }

    public void tick(ClientLevel level, VfxExpressionContext emitterContext) {
        tick(level, emitterContext, null);
    }

    /** Called synchronously after the colliding particle's state update.
     * Event execution may append births to this same pool; such births
     * are retained but must not advance until the next emitter tick.
     */
    @FunctionalInterface
    public interface CollisionEventSink {
        void onCollision(double x, double y, double z);
    }

    public void tick(ClientLevel level, VfxExpressionContext emitterContext,
                     CollisionEventSink collisionSink) {
        collisionContactsLastTick = 0;
        if (size == 0) return;
        VfxExpressionContext scope = emitterContext.child();
        int out = 0;
        final int end = size;
        for (int i = 0; i < end; i++) {
            boolean expiredOnCollision = false;
            boolean collisionEvent = false;
            double collisionX = 0, collisionY = 0, collisionZ = 0;
            prevScaleX[i] = scaleX[i];
            prevScaleY[i] = scaleY[i];
            prevScaleZ[i] = scaleZ[i];
            prevR[i] = r[i]; prevG[i] = g[i]; prevB[i] = b[i]; prevA[i] = a[i];

            double oldX = px[i], oldY = py[i], oldZ = pz[i];
            if (moving && !sourceMotionApplied) {
                prevX[i] = oldX; prevY[i] = oldY; prevZ[i] = oldZ;
            }
            if (rotating && !sourceMotionApplied) {
                prevRx[i] = rx[i]; prevRy[i] = ry[i]; prevRz[i] = rz[i];
            }
            setContext(scope, i, oldX, oldY, oldZ);
            if (dynamic) {
                VfxVec3 acceleration = definition.motion().dynamic().acceleration().evaluate(scope);
                Vec3 accelerationVector = localSource == null ? acceleration.toVec3()
                        : localSource.rotate(acceleration.toVec3());
                double drag = definition.motion().dynamic().linearDrag().evaluateDouble(scope);
                double multiplier = Math.exp(-drag * TICK_SECONDS);
                vx[i] = (vx[i] + accelerationVector.x * TICK_SECONDS_SQUARED) * multiplier;
                vy[i] = (vy[i] + accelerationVector.y * TICK_SECONDS_SQUARED) * multiplier;
                vz[i] = (vz[i] + accelerationVector.z * TICK_SECONDS_SQUARED) * multiplier;
                if (colliding) {
                    var collision = VfxDenseCollisionSystem.resolve(level,
                            definition.motion().collision(), scope, blockCursor,
                            oldX, oldY, oldZ, vx[i], vy[i], vz[i]);
                    if (collision == null) {
                        px[i] += vx[i]; py[i] += vy[i]; pz[i] += vz[i];
                    } else {
                        px[i] = collision.x(); py[i] = collision.y(); pz[i] = collision.z();
                        vx[i] = collision.vx(); vy[i] = collision.vy(); vz[i] = collision.vz();
                        expiredOnCollision = collision.dead();
                        collisionContactsLastTick++;
                        collisionEvent = true;
                        collisionX = collision.x();
                        collisionY = collision.y();
                        collisionZ = collision.z();
                    }
                } else {
                    px[i] += vx[i]; py[i] += vy[i]; pz[i] += vz[i];
                }
            } else if (definition.motion().mode() == VfxMotionMode.PARAMETRIC) {
                VfxVec3 offset = definition.motion().parametric().offset().evaluate(scope);
                Vec3 displacement = localSource == null ? offset.toVec3() : localSource.rotate(offset.toVec3());
                px[i] = spawnX[i] + displacement.x;
                py[i] = spawnY[i] + displacement.y;
                pz[i] = spawnZ[i] + displacement.z;
            }
            if (rotating) {
                if (dynamicRotation) {
                    VfxVec3 accel = definition.rotation().dynamic().angularAcceleration().evaluate(scope);
                    double drag = Math.exp(-definition.rotation().dynamic().angularDrag()
                            .evaluateDouble(scope) * TICK_SECONDS);
                    wx[i] = (wx[i] + accel.x() * TICK_SECONDS_SQUARED) * drag;
                    wy[i] = (wy[i] + accel.y() * TICK_SECONDS_SQUARED) * drag;
                    wz[i] = (wz[i] + accel.z() * TICK_SECONDS_SQUARED) * drag;
                    rx[i] += wx[i]; ry[i] += wy[i]; rz[i] += wz[i];
                } else if (definition.rotation().mode() == VfxRotationMode.PARAMETRIC) {
                    VfxVec3 angles = definition.rotation().parametric().rotation().evaluate(scope);
                    Vec3 result = localSource == null ? angles.toVec3()
                            : localSource.rotateAngles(angles.toVec3());
                    rx[i] = result.x; ry[i] = result.y; rz[i] = result.z;
                    if (age[i] == 0) {
                        prevRx[i] = rx[i]; prevRy[i] = ry[i]; prevRz[i] = rz[i];
                    }
                }
            }
            setContext(scope, i, oldX, oldY, oldZ);
            VfxVec3 scale = definition.render().appearance().scale().evaluate(scope);
            VfxColor color = definition.render().appearance().color().evaluate(scope);
            scaleX[i] = scale.x(); scaleY[i] = scale.y(); scaleZ[i] = scale.z();
            r[i] = color.r(); g[i] = color.g(); b[i] = color.b(); a[i] = color.a();
            age[i]++;
            if (!expiredOnCollision && age[i] < lifetime[i]) {
                if (out != i) copy(i, out);
                out++;
            }
            // Legacy invokes on_collision after completing this particle's
            // appearance/age update and before visiting the next particle.
            // Draining here keeps parameter changes and RNG draws ordered.
            if (collisionEvent && collisionSink != null)
                collisionSink.onCollision(collisionX, collisionY, collisionZ);
        }
        // Reentrant event "emit" may append particles at [end, size).
        // Move them behind the surviving original particles, preserving the
        // append/RNG order without giving them an extra tick of simulation.
        int all = size;
        for (int i = end; i < all; i++) {
            if (out != i) copy(i, out);
            out++;
        }
        Arrays.fill(uv, out, size, null);
        size = out;
        sourceMotionApplied = false;
    }

    /** Rotate and translate dense world state in place, without per-particle objects. */
    public void followSource(net.kofllee.pyrovfx.vfx.transform.VfxTransform previous,
                             net.kofllee.pyrovfx.vfx.transform.VfxTransform current) {
        if (definition.space() != net.kofllee.pyrovfx.vfx.type.VfxSpace.LOCAL) return;
        localSource = current;
        if (size == 0) return;
        if (previous.position().equals(current.position())
                && previous.rotation().equals(current.rotation())) return;
        sourceMotionApplied = true;
        var delta = current.rotation().mul(previous.rotation().conjugate());
        var basis = new org.joml.Matrix3f().set(delta);
        double a = basis.m00(), b = basis.m10(), c = basis.m20();
        double d = basis.m01(), e = basis.m11(), f = basis.m21();
        double g = basis.m02(), h = basis.m12(), k = basis.m22();
        Vec3 previousOrigin = previous.position(), currentOrigin = current.position();
        var rotationScratch = rotating ? new org.joml.Quaternionf() : null;
        var eulerScratch = rotating ? new org.joml.Vector3f() : null;
        for (int i = 0; i < size; i++) {
            prevX[i] = px[i]; prevY[i] = py[i]; prevZ[i] = pz[i];
            double x = px[i] - previousOrigin.x, y = py[i] - previousOrigin.y,
                    z = pz[i] - previousOrigin.z;
            px[i] = currentOrigin.x + a*x + b*y + c*z;
            py[i] = currentOrigin.y + d*x + e*y + f*z;
            pz[i] = currentOrigin.z + g*x + h*y + k*z;
            x = spawnX[i] - previousOrigin.x; y = spawnY[i] - previousOrigin.y;
            z = spawnZ[i] - previousOrigin.z;
            spawnX[i] = currentOrigin.x + a*x + b*y + c*z;
            spawnY[i] = currentOrigin.y + d*x + e*y + f*z;
            spawnZ[i] = currentOrigin.z + g*x + h*y + k*z;
            if (dynamic) {
                x = vx[i]; y = vy[i]; z = vz[i];
                vx[i] = a*x + b*y + c*z;
                vy[i] = d*x + e*y + f*z;
                vz[i] = g*x + h*y + k*z;
            }
            if (rotating) {
                rotationScratch.rotationXYZ(
                        (float) Math.toRadians(rx[i]), (float) Math.toRadians(ry[i]),
                        (float) Math.toRadians(rz[i])).premul(delta);
                prevRx[i] = rx[i]; prevRy[i] = ry[i]; prevRz[i] = rz[i];
                rotationScratch.getEulerAnglesXYZ(eulerScratch);
                rx[i] = Math.toDegrees(eulerScratch.x);
                ry[i] = Math.toDegrees(eulerScratch.y);
                rz[i] = Math.toDegrees(eulerScratch.z);
            }
        }
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
        scope.setVec3Slots(VfxVariableLayout.PARTICLE_ROTATION, "particle.rotation",
                rotating ? rx[i] : 0, rotating ? ry[i] : 0, rotating ? rz[i] : 0);
        scope.setVec3Slots(VfxVariableLayout.PARTICLE_ANGULAR_VELOCITY, "particle.angular_velocity",
                dynamicRotation ? wx[i] : 0, dynamicRotation ? wy[i] : 0, dynamicRotation ? wz[i] : 0);
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
        if (moving) {
            prevX[to] = prevX[from];
            prevY[to] = prevY[from];
            prevZ[to] = prevZ[from];
        }
        if (dynamic) {
            vx[to] = vx[from];
            vy[to] = vy[from];
            vz[to] = vz[from];
        }
        if (rotating) {
            rx[to] = rx[from]; ry[to] = ry[from]; rz[to] = rz[from];
            prevRx[to] = prevRx[from]; prevRy[to] = prevRy[from]; prevRz[to] = prevRz[from];
        }
        if (dynamicRotation) {
            wx[to] = wx[from]; wy[to] = wy[from]; wz[to] = wz[from];
        }
        age[to] = age[from];
        lifetime[to] = lifetime[from];
        uv[to] = uv[from];
    }

    public void render(PoseStack stack, MultiBufferSource buffer, Camera camera, float partialTick) {
        for (int i = 0; i < size; i++) {
            renderIndex(i, stack, buffer, camera, partialTick);
        }
    }

    /** Render one Dense entry after transparent depth-sorting, without
     * creating a snapshot or exposing the mutable internal cursor.
     */
    public void renderIndex(int i, PoseStack stack, MultiBufferSource buffer,
                            Camera camera, float partialTick) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException(i);
        cursor.index = i;
        if (model()) ModelVfxParticleRenderer.render(cursor, stack, buffer, camera, partialTick);
        else SpriteVfxParticleRenderer.render(cursor, stack, buffer, camera, partialTick);
    }

    public Vec3 interpolatedPosition(int i, float partialTick) {
        if (i < 0 || i >= size) throw new IndexOutOfBoundsException(i);
        return new Vec3(moving ? prevX[i] + (px[i] - prevX[i]) * partialTick : px[i],
                moving ? prevY[i] + (py[i] - prevY[i]) * partialTick : py[i],
                moving ? prevZ[i] + (pz[i] - prevZ[i]) * partialTick : pz[i]);
    }

    /** Compatibility/debug-only accessor. Materializes a legacy read-only snapshot on demand. */
    public ClientVfxParticle snapshot(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        return ClientVfxParticle.denseSnapshot(
                definition, new Vec3(spawnX[index], spawnY[index], spawnZ[index]),
                new Vec3(px[index], py[index], pz[index]),
                moving ? new Vec3(prevX[index], prevY[index], prevZ[index])
                        : new Vec3(px[index], py[index], pz[index]),
                dynamic ? new Vec3(vx[index], vy[index], vz[index]) : Vec3.ZERO,
                rotating ? new Vec3(rx[index], ry[index], rz[index]) : Vec3.ZERO,
                rotating ? new Vec3(prevRx[index], prevRy[index], prevRz[index]) : Vec3.ZERO,
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
            if (!moving) return new Vec3(px[i], py[i], pz[i]);
            return new Vec3(prevX[i] + (px[i] - prevX[i]) * t,
                    prevY[i] + (py[i] - prevY[i]) * t, prevZ[i] + (pz[i] - prevZ[i]) * t);
        }
        @Override public Vec3 interpolatedRotation(float t) {
            int i = index;
            return rotating ? new Vec3(prevRx[i] + (rx[i] - prevRx[i]) * t,
                    prevRy[i] + (ry[i] - prevRy[i]) * t,
                    prevRz[i] + (rz[i] - prevRz[i]) * t) : Vec3.ZERO;
        }
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
