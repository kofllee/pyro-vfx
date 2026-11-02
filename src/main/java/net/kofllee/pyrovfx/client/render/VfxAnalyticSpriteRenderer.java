package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.VfxAnalyticSpritePool;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;

/**
 * GPU resident initial xyz/velocity; camera-relative closed-form motion is
 * evaluated by a vertex shader. No CPU per-particle tick or per-frame upload.
 */
public final class VfxAnalyticSpriteRenderer {
    private static final int MAX_DRAW = 65_536;
    private static final ArrayDeque<Integer> RETIRED = new ArrayDeque<>();
    private static final FloatBuffer MATRIX = ByteBuffer.allocateDirect(64)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final String VS = """
            #version 330 core
            layout(location=0) in vec2 corner;
            layout(location=1) in vec2 uv;
            layout(location=2) in vec3 birth;
            layout(location=3) in vec3 velocity;
            uniform mat4 uMvp;
            uniform vec3 uCamera, uRight, uUp, uAcceleration;
            uniform vec2 uMotion;
            uniform vec2 uScale;
            uniform vec4 uUvRect;
            out vec2 vUv;
            void main() {
                vec3 p = birth - uCamera + velocity * uMotion.x
                         + uAcceleration * uMotion.y;
                vec3 cornerPosition = p + uRight * (corner.x * uScale.x)
                                        + uUp * (corner.y * uScale.y);
                gl_Position = uMvp * vec4(cornerPosition, 1.0);
                vUv = vec2(mix(uUvRect.x,uUvRect.z,uv.x),
                           1.0-mix(uUvRect.y,uUvRect.w,uv.y));
            }
            """;
    private static final String FS = """
            #version 330 core
            in vec2 vUv;
            uniform sampler2D uTexture;
            uniform vec4 uColor;
            out vec4 frag;
            void main() {
                frag = texture(uTexture,vUv) * uColor;
                if (frag.a <= 0.0) discard;
            }
            """;
    private static int program, vao, quadVbo, generation;
    private static int mvpLoc, cameraLoc, rightLoc, upLoc, accelerationLoc,
            motionLoc, sizeLoc, colorLoc, samplerLoc, uvRectLoc;
    private static volatile boolean reloadPending;
    private static boolean unavailable;
    private static int drawCalls, instances, uploadedBytes, uploadedDatasets;
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f();

    private VfxAnalyticSpriteRenderer() {}

