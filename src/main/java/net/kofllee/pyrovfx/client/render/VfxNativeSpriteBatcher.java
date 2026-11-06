package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.VfxDenseParticlePool;
import net.kofllee.pyrovfx.client.vfx.VfxSpriteParticleView;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxBlendMode;
import net.kofllee.pyrovfx.vfx.type.VfxFacingMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shared additive sprite batching and globally depth-sorted alpha queue.
 * Native GPU Geometry Bridge owns the fast-path vertex expansion and Minecraft
 * ShaderInstance submission; vanilla RenderType is the CPU-only fallback.
 */
public final class VfxNativeSpriteBatcher {
    private static final int INSTANCE_FLOATS = 21;
    private static final int STRIDE = INSTANCE_FLOATS * Float.BYTES;
    private static final int MAX_BATCH = 16_384;
    private static int bufferCapacity;
    private static ByteBuffer staging;
    private static volatile boolean reloadPending;
    private static Object lastWorld;

    private static ResourceLocation batchTexture;
    private static VfxFacingMode batchFacing;
    private static boolean batchRotating;
    private static boolean batchAlpha;
    private static boolean batchWorldLit;
    private record AlphaMaterial(ResourceLocation texture, VfxFacingMode facing,
                                 boolean rotating, boolean worldLit) {}
    private static final List<AlphaMaterial> ALPHA_MATERIALS = new ArrayList<>();
    private static final java.util.Map<AlphaMaterial, Integer> ALPHA_MATERIAL_IDS =
            new java.util.HashMap<>();
    private static float[] alphaData = new float[0];
    private static long[] alphaOrder = new long[0];
    private static long[] alphaScratch = new long[0];
    private static final int[] ALPHA_RADIX_COUNTS = new int[1 << 16];
    private static int[] alphaMaterial = new int[0];
    private static int alphaCount;
    private record AlphaModel(long key, Runnable draw) {}
    private static final List<AlphaModel> ALPHA_MODELS = new ArrayList<>();
    private static boolean collectingAlpha;
    private static int collectingMaterial;
    private static int count;
    private static Matrix4f mvp;
    private static PoseStack framePose;
    private static Camera camera;
    private static float partialTick;
    private static int drawCalls;
    private static int submitted;
    private static int culled;
    private static int subpixelCulled;

    private VfxNativeSpriteBatcher() {}

    public static boolean isSupported(VfxEmitterDefinition definition) {
        return compatibleEnvironment() && supportsGeometry(definition);
    }

    /** Pure capability check; usable by server GameTests without an OpenGL context. */
    public static boolean supportsGeometry(VfxEmitterDefinition definition) {
        if (definition.render().sprite() == null
                || (definition.render().sprite().blendMode() != VfxBlendMode.ADDITIVE
                    && definition.render().sprite().blendMode() != VfxBlendMode.ALPHA)) {
            return false;
        }
        VfxFacingMode facing = definition.render().facing();
        return facing == VfxFacingMode.CAMERA || facing == VfxFacingMode.CAMERA_HORIZONTAL
                || facing == VfxFacingMode.WORLD_X || facing == VfxFacingMode.WORLD_Y
                || facing == VfxFacingMode.WORLD_Z || facing == VfxFacingMode.VELOCITY;
    }

    static boolean compatibleEnvironment() {
        return VfxRenderCompatibility.compatibleEnvironment();
    }

    public static boolean ready() {
        // Sorting and vanilla RenderType fallback are CPU-only; they remain
        // available without an OpenGL 3.3 native geometry bridge.
        maintain();
        return true;
    }

    /** Drop large alpha arrays after world unload/reload, not every frame. */
    public static void maintain() {
        var world = Minecraft.getInstance().level;
        if (reloadPending || lastWorld != world) {
            staging = null;
            bufferCapacity = 0;
            alphaData = new float[0];
            alphaOrder = alphaScratch = new long[0];
            alphaMaterial = new int[0];
            alphaCount = count = 0;
            ALPHA_MATERIALS.clear();
            ALPHA_MATERIAL_IDS.clear();
            ALPHA_MODELS.clear();
            reloadPending = false;
            lastWorld = world;
        }
    }

