package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.VfxDenseSpritePool;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxBlendMode;
import net.kofllee.pyrovfx.vfx.type.VfxFacingMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
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

/**
 * Opt-in-by-capability, vanilla-only OpenGL 3.3 sprite instancing.
 *
 * The shader and buffers are completely private to the VFX pass. All altered
 * OpenGL bindings and blend/depth/cull state are captured and restored.
 * Iris/Oculus (including shader-pack toggling) take the original RenderType path.
 *
 * Transparent alpha blending is deliberately not reordered into instance
 * groups: only additive sprites can safely merge across particles/materials.
 */
public final class VfxInstancedSpriteRenderer {
    private static final int INSTANCE_FLOATS = 13;
    private static final int STRIDE = INSTANCE_FLOATS * Float.BYTES;
    private static final int MAX_BATCH = 16_384;
    private static final int QUAD_STRIDE = 4 * Float.BYTES;

    private static final String VERTEX = """
            #version 330 core
            layout (location=0) in vec2 aCorner;
            layout (location=1) in vec2 aUv;
            layout (location=2) in vec3 iPosition;
            layout (location=3) in vec2 iSize;
            layout (location=4) in vec4 iColor;
            layout (location=5) in vec4 iUvRect;
            uniform mat4 uMvp;
            uniform vec3 uRight;
            uniform vec3 uUp;
            out vec2 vUv;
            out vec4 vColor;
            void main() {
                vec3 position = iPosition + uRight * (aCorner.x * iSize.x)
                                         + uUp * (aCorner.y * iSize.y);
                gl_Position = uMvp * vec4(position, 1.0);
                vUv = vec2(mix(iUvRect.x, iUvRect.z, aUv.x),
                           mix(iUvRect.w, iUvRect.y, aUv.y));
                vColor = iColor;
            }
            """;
    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec4 vColor;
            uniform sampler2D uTexture;
            uniform int uDebug;
            out vec4 fragColor;
            void main() {
                if (uDebug != 0) { fragColor = vec4(1.0, 0.0, 0.0, 1.0); return; }
                vec4 color = texture(uTexture, vUv) * vColor;
                if (color.a <= 0.0) discard;
                fragColor = color;
            }
            """;

    private static int program;
    private static int vao;
    private static int quadBuffer;
    private static int instanceBuffer;
    private static int bufferCapacity;
    private static ByteBuffer staging;
    private static boolean unavailable;
    private static volatile boolean reloadPending;

    private static int mvpUniform;
    private static int rightUniform;
    private static int upUniform;
    private static int textureUniform;
    private static int debugUniform;

    private static final FloatBuffer MVP_DATA = ByteBuffer.allocateDirect(16 * Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();

    private static ResourceLocation batchTexture;
    private static VfxFacingMode batchFacing;
    private static int count;
    private static Matrix4f mvp;
    private static Camera camera;
    private static float partialTick;
    private static float rightX, rightY, rightZ, upX, upY, upZ;
    private static final Vector3f BASIS_RIGHT = new Vector3f();
    private static final Vector3f BASIS_UP = new Vector3f();
    private static int drawCalls;
    private static int submitted;
    private static int culled;
    private static int subpixelCulled;
    private static boolean loggedDebugMvp;

    private VfxInstancedSpriteRenderer() {}

    public static boolean isSupported(VfxEmitterDefinition definition) {
        if (!compatibleEnvironment() || unavailable) return false;
        // This GPU vertex layout has no per-particle rotation; preserve visual
        // parity by rendering rotating dense sprites through Minecraft quads.
        if (definition.rotation().mode() != net.kofllee.pyrovfx.vfx.type.VfxRotationMode.NONE) return false;
        if (definition.render().sprite() == null
                || definition.render().sprite().blendMode() != VfxBlendMode.ADDITIVE
                || definition.render().environmentLighting()) {
            return false;
        }
        VfxFacingMode facing = definition.render().facing();
        return facing == VfxFacingMode.CAMERA || facing == VfxFacingMode.CAMERA_HORIZONTAL
                || facing == VfxFacingMode.WORLD_Z;
    }

    static boolean compatibleEnvironment() {
        if (Boolean.getBoolean("pyrovfx.render.forceLegacy")) return false;
        // Shader mods can replace framebuffer/program state at runtime. Never
        // issue raw GL calls inside their pipeline without explicit integration.
        ModList mods = ModList.get();
        if (mods != null && (mods.isLoaded("iris") || mods.isLoaded("oculus")
                || mods.isLoaded("neoculus") || mods.isLoaded("forgeoculus")
                || mods.isLoaded("embeddium") || mods.isLoaded("sodium"))) {
            return false;
        }
        return true;
    }

    public static boolean ready() {
        if (unavailable || !GL.getCapabilities().OpenGL33) return false;
        if (reloadPending) {
            disposeOnRenderThread();
            reloadPending = false;
        }
        if (program != 0) return true;
        try {
            initialize();
            return true;
        } catch (RuntimeException ex) {
            // Render pass automatically falls back without killing Minecraft.
            unavailable = true;
            disposeOnRenderThread();
            com.mojang.logging.LogUtils.getLogger()
                    .warn("GPU VFX instancing unavailable, switching to vanilla RenderType renderer", ex);
            return false;
        }
    }

    /** Resource reload listeners can be invoked off-thread: actual GL cleanup is deferred. */
    public static void invalidateAfterReload() {
        reloadPending = true;
        unavailable = false;
    }

    public static void begin(PoseStack poses, Camera view, float tick) {
        RenderSystem.assertOnRenderThread();
        camera = view;
        partialTick = tick;
        // RenderType's entity shaders multiply Minecraft's camera model-view
        // matrix too. Omitting it projects +Z world geometry behind the camera.
        mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(RenderSystem.getModelViewMatrix())
                .mul(poses.last().pose());
        if (Boolean.getBoolean("pyrovfx.render.debugSolid") && !loggedDebugMvp) {
            loggedDebugMvp = true;
            float x=0f,y=-1.5f,z=12.5f;
            float cx=mvp.m00()*x + mvp.m10()*y + mvp.m20()*z + mvp.m30();
            float cy=mvp.m01()*x + mvp.m11()*y + mvp.m21()*z + mvp.m31();
            float cz=mvp.m02()*x + mvp.m12()*y + mvp.m22()*z + mvp.m32();
            float cw=mvp.m03()*x + mvp.m13()*y + mvp.m23()*z + mvp.m33();
            System.out.println("[VFX GPU DEBUG] clip=("+cx+","+cy+","+cz+","+cw+") view="+view.getPosition());
        }
        count = 0;
        batchTexture = null;
        batchFacing = null;
        drawCalls = submitted = culled = subpixelCulled = 0;
    }

    public static boolean matches(VfxEmitterDefinition definition) {
        return isSupported(definition) && ready();
    }

    public static int drawCalls() { return drawCalls; }
    public static int submittedParticles() { return submitted; }
    public static int culledParticles() { return culled; }
    public static int subpixelCulledParticles() { return subpixelCulled; }
    public static void resetFrameStats() { drawCalls = submitted = culled = subpixelCulled = 0; }

    public static void append(VfxDenseSpritePool pool) {
        VfxEmitterDefinition definition = pool.definition();
        ResourceLocation texture = definition.render().sprite().texture();
        VfxFacingMode facing = definition.render().facing();
        if (batchTexture != null && (!batchTexture.equals(texture) || batchFacing != facing)) {
            flush();
        }
        batchTexture = texture;
        batchFacing = facing;
        pool.writeInstances(VfxInstancedSpriteRenderer::put, camera, partialTick);
    }

    private static void put(double x, double y, double z, float sizeX, float sizeY,
                            float red, float green, float blue, float alpha,
                            float u0, float v0, float u1, float v1) {
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
                Minecraft.getInstance().getWindow().getHeight(), lodThreshold())) {
            subpixelCulled++;
            return;
        }
        if (count >= MAX_BATCH) flush();
        ensureCapacity(count + 1);
        staging.putFloat((float) dx).putFloat((float) dy).putFloat((float) dz);
        staging.putFloat(sizeX).putFloat(sizeY);
        staging.putFloat(red).putFloat(green).putFloat(blue).putFloat(alpha);
        staging.putFloat(u0).putFloat(v0).putFloat(u1).putFloat(v1);
        count++;
        submitted++;
    }

    private static final double MIN_DIAMETER_PX = parseLodThreshold();

    private static double parseLodThreshold() {
        try {
            return Math.clamp(Double.parseDouble(
                    System.getProperty("pyrovfx.render.minDiameterPx", "0.20")), 0.0, 2.0);
        } catch (NumberFormatException ignored) {
            return 0.20;
        }
    }

    private static double lodThreshold() {
        return MIN_DIAMETER_PX;
    }

    private static void ensureCapacity(int required) {
        if (staging == null || required > bufferCapacity) {
            bufferCapacity = Math.min(MAX_BATCH, Math.max(required, Math.max(256, bufferCapacity * 2)));
            staging = ByteBuffer.allocateDirect(bufferCapacity * STRIDE).order(ByteOrder.nativeOrder());
        }
    }

    /** Flush all pending Minecraft buffers before this call; never interleave raw GL with queued vertices. */
    public static void flush() {
        if (count == 0) return;
        RenderSystem.assertOnRenderThread();
        GlState state = new GlState();
        try {
            state.capture();
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
            staging.flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, staging, GL15.GL_STREAM_DRAW);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int texture = Minecraft.getInstance().getTextureManager().getTexture(batchTexture).getId();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL20.glUniform1i(textureUniform, 0);
            GL20.glUniform1i(debugUniform,
                    Boolean.getBoolean("pyrovfx.render.debugSolid") ? 1 : 0);
            MVP_DATA.clear();
            mvp.get(MVP_DATA);
            MVP_DATA.position(0);
            MVP_DATA.limit(16);
            GL20.glUniformMatrix4fv(mvpUniform, false, MVP_DATA);
            basis(batchFacing);
            GL20.glUniform3f(rightUniform, rightX, rightY, rightZ);
            GL20.glUniform3f(upUniform, upX, upY, upZ);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            if (Boolean.getBoolean("pyrovfx.render.debugSolid")) {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
            }
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, count);
            if (Boolean.getBoolean("pyrovfx.render.debugSolid")) {
                System.out.println("[VFX GPU DEBUG] draw count="+count+" glError="+GL11.glGetError());
            }
            drawCalls++;
        } finally {
            state.restore();
            if (Boolean.getBoolean("pyrovfx.render.stateAudit")) {
                state.assertRestored();
            }
            staging.clear();
            count = 0;
        }
    }

    public static void finish() {
        flush();
        batchTexture = null;
        camera = null;
        mvp = null;
    }

    private static void basis(VfxFacingMode facing) {
        if (facing == VfxFacingMode.WORLD_Z) {
            rightX=1; rightY=0; rightZ=0; upX=0; upY=1; upZ=0;
        } else if (facing == VfxFacingMode.CAMERA_HORIZONTAL) {
            float angle = (float) Math.toRadians(-camera.getYRot());
            rightX=(float)Math.cos(angle); rightY=0; rightZ=-(float)Math.sin(angle);
            upX=0; upY=1; upZ=0;
        } else {
            BASIS_RIGHT.set(1, 0, 0).rotate(camera.rotation());
            BASIS_UP.set(0, 1, 0).rotate(camera.rotation());
            rightX=BASIS_RIGHT.x; rightY=BASIS_RIGHT.y; rightZ=BASIS_RIGHT.z;
            upX=BASIS_UP.x; upY=BASIS_UP.y; upZ=BASIS_UP.z;
        }
    }

    private static void initialize() {
        int vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = shader(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        int newProgram = GL20.glCreateProgram();
        GL20.glAttachShader(newProgram, vertex);
        GL20.glAttachShader(newProgram, fragment);
        GL20.glLinkProgram(newProgram);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(newProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String info = GL20.glGetProgramInfoLog(newProgram);
            GL20.glDeleteProgram(newProgram);
            throw new IllegalStateException("VFX GPU program link: " + info);
        }
        program = newProgram;
        mvpUniform = GL20.glGetUniformLocation(program, "uMvp");
        rightUniform = GL20.glGetUniformLocation(program, "uRight");
        upUniform = GL20.glGetUniformLocation(program, "uUp");
        textureUniform = GL20.glGetUniformLocation(program, "uTexture");
        debugUniform = GL20.glGetUniformLocation(program, "uDebug");

        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            vao = GL30.glGenVertexArrays();
            quadBuffer = GL15.glGenBuffers();
            instanceBuffer = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadBuffer);
            FloatBuffer corners = ByteBuffer.allocateDirect(4 * 4 * Float.BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            corners.put(new float[]{-0.5f,-0.5f,0,0, 0.5f,-0.5f,1,0,
                                    -0.5f,0.5f,0,1, 0.5f,0.5f,1,1}).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, corners, GL15.GL_STATIC_DRAW);
            attr(0,2,QUAD_STRIDE,0,0);
            attr(1,2,QUAD_STRIDE,2*Float.BYTES,0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 256L * STRIDE, GL15.GL_STREAM_DRAW);
            attr(2,3,STRIDE,0,1);
            attr(3,2,STRIDE,3*Float.BYTES,1);
            attr(4,4,STRIDE,5*Float.BYTES,1);
            attr(5,4,STRIDE,9*Float.BYTES,1);
        } finally {
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArray);
        }
    }

    private static void attr(int slot, int size, int stride, long offset, int divisor) {
        GL20.glEnableVertexAttribArray(slot);
        GL20.glVertexAttribPointer(slot,size,GL11.GL_FLOAT,false,stride,offset);
        GL33.glVertexAttribDivisor(slot,divisor);
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String info = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("VFX GPU shader compilation: " + info);
        }
        return shader;
    }

    private static void disposeOnRenderThread() {
        RenderSystem.assertOnRenderThread();
        if (instanceBuffer != 0) GL15.glDeleteBuffers(instanceBuffer);
        if (quadBuffer != 0) GL15.glDeleteBuffers(quadBuffer);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (program != 0) GL20.glDeleteProgram(program);
        instanceBuffer = quadBuffer = vao = program = 0;
        staging = null;
        bufferCapacity = 0;
    }

    /** Only parameters we touch: state is captured before render and restored in a finally block. */
    static final class GlState {
        int program, vao, array, activeTexture, texture0, blendRgbSrc, blendRgbDst,
                blendAlphaSrc, blendAlphaDst, depthFunc;
        boolean blend, depth, cull, depthMask;

        void capture() {
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            blendRgbSrc = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendRgbDst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendAlphaSrc = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendAlphaDst = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        }

        void restore() {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, array);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(activeTexture);
            GL14.glBlendFuncSeparate(blendRgbSrc,blendRgbDst,blendAlphaSrc,blendAlphaDst);
            if (blend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(depthMask);
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
                    || GL11.glGetInteger(GL11.GL_DEPTH_FUNC) != depthFunc
                    || GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) != blendRgbSrc
                    || GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) != blendRgbDst
                    || GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA) != blendAlphaSrc
                    || GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) != blendAlphaDst) {
                throw new IllegalStateException("GPU VFX leaked OpenGL render state");
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(activeTexture);
            if (texture != texture0) {
                throw new IllegalStateException("GPU VFX leaked texture unit 0 binding");
            }
        }
    }
}