    public static boolean gpuPossible() {
        if (unavailable || Boolean.getBoolean("pyrovfx.analytic.forceCpu")
                || !VfxInstancedSpriteRenderer.compatibleEnvironment()) return false;
        try {
            return GL.getCapabilities() != null && GL.getCapabilities().OpenGL33;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    public static void invalidateAfterReload() { reloadPending = true; unavailable = false; }
    public static void retireBuffer(int id) {
        if (id > 0) RETIRED.add(id); // called from Minecraft client tick/render thread
    }
    public static int generation() { return generation; }
    public static int drawCalls() { return drawCalls; }
    public static int instances() { return instances; }
    public static int uploadedBytes() { return uploadedBytes; }
    public static int uploadedDatasets() { return uploadedDatasets; }
    public static void resetFrameStats() {
        drawCalls = instances = uploadedBytes = uploadedDatasets = 0;
    }

    /** Called on the render thread on every world frame, including empty ones. */
    public static void maintain() {
        RenderSystem.assertOnRenderThread();
        if (reloadPending) {
            if (program != 0) GL20.glDeleteProgram(program);
            if (quadVbo != 0) GL15.glDeleteBuffers(quadVbo);
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
            program = quadVbo = vao = 0;
            generation++;
            reloadPending = false;
        }
        while (!RETIRED.isEmpty()) GL15.glDeleteBuffers(RETIRED.removeFirst());
    }

    public static boolean render(VfxAnalyticSpritePool pool, PoseStack pose, Camera camera, float partial) {
        RenderSystem.assertOnRenderThread();
        if (pool.liveCount() <= 0 || !gpuPossible()) return false;
        try {
            prepare();
            if (pool.gpuBuffer() == 0 || pool.gpuGeneration() != generation || pool.dirty()) {
                if (pool.gpuBuffer() != 0) retireBuffer(pool.gpuBuffer());
                int buffer = GL15.glGenBuffers();
                int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                try {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
                    ByteBuffer data = pool.data();
                    uploadedBytes += data.remaining();
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
                    uploadedDatasets++;
                } finally {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
                }
                pool.setGpuBuffer(buffer, generation);
            }

            VfxInstancedSpriteRenderer.GlState state = new VfxInstancedSpriteRenderer.GlState();
            state.capture();
            try {
                pool.updateAppearance(partial);
                GL20.glUseProgram(program);
                GL30.glBindVertexArray(vao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, pool.gpuBuffer());
                GL20.glVertexAttribPointer(2,3,GL11.GL_FLOAT,false,24,0L);
                GL20.glVertexAttribPointer(3,3,GL11.GL_FLOAT,false,24,12L);
                Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                        .mul(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
                MATRIX.clear();
                mvp.get(MATRIX);
                MATRIX.position(0);
                MATRIX.limit(16);
                GL20.glUniformMatrix4fv(mvpLoc,false,MATRIX);
                GL20.glUniform3f(cameraLoc,(float)camera.getPosition().x,
                        (float)camera.getPosition().y,(float)camera.getPosition().z);
                var facing = pool.definition().render().facing();
                if (facing == net.kofllee.pyrovfx.vfx.type.VfxFacingMode.WORLD_Z) {
                    RIGHT.set(1,0,0); UP.set(0,1,0);
                } else if (facing == net.kofllee.pyrovfx.vfx.type.VfxFacingMode.CAMERA_HORIZONTAL) {
                    float angle = (float)Math.toRadians(-camera.getYRot());
                    RIGHT.set((float)Math.cos(angle),0,-(float)Math.sin(angle));
                    UP.set(0,1,0);
                } else {
                    RIGHT.set(1,0,0).rotate(camera.rotation());
                    UP.set(0,1,0).rotate(camera.rotation());
                }
                GL20.glUniform3f(rightLoc,RIGHT.x,RIGHT.y,RIGHT.z);
                GL20.glUniform3f(upLoc,UP.x,UP.y,UP.z);
                var plan = pool.plan();
                GL20.glUniform3f(accelerationLoc,(float)plan.accelerationX(),
                        (float)plan.accelerationY(),(float)plan.accelerationZ());
                double[] coefficients = plan.coefficients(pool.age(),partial);
                GL20.glUniform2f(motionLoc,(float)coefficients[0],(float)coefficients[1]);
                GL20.glUniform2f(sizeLoc,(float)pool.scale().x,(float)pool.scale().y);
                GL20.glUniform4f(colorLoc,(float)pool.color().r(),(float)pool.color().g(),
                        (float)pool.color().b(),(float)pool.color().a());
                var rect = pool.uvRect();
                GL20.glUniform4f(uvRectLoc, rect.u0(), rect.v0(), rect.u1(), rect.v1());
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                int texture = Minecraft.getInstance().getTextureManager()
                        .getTexture(pool.definition().render().sprite().texture()).getId();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                GL20.glUniform1i(samplerLoc,0);
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,
                        GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(GL11.GL_LEQUAL);
                GL11.glDepthMask(true);
                GL11.glDisable(GL11.GL_CULL_FACE);
                int total=pool.liveCount();
                for(int start=0;start<total;start+=MAX_DRAW) {
                    int batch=Math.min(MAX_DRAW,total-start);
                    long offset=(long)start*VfxAnalyticSpritePool.STRIDE;
                    GL20.glVertexAttribPointer(2,3,GL11.GL_FLOAT,false,24,offset);
                    GL20.glVertexAttribPointer(3,3,GL11.GL_FLOAT,false,24,offset+12);
                    GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP,0,4,batch);
                    drawCalls++;
                    instances+=batch;
                }
            } finally {
                state.restore();
                if (Boolean.getBoolean("pyrovfx.render.stateAudit")) state.assertRestored();
            }
            return true;
        } catch (RuntimeException ex) {
            unavailable = true;
            com.mojang.logging.LogUtils.getLogger().error(
                    "Analytic GPU rendering failed; disabling backend for new instances",ex);
            return false;
        }
    }

    /** All GL objects are owned by the render thread and cleared during reload. */
    private static void prepare() {
        maintain();
        if (program != 0) return;
        int v=compile(GL20.GL_VERTEX_SHADER,VS);
        int f=compile(GL20.GL_FRAGMENT_SHADER,FS);
        program=GL20.glCreateProgram();
        GL20.glAttachShader(program,v);
        GL20.glAttachShader(program,f);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE)
            throw new IllegalStateException("Analytic shader linking: "+GL20.glGetProgramInfoLog(program));
        mvpLoc=uniform("uMvp");cameraLoc=uniform("uCamera");
        rightLoc=uniform("uRight");upLoc=uniform("uUp");
        accelerationLoc=uniform("uAcceleration");motionLoc=uniform("uMotion");
        sizeLoc=uniform("uScale");colorLoc=uniform("uColor");
        samplerLoc=uniform("uTexture");uvRectLoc=uniform("uUvRect");
        int oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            vao=GL30.glGenVertexArrays();
            quadVbo=GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,quadVbo);
            FloatBuffer vertices=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertices.put(new float[]{-0.5f,-0.5f,0,0, 0.5f,-0.5f,1,0,
                                    -0.5f,0.5f,0,1, 0.5f,0.5f,1,1}).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,16,0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,16,8L);
            GL20.glEnableVertexAttribArray(2);
            GL20.glEnableVertexAttribArray(3);
            GL33.glVertexAttribDivisor(2,1);
            GL33.glVertexAttribDivisor(3,1);
        } finally {
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,oldArray);
        }
    }
    private static int uniform(String id) { return GL20.glGetUniformLocation(program,id); }
    private static int compile(int type,String src) {
        int s=GL20.glCreateShader(type);
        GL20.glShaderSource(s,src);
        GL20.glCompileShader(s);
        if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)!=GL11.GL_TRUE) {
            String error=GL20.glGetShaderInfoLog(s);
            GL20.glDeleteShader(s);
            throw new IllegalStateException(error);
        }
        return s;
    }
}