    /** Resource reload listeners can be invoked off-thread: actual GL cleanup is deferred. */
    public static void invalidateAfterReload() {
        reloadPending = true;
    }

    public static void begin(PoseStack poses, Camera view, float tick) {
        RenderSystem.assertOnRenderThread();
        camera = view;
        partialTick = tick;
        framePose = poses;
        // RenderType's entity shaders multiply Minecraft's camera model-view
        // matrix too. Omitting it projects +Z world geometry behind the camera.
        mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix())
                .mul(poses.last().pose());
        count = 0;
        batchTexture = null;
        batchFacing = null;
        batchRotating = false;
        batchAlpha = false;
        batchWorldLit = false;
        alphaCount = 0;
        ALPHA_MATERIALS.clear();
        ALPHA_MATERIAL_IDS.clear();
        ALPHA_MODELS.clear();
    }

    public static boolean matches(VfxEmitterDefinition definition) {
        return isSupported(definition) && ready();
    }

    public static int drawCalls() { return drawCalls; }
    public static int submittedParticles() { return submitted; }
    public static int culledParticles() { return culled; }
    public static int subpixelCulledParticles() { return subpixelCulled; }
    public static void resetFrameStats() { drawCalls = submitted = culled = subpixelCulled = 0; }

    public static void append(VfxDenseParticlePool pool) {
        VfxEmitterDefinition definition = pool.definition();
        ResourceLocation texture = definition.render().sprite().texture();
        VfxFacingMode facing = definition.render().facing();
        boolean rotating = definition.rotation().mode() != net.kofllee.pyrovfx.vfx.type.VfxRotationMode.NONE;
        boolean worldLit = definition.render().environmentLighting();
        if (definition.render().sprite().blendMode() == VfxBlendMode.ALPHA) {
            collectingMaterial = materialIndex(new AlphaMaterial(
                    texture, facing, rotating, worldLit));
            collectingAlpha = true;
            try {
                pool.writeInstances(VfxNativeSpriteBatcher::put, camera, partialTick);
            } finally {
                collectingAlpha = false;
            }
            return;
        }
        if (batchTexture != null && (!batchTexture.equals(texture) || batchFacing != facing
                || batchRotating != rotating || batchWorldLit != worldLit)) {
            flush();
        }
        batchTexture = texture;
        batchFacing = facing;
        batchRotating = rotating;
        batchAlpha = false;
        batchWorldLit = worldLit;
        pool.writeInstances(VfxNativeSpriteBatcher::put, camera, partialTick);
    }

    /**
     * Merge alpha sprites from the object-backed CPU simulation into the
     * SAME view-depth queue as Dense alpha sprites. Previously these were
     * flushed through Minecraft RenderType in emitter order, so overlapping
     * CPU and Dense smoke clouds could blend in the wrong order.
     */
    public static void appendAlpha(VfxSpriteParticleView particle) {
        var render = particle.emitterDefinition().render();
        if (render.sprite() == null || render.sprite().blendMode() != VfxBlendMode.ALPHA)
            throw new IllegalArgumentException("Only alpha sprites belong in the sorted queue");
        var color = particle.interpolatedColor(partialTick);
        collectingMaterial = materialIndex(new AlphaMaterial(render.sprite().texture(),
                render.facing(),
                particle.emitterDefinition().rotation().mode()
                        != net.kofllee.pyrovfx.vfx.type.VfxRotationMode.NONE,
                render.environmentLighting()));
        collectingAlpha = true;
        try {
            var position = particle.interpolatedPosition(partialTick);
            var size = particle.interpolatedScale(partialTick);
            var angles = particle.interpolatedRotation(partialTick);
            var uv = VfxSpriteUvSampler.sample(particle);
            int light = render.environmentLighting()
                    ? VfxRenderLight.getParticleLight(position.x, position.y, position.z)
                    : net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            put(position.x, position.y, position.z,
                    (float) size.x, (float) size.y,
                    (float) color.r(), (float) color.g(), (float) color.b(), (float) color.a(),
                    uv.u0(), uv.v0(), uv.u1(), uv.v1(),
                    (float) angles.x, (float) angles.y, (float) angles.z,
                    (((light >> 4) & 15) + 0.5f) / 16f,
                    (((light >> 20) & 15) + 0.5f) / 16f,
                    (float) particle.velocity().x,
                    (float) particle.velocity().y,
                    (float) particle.velocity().z);
        } finally {
            collectingAlpha = false;
        }
    }

    /**
     * Transparent models share the same far-to-near order as sprite alpha.
     * Their Minecraft RenderType submissions must be fully flushed by the
     * callback before another sprite/material is drawn. This is deliberately
     * a per-model slow path; opaque models still use GPU instancing.
     */
    public static void appendTransparentModel(net.minecraft.world.phys.Vec3 position,
                                               Runnable drawAndFlush) {
        var view = camera.getPosition();
        float depth = (float) ((position.x - view.x) * camera.getLookVector().x()
                + (position.y - view.y) * camera.getLookVector().y()
                + (position.z - view.z) * camera.getLookVector().z());
        ALPHA_MODELS.add(new AlphaModel(depthSortKey(depth, alphaCount), drawAndFlush));
    }

    private static int materialIndex(AlphaMaterial material) {
        return ALPHA_MATERIAL_IDS.computeIfAbsent(material, key -> {
            int index = ALPHA_MATERIALS.size();
            ALPHA_MATERIALS.add(key);
            return index;
        });
    }

    private static void put(double x, double y, double z, float sizeX, float sizeY,
                            float red, float green, float blue, float alpha,
                            float u0, float v0, float u1, float v1,
                            float rotationX, float rotationY, float rotationZ,
                            float blockLight, float skyLight,
                            float velocityX, float velocityY, float velocityZ) {
        if ((sizeX == 0 && sizeY == 0) || alpha <= 0) {
            culled++;
            return;
        }
        double dx = x - camera.getPosition().x;
        double dy = y - camera.getPosition().y;
        double dz = z - camera.getPosition().z;
        double radius = Math.max(Math.abs(sizeX), Math.abs(sizeY)) * 0.75;
        if (!Boolean.getBoolean("pyrovfx.render.debugNoCulling")
                && !VfxRenderVisibility.visible(mvp, dx, dy, dz, radius)) {
            culled++;
            return;
        }
        if (!VfxRenderVisibility.exceedsPixelFootprint(mvp, dx, dy, dz, radius,
                Minecraft.getInstance().getWindow().getHeight(),
                VfxRenderVisibility.minDiameterPixels())) {
            subpixelCulled++;
            return;
        }
        if (collectingAlpha) {
            ensureAlphaCapacity(alphaCount + 1);
            int offset = alphaCount * INSTANCE_FLOATS;
            float depth = (float) (dx * camera.getLookVector().x()
                    + dy * camera.getLookVector().y() + dz * camera.getLookVector().z());
            alphaOrder[alphaCount] = depthSortKey(depth, alphaCount);
            alphaMaterial[alphaCount] = collectingMaterial;
            alphaData[offset++] = (float) dx;
            alphaData[offset++] = (float) dy;
            alphaData[offset++] = (float) dz;
            alphaData[offset++] = sizeX;
            alphaData[offset++] = sizeY;
            alphaData[offset++] = red;
            alphaData[offset++] = green;
            alphaData[offset++] = blue;
            alphaData[offset++] = alpha;
            alphaData[offset++] = u0;
            alphaData[offset++] = v0;
            alphaData[offset++] = u1;
            alphaData[offset++] = v1;
            alphaData[offset++] = rotationX;
            alphaData[offset++] = rotationY;
            alphaData[offset++] = rotationZ;
            alphaData[offset++] = blockLight;
            alphaData[offset++] = skyLight;
            alphaData[offset++] = velocityX;
            alphaData[offset++] = velocityY;
            alphaData[offset] = velocityZ;
            alphaCount++;
            submitted++;
            return;
        }
        if (count >= MAX_BATCH) flush();
        ensureCapacity(count + 1);
        staging.putFloat((float) dx).putFloat((float) dy).putFloat((float) dz);
        staging.putFloat(sizeX).putFloat(sizeY);
        staging.putFloat(red).putFloat(green).putFloat(blue).putFloat(alpha);
        staging.putFloat(u0).putFloat(v0).putFloat(u1).putFloat(v1);
        staging.putFloat(rotationX).putFloat(rotationY).putFloat(rotationZ);
        staging.putFloat(blockLight).putFloat(skyLight);
        staging.putFloat(velocityX).putFloat(velocityY).putFloat(velocityZ);
        count++;
        submitted++;
    }

    /** Signed long sort key: farthest first, stable order for equal depth. */
    public static long depthSortKey(float depth, int index) {
        int bits = Float.floatToIntBits(Float.isNaN(depth) ? Float.NEGATIVE_INFINITY : depth);
        int ascending = bits < 0 ? ~bits : bits ^ Integer.MIN_VALUE;
        int farFirst = ~ascending ^ Integer.MIN_VALUE;
        return ((long) farFirst << 32) | (index & 0xFFFF_FFFFL);
    }

    private static void ensureAlphaCapacity(int required) {
        if (required <= alphaOrder.length) return;
        int capacity = Math.max(required, Math.max(256, alphaOrder.length * 2));
        alphaOrder = Arrays.copyOf(alphaOrder, capacity);
        alphaScratch = Arrays.copyOf(alphaScratch, capacity);
        alphaMaterial = Arrays.copyOf(alphaMaterial, capacity);
        alphaData = Arrays.copyOf(alphaData, capacity * INSTANCE_FLOATS);
    }

    /** Two stable 16-bit radix passes on signed view-depth keys; O(n) without boxing. */
    public static void sortAlphaOrder(long[] order, int length) {
        if (length < 2) return;
        if (alphaScratch.length < length) alphaScratch = new long[length];
        for (int shift = 0; shift < 32; shift += 16) {
            Arrays.fill(ALPHA_RADIX_COUNTS, 0);
            for (int i = 0; i < length; i++) {
                int key = (int) (order[i] >>> 32) ^ Integer.MIN_VALUE;
                ALPHA_RADIX_COUNTS[(key >>> shift) & 0xffff]++;
            }
            int next = 0;
            for (int i = 0; i < ALPHA_RADIX_COUNTS.length; i++) {
                int amount = ALPHA_RADIX_COUNTS[i];
                ALPHA_RADIX_COUNTS[i] = next;
                next += amount;
            }
            for (int i = 0; i < length; i++) {
                long value = order[i];
                int key = (int) (value >>> 32) ^ Integer.MIN_VALUE;
                alphaScratch[ALPHA_RADIX_COUNTS[(key >>> shift) & 0xffff]++] = value;
            }
            System.arraycopy(alphaScratch, 0, order, 0, length);
        }
    }

    private static void flushSortedAlpha() {
        if (alphaCount == 0 && ALPHA_MODELS.isEmpty()) return;
        flush();
        sortAlphaOrder(alphaOrder, alphaCount);
        ALPHA_MODELS.sort(java.util.Comparator.comparingLong(AlphaModel::key));
        int previousMaterial = -1;
        int modelIndex = 0;
        for (int index = 0; index <= alphaCount; index++) {
            long nextSpriteKey = index == alphaCount ? Long.MAX_VALUE : alphaOrder[index];
            while (modelIndex < ALPHA_MODELS.size()
                    && ALPHA_MODELS.get(modelIndex).key() <= nextSpriteKey) {
                flush();
                ALPHA_MODELS.get(modelIndex++).draw().run();
                previousMaterial = -1;
            }
            if (index == alphaCount) break;
            int source = (int) alphaOrder[index];
            int material = alphaMaterial[source];
            AlphaMaterial spec = ALPHA_MATERIALS.get(material);
            if (previousMaterial != material && previousMaterial >= 0) {
                AlphaMaterial prev = ALPHA_MATERIALS.get(previousMaterial);
                if (!prev.equals(spec)) flush();
            }
            if (count == MAX_BATCH) flush();
            batchTexture = spec.texture();
            batchFacing = spec.facing();
            batchRotating = spec.rotating();
            batchAlpha = true;
            batchWorldLit = spec.worldLit();
            ensureCapacity(count + 1);
            for (int channel = 0; channel < INSTANCE_FLOATS; channel++) {
                staging.putFloat(alphaData[source * INSTANCE_FLOATS + channel]);
            }
            count++;
            previousMaterial = material;
        }
        flush();
        alphaCount = 0;
        ALPHA_MATERIALS.clear();
        ALPHA_MATERIAL_IDS.clear();
        ALPHA_MODELS.clear();
    }

    private static void ensureCapacity(int required) {
        if (staging == null || required > bufferCapacity) {
            bufferCapacity = Math.min(MAX_BATCH, Math.max(required, Math.max(256, bufferCapacity * 2)));
            staging = ByteBuffer.allocateDirect(bufferCapacity * STRIDE).order(ByteOrder.nativeOrder());
        }
    }

    /**
     * Draw the sorted CPU batch through Minecraft-owned shaders. Normal frames
     * use GPU Transform Feedback geometry; this path preserves alpha order and
     * all sprite attributes even when the native GL path is unavailable.
     */
    public static void flush() {
        if (count == 0) return;
        RenderSystem.assertOnRenderThread();
        ByteBuffer input = staging.duplicate().order(ByteOrder.nativeOrder());
        input.flip();
        int drawn = count;
        try {
            if (!VfxNativeGpuGeometryBridge.renderInstances(input, count,
                    batchTexture, batchFacing, batchRotating, batchWorldLit, batchAlpha, camera)) {
                drawMinecraftBatch(input, drawn);
            }
            drawCalls++;
        } finally {
            staging.clear();
            count = 0;
        }
    }

    private static void drawMinecraftBatch(ByteBuffer bytes, int particles) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var blend = batchAlpha ? VfxBlendMode.ALPHA : VfxBlendMode.ADDITIVE;
        var consumer = buffers.getBuffer(VfxSpriteRenderTypes.get(batchTexture, blend));
        var view = camera.rotation();
        var buffer = bytes.order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int i = 0; i < particles; i++) {
            int o = i * INSTANCE_FLOATS;
            float x = buffer.get(o), y = buffer.get(o + 1), z = buffer.get(o + 2);
            float sx = buffer.get(o + 3) * 0.5f, sy = buffer.get(o + 4) * 0.5f;
            float red = buffer.get(o + 5), green = buffer.get(o + 6);
            float blue = buffer.get(o + 7), alpha = buffer.get(o + 8);
            float u0 = buffer.get(o + 9), v0 = buffer.get(o + 10);
            float u1 = buffer.get(o + 11), v1 = buffer.get(o + 12);
            float rx = buffer.get(o + 13), ry = buffer.get(o + 14), rz = buffer.get(o + 15);
            float block = buffer.get(o + 16), sky = buffer.get(o + 17);
            var poses = framePose;
            poses.pushPose();
            poses.translate(x,y,z);
            switch (batchFacing) {
                case CAMERA -> poses.mulPose(view);
                case CAMERA_HORIZONTAL ->
                        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-camera.getYRot()));
                case WORLD_X -> poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90f));
                case WORLD_Y -> poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90f));
                case VELOCITY -> {
                    float vx = buffer.get(o+18), vy = buffer.get(o+19), vz = buffer.get(o+20);
                    if (vx*vx+vy*vy+vz*vz < 1e-6f)
                        poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90f));
                    else poses.mulPose(new org.joml.Quaternionf().rotationTo(
                            0f,0f,1f,vx,vy,vz));
                }
                default -> { }
            }
            if (batchRotating) {
                poses.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rx));
                poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(ry));
                poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rz));
            }
            var matrix = poses.last().pose();
            int r = Math.clamp((int)(red*255f),0,255);
            int g = Math.clamp((int)(green*255f),0,255);
            int b = Math.clamp((int)(blue*255f),0,255);
            int a = Math.clamp((int)(alpha*255f),0,255);
            int packedLight = batchWorldLit ? (Math.clamp((int)(block*16f),0,15)<<4)
                    | (Math.clamp((int)(sky*16f),0,15)<<20)
                    : net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            minecraftVertex(consumer,matrix,-sx,-sy,u0,v1,r,g,b,a,packedLight);
            minecraftVertex(consumer,matrix, sx,-sy,u1,v1,r,g,b,a,packedLight);
            minecraftVertex(consumer,matrix, sx, sy,u1,v0,r,g,b,a,packedLight);
            minecraftVertex(consumer,matrix,-sx, sy,u0,v0,r,g,b,a,packedLight);
            poses.popPose();
        }
        buffers.endBatch();
    }

    private static void minecraftVertex(com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                         Matrix4f matrix,float x,float y,float u,float v,
                                         int r,int g,int b,int a,int light) {
        consumer.addVertex(matrix,x,y,0f).setColor(r,g,b,a).setUv(u,v)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(0f,1f,0f);
    }

    public static void finish() {
        try {
            flush();
            flushSortedAlpha();
        } finally {
            alphaCount = 0;
            ALPHA_MATERIALS.clear();
            ALPHA_MATERIAL_IDS.clear();
            ALPHA_MODELS.clear();
            batchTexture = null;
            batchRotating = false;
            batchAlpha = false;
            batchWorldLit = false;
            camera = null;
            framePose = null;
            mvp = null;
        }
    }

    /** Only parameters we touch: state is captured before render and restored in a finally block. */
    static final class GlState {
        int program, vao, array, activeTexture, texture0, texture2, texture3d, blendRgbSrc, blendRgbDst,
                blendAlphaSrc, blendAlphaDst, blendEquationRgb, blendEquationAlpha, depthFunc, colorMask;
        boolean blend, depth, cull, depthMask;
        private static final ByteBuffer CHANNEL_MASK = ByteBuffer.allocateDirect(4);

        private static int colorMaskBits() {
            CHANNEL_MASK.clear();
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, CHANNEL_MASK);
            int bits = 0;
            for (int i = 0; i < 4; i++)
                if (CHANNEL_MASK.get(i) != 0) bits |= 1 << i;
            return bits;
        }

        void capture() {
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            texture2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            texture3d = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            colorMask = colorMaskBits();
            depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            blendRgbSrc = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendRgbDst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendAlphaSrc = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendAlphaDst = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        }

        void restore() {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
            // Another render pass may leave texture unit 1/2 active. Restore
            // each binding on its own unit rather than corrupting that unit
            // with texture0 and silently breaking the following shader pass.
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture3d);
            GL13.glActiveTexture(activeTexture);
            GL14.glBlendFuncSeparate(blendRgbSrc,blendRgbDst,blendAlphaSrc,blendAlphaDst);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            if (blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(depthMask);
            GL11.glColorMask((colorMask & 1) != 0, (colorMask & 2) != 0,
                    (colorMask & 4) != 0, (colorMask & 8) != 0);
            GL11.glDepthFunc(depthFunc);
        }

        void assertRestored() {
            if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program
                    || GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != vao
                    || GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) != array
                    || GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != activeTexture
                    || GL11.glIsEnabled(GL11.GL_BLEND) != blend
                    || GL11.glIsEnabled(GL11.GL_DEPTH_TEST) != depth
                    || GL11.glIsEnabled(GL11.GL_CULL_FACE) != cull
                    || GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) != depthMask
                    || colorMaskBits() != colorMask
                    || GL11.glGetInteger(GL11.GL_DEPTH_FUNC) != depthFunc
                    || GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) != blendRgbSrc
                    || GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) != blendRgbDst
                    || GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA) != blendAlphaSrc
                    || GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) != blendAlphaDst
                    || GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB) != blendEquationRgb
                    || GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA) != blendEquationAlpha) {
                throw new IllegalStateException("GPU VFX leaked OpenGL render state");
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(activeTexture);
            if (texture != texture0) {
                throw new IllegalStateException("GPU VFX leaked texture unit 0 binding");
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            int lightTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(activeTexture);
            if (lightTexture != texture2) {
                throw new IllegalStateException("GPU VFX leaked lightmap texture unit 2 binding");
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            int volumeTexture = GL11.glGetInteger(GL12.GL_TEXTURE_BINDING_3D);
            GL13.glActiveTexture(activeTexture);
            if (volumeTexture != texture3d) {
                throw new IllegalStateException("GPU VFX leaked texture unit 3 volume binding");
            }
        }
    }
}
