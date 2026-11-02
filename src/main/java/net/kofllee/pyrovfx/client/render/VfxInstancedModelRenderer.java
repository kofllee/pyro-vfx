package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.client.render.VfxBakedModelMeshCache.Mesh;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.client.vfx.VfxDenseParticlePool;
import net.kofllee.pyrovfx.client.vfx.VfxAnalyticParticlePool;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxModelRenderDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer;
import net.kofllee.pyrovfx.vfx.type.VfxModelSourceType;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Quaternionf;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * GPU instancing of stable untinted CUSTOM, BLOCK and ITEM baked quads.
 * Rotation, scale, color and packed light are unique per instance.
 * Entity animation, tint-indexed and depth-sorted translucent models fall back.
 */
public final class VfxInstancedModelRenderer {
    private static final String VERTEX = """
            #version 330 core
            layout(location=0) in vec3 aPosition;
            layout(location=1) in vec2 aUv;
            layout(location=7) in vec4 aBakedColor;
            layout(location=2) in vec3 iPosition;
            layout(location=3) in vec3 iScale;
            layout(location=4) in vec4 iColor;
            layout(location=5) in vec4 iRotation;
            layout(location=6) in vec2 iLight;
            layout(location=8) in vec3 iBirthVelocity;
            uniform mat4 uMvp;
            uniform int uAnalytic;
            uniform vec3 uCamera, uAcceleration, uSharedScale;
            uniform vec2 uMotion;
            uniform vec4 uSharedColor;
            uniform vec4 uSharedRotation;
            out vec2 vUv;
            out vec4 vColor;
            out vec4 vBakedColor;
            out vec2 vLight;
            void main() {
                vec3 scale = uAnalytic != 0 ? uSharedScale : iScale;
                vec4 rot = uAnalytic != 0 ? uSharedRotation : iRotation;
                vec3 origin = iPosition;
                if (uAnalytic != 0) {
                    origin = iPosition - uCamera + iBirthVelocity * uMotion.x
                             + uAcceleration * uMotion.y;
                }
                vec3 p = (aPosition - vec3(0.5)) * scale;
                vec3 t = 2.0 * cross(rot.xyz, p);
                vec3 point = origin + p + rot.w * t + cross(rot.xyz, t);
                gl_Position = uMvp * vec4(point, 1.0);
                vUv = aUv;
                vColor = uAnalytic != 0 ? uSharedColor : iColor;
                vBakedColor = aBakedColor;
                vLight = iLight;
            }
            """;
    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec4 vColor;
            in vec4 vBakedColor;
            in vec2 vLight;
            uniform sampler2D uTexture;
            uniform sampler2D uLightTexture;
            uniform int uUseLight;
            uniform int uCutout;
            out vec4 fragColor;
            void main() {
                vec4 texel = texture(uTexture, vUv) * vColor * vBakedColor;
                if (uCutout != 0 && texel.a < 0.1) discard;
                vec3 light = uUseLight != 0
                        ? texture(uLightTexture, vLight).rgb : vec3(1.0);
                fragColor = vec4(texel.rgb * light, texel.a);
            }
            """;
    private static final int INSTANCE_FLOATS = 16, STRIDE = INSTANCE_FLOATS * Float.BYTES;
    private static final int VERTEX_FLOATS = 9;
    private static final int MAX_BATCH = 16384;

    private static final FloatBuffer MATRIX_BUFFER = ByteBuffer.allocateDirect(16 * Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static ByteBuffer instances;
    private static int program, instanceVbo, vao, analyticVao, vertexVbo;
    private static int capacity, mvpUniform;
    private static boolean unavailable;
    private static volatile boolean reloadPending;
    private static int lastDrawCalls;
    private static int lastInstances;
    private static int residentUploads, residentUploadBytes;
    private static final Quaternionf ROTATION = new Quaternionf();


    private VfxInstancedModelRenderer() {}

    public static boolean accepts(VfxEmitterDefinition definition) {
        if (Boolean.getBoolean("pyrovfx.model.forceLegacy")
                || !VfxInstancedSpriteRenderer.compatibleEnvironment()
                || unavailable || !GL.getCapabilities().OpenGL33) return false;
        return supportsGeometry(definition);
    }

    /** Pure eligibility check shared with server-side routing tests. */
    public static boolean supportsGeometry(VfxEmitterDefinition definition) {
        if (definition.render().type() != VfxRenderType.MODEL
                || definition.render().model() == null) return false;
        var model = definition.render().model();
        return model.source() != VfxModelSourceType.ENTITY
                && model.renderLayer() != VfxModelRenderLayer.TRANSLUCENT;
    }

    public static void invalidateAfterReload() {
        reloadPending = true;
        unavailable = false;
    }

    public static int lastDrawCalls() { return lastDrawCalls; }
    public static int lastInstances() { return lastInstances; }
    public static int residentUploads() { return residentUploads; }
    public static int residentUploadBytes() { return residentUploadBytes; }
    public static void resetFrameStats() { lastDrawCalls = lastInstances = residentUploads = residentUploadBytes = 0; }

    /** One birth-state VBO per cohort; model transforms are advanced by the vertex shader. */
    public static boolean renderAnalytic(VfxAnalyticParticlePool pool, PoseStack poses,
                                         Camera camera, float partialTick) {
        if (!pool.model() || pool.liveCount() <= 0 || !accepts(pool.definition())) return false;
        RenderSystem.assertOnRenderThread();
        if (reloadPending) {
            destroy();
            reloadPending = false;
        }
        var resource = pool.definition().render().model();
        Mesh mesh = VfxBakedModelMeshCache.get(resource);
        if (mesh == null) return false;
        boolean submitted = false;
        try {
            if (program == 0) initialize();
            if (pool.gpuBuffer() == 0 || pool.gpuGeneration() != VfxAnalyticSpriteRenderer.generation()
                    || pool.dirty()) {
                if (pool.gpuBuffer() != 0) VfxAnalyticSpriteRenderer.retireBuffer(pool.gpuBuffer());
                int oldArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                int buffer = GL15.glGenBuffers();
                try {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
                    ByteBuffer data = pool.data();
                    residentUploadBytes += data.remaining();
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
                    residentUploads++;
                } finally {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArray);
                }
                pool.setGpuBuffer(buffer, VfxAnalyticSpriteRenderer.generation());
            }
            pool.updateAppearance(partialTick);
            Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                    .mul(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
            for (int start = 0; start < pool.liveCount(); start += MAX_BATCH) {
                int count = Math.min(MAX_BATCH, pool.liveCount() - start);
                drawResident(mesh, mvp, count, resource.renderLayer(), pool, start, camera, partialTick);
                submitted = true;
                lastDrawCalls++;
                lastInstances += count;
            }
            return true;
        } catch (RuntimeException ex) {
            unavailable = true;
            com.mojang.logging.LogUtils.getLogger().warn(
                    "GPU resident model rendering failed; switching to Minecraft fallback", ex);
            return submitted; // Do not redraw already submitted batches through Minecraft fallback.
        }
    }

    /** Render dense primitive model state directly, without materializing particle objects. */
    public static boolean renderDense(VfxDenseParticlePool pool, PoseStack poses,
                                      Camera camera, float partialTick) {
        if (pool.size() == 0 || !pool.model() || !accepts(pool.definition())
                || !pool.fullyOpaque(partialTick)) return false;
        RenderSystem.assertOnRenderThread();
        if (reloadPending) {
            destroy();
            reloadPending = false;
        }
        VfxModelRenderDefinition resource = pool.definition().render().model();
        Mesh mesh = VfxBakedModelMeshCache.get(resource);
        if (mesh == null) return false;
        try {
            if (program == 0) initialize();
            Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                    .mul(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
            Vec3 cameraPos = camera.getPosition();
            ensureCapacity(MAX_BATCH);
            instances.clear();
            final int[] batched = {0};
            final Mesh readyMesh = mesh;
            pool.writeModelInstances((x,y,z,sx,sy,sz,r,g,b,a,rx,ry,rz) -> {
                double px = x - cameraPos.x;
                double py = y - cameraPos.y;
                double pz = z - cameraPos.z;
                double radius = 0.87 * Math.max(Math.max(Math.abs(sx), Math.abs(sy)), Math.abs(sz));
                if (!VfxRenderVisibility.visible(mvp, px, py, pz, radius)) return;
                instances.putFloat((float)px).putFloat((float)py).putFloat((float)pz);
                instances.putFloat(sx).putFloat(sy).putFloat(sz);
                instances.putFloat(r).putFloat(g).putFloat(b).putFloat(a);
                ROTATION.rotationXYZ((float)Math.toRadians(rx),
                        (float)Math.toRadians(ry), (float)Math.toRadians(rz));
                instances.putFloat(ROTATION.x).putFloat(ROTATION.y)
                        .putFloat(ROTATION.z).putFloat(ROTATION.w);
                int light = pool.definition().render().environmentLighting()
                        ? VfxRenderLight.getParticleLight(x, y, z)
                        : LightTexture.FULL_BRIGHT;
                instances.putFloat((((light >> 4) & 15) + 0.5f) / 16f);
                instances.putFloat((((light >> 20) & 15) + 0.5f) / 16f);
                if (++batched[0] == MAX_BATCH) {
                    instances.flip();
                    draw(readyMesh, mvp, batched[0], resource.renderLayer(),
                            pool.definition().render().environmentLighting());
                    lastDrawCalls++;
                    lastInstances += batched[0];
                    batched[0] = 0;
                    instances.clear();
                }
            }, partialTick);
            if (batched[0] > 0) {
                instances.flip();
                draw(mesh, mvp, batched[0], resource.renderLayer(),
                        pool.definition().render().environmentLighting());
                lastDrawCalls++;
                lastInstances += batched[0];
            }
            return true;
        } catch (RuntimeException ex) {
            unavailable = true;
            com.mojang.logging.LogUtils.getLogger().error(
                    "Dense model GPU instancing failed; switching to legacy renderer", ex);
            return true; // avoid duplicate rendering if an earlier batch was submitted
        }
    }

    public static boolean render(List<ClientVfxParticle> particles, PoseStack poses,
                                 Camera camera, float partialTick) {
        if (particles.isEmpty() || !accepts(particles.getFirst().emitterDefinition())) return false;
        RenderSystem.assertOnRenderThread();
        if (reloadPending) {
            destroy();
            reloadPending = false;
        }
        VfxEmitterDefinition definition = particles.getFirst().emitterDefinition();
        VfxModelRenderDefinition resource = definition.render().model();
        Mesh mesh = VfxBakedModelMeshCache.get(resource);
        if (mesh == null) return false;
        // No partial rendering: if any instance needs a semantics-rich path,
        // the entire emitter is left to the Minecraft renderer.
        for (ClientVfxParticle particle : particles) {
            if (particle.emitterDefinition() != definition) return false;
            if (resource.renderLayer() == VfxModelRenderLayer.SOLID
                    && Math.abs(particle.interpolatedColor(partialTick).a() - 1.0) > 1.0e-6) return false;
        }
        try {
            if (program == 0) initialize();
            Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                    .mul(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
            Vec3 cameraPos = camera.getPosition();
            int start = 0;
            while (start < particles.size()) {
                int batchCount = Math.min(MAX_BATCH, particles.size() - start);
                ensureCapacity(batchCount);
                instances.clear();
                int drawn = 0;
                for (int i = start; i < start + batchCount; i++) {
                    ClientVfxParticle particle = particles.get(i);
                    Vec3 pos = particle.interpolatedPosition(partialTick).subtract(cameraPos);
                    Vec3 scale = particle.interpolatedScale(partialTick);
                    Vec3 rotation = particle.interpolatedRotation(partialTick);
                    var color = particle.interpolatedColor(partialTick);
                    double radius = 0.87 * Math.max(
                            Math.max(Math.abs(scale.x), Math.abs(scale.y)), Math.abs(scale.z));
                    if (!VfxRenderVisibility.visible(mvp, pos.x, pos.y, pos.z, radius)) {
                        continue;
                    }
                    instances.putFloat((float)pos.x).putFloat((float)pos.y).putFloat((float)pos.z);
                    instances.putFloat((float)scale.x).putFloat((float)scale.y).putFloat((float)scale.z);
                    instances.putFloat((float)color.r()).putFloat((float)color.g())
                            .putFloat((float)color.b()).putFloat((float)color.a());
                    ROTATION.rotationXYZ((float) Math.toRadians(rotation.x),
                            (float) Math.toRadians(rotation.y), (float) Math.toRadians(rotation.z));
                    instances.putFloat(ROTATION.x).putFloat(ROTATION.y)
                            .putFloat(ROTATION.z).putFloat(ROTATION.w);
                    int light = definition.render().environmentLighting()
                            ? VfxRenderLight.getParticleLight(particle, partialTick)
                            : LightTexture.FULL_BRIGHT;
                    instances.putFloat((((light >> 4) & 15) + 0.5f) / 16f);
                    instances.putFloat((((light >> 20) & 15) + 0.5f) / 16f);
                    drawn++;
                }
                if (drawn != 0) {
                    instances.flip();
                    draw(mesh, mvp, drawn, resource.renderLayer(),
                            definition.render().environmentLighting());
                    lastDrawCalls++;
                    lastInstances += drawn;
                }
                start += batchCount;
            }
            return true;
        } catch (RuntimeException ex) {
            unavailable = true;
            com.mojang.logging.LogUtils.getLogger()
                    .warn("GPU model instancing failed; future frames will use Minecraft model rendering", ex);
            return true; // do not submit the same models twice after a partial GPU draw
        }
    }

    private static void ensureCapacity(int count) {
        if (capacity >= count && instances != null) return;
        capacity = Math.min(MAX_BATCH, Math.max(128, Math.max(count, capacity * 2)));
        instances = ByteBuffer.allocateDirect(capacity * STRIDE).order(ByteOrder.nativeOrder());
    }

    private static void draw(Mesh mesh, Matrix4f mvp, int count,
                             VfxModelRenderLayer layer, boolean worldLit) {
        drawModel(mesh, mvp, count, layer, worldLit, null, 0, null, 0);
    }

    private static void drawResident(Mesh mesh, Matrix4f mvp, int count,
                                     VfxModelRenderLayer layer, VfxAnalyticParticlePool pool,
                                     int start, Camera camera, float partialTick) {
        drawModel(mesh, mvp, count, layer, false, pool, start, camera, partialTick);
    }

    private static void drawModel(Mesh mesh, Matrix4f mvp, int count,
                                  VfxModelRenderLayer layer, boolean worldLit,
                                  VfxAnalyticParticlePool resident, int start,
                                  Camera camera, float partialTick) {
        VfxInstancedSpriteRenderer.GlState state = new VfxInstancedSpriteRenderer.GlState();
        state.capture();
        int previousShaderLight = RenderSystem.getShaderTexture(2);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        int priorLight = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        try {
            // AFTER_PARTICLES does not guarantee the vanilla shader lightmap
            // remains bound to shader sampler 2. Bind it explicitly for lit
            // models instead of sampling texture id 0 (black geometry).
            if (worldLit) Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(resident == null ? vao : analyticVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.geometryVbo());
            attribute(0,3,VERTEX_FLOATS*Float.BYTES,0,0);
            attribute(1,2,VERTEX_FLOATS*Float.BYTES,3L*Float.BYTES,0);
            attribute(7,4,VERTEX_FLOATS*Float.BYTES,5L*Float.BYTES,0);
            if (resident == null) {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STREAM_DRAW);
            } else {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, resident.gpuBuffer());
                long base = (long) start * VfxAnalyticParticlePool.STRIDE;
                attribute(2, 3, VfxAnalyticParticlePool.STRIDE, base, 1);
                attribute(8, 3, VfxAnalyticParticlePool.STRIDE, base + 12, 1);
            }
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uAnalytic"), resident == null ? 0 : 1);
            if (resident != null) {
                var coefficients = resident.plan().coefficients(resident.age(), partialTick);
                GL20.glUniform2f(GL20.glGetUniformLocation(program, "uMotion"),
                        (float) coefficients[0], (float) coefficients[1]);
                GL20.glUniform3f(GL20.glGetUniformLocation(program, "uCamera"),
                        (float) camera.getPosition().x,
                        (float) camera.getPosition().y, (float) camera.getPosition().z);
                GL20.glUniform3f(GL20.glGetUniformLocation(program, "uAcceleration"),
                        (float) resident.plan().accelerationX(), (float) resident.plan().accelerationY(),
                        (float) resident.plan().accelerationZ());
                GL20.glUniform3f(GL20.glGetUniformLocation(program, "uSharedScale"),
                        (float) resident.scale().x, (float) resident.scale().y, (float) resident.scale().z);
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "uSharedColor"),
                        (float) resident.color().r(), (float) resident.color().g(),
                        (float) resident.color().b(), (float) resident.color().a());
                Vec3 angles = resident.modelRotation(partialTick);
                ROTATION.rotationXYZ((float) Math.toRadians(angles.x),
                        (float) Math.toRadians(angles.y),
                        (float) Math.toRadians(angles.z));
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "uSharedRotation"),
                        ROTATION.x, ROTATION.y, ROTATION.z, ROTATION.w);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, Minecraft.getInstance().getTextureManager()
                    .getTexture(InventoryMenu.BLOCK_ATLAS).getId());
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uTexture"), 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, RenderSystem.getShaderTexture(2));
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uLightTexture"), 2);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uUseLight"), worldLit ? 1 : 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uCutout"),
                    layer == VfxModelRenderLayer.CUTOUT ? 1 : 0);
            MATRIX_BUFFER.clear();
            mvp.get(MATRIX_BUFFER);
            MATRIX_BUFFER.position(0);
            MATRIX_BUFFER.limit(16);
            GL20.glUniformMatrix4fv(mvpUniform, false, MATRIX_BUFFER);
            if (layer == VfxModelRenderLayer.ADDITIVE) {
                GL11.glEnable(GL11.GL_BLEND);
                org.lwjgl.opengl.GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE,
                        GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            } else GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(layer != VfxModelRenderLayer.ADDITIVE);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, mesh.vertexCount(), count);
        } finally {
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, priorLight);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            RenderSystem.setShaderTexture(2, previousShaderLight);
            state.restore();
            if (Boolean.getBoolean("pyrovfx.render.stateAudit")) {
                state.assertRestored();
            }
        }
    }

    private static void initialize() {
        int vs = shader(GL20.GL_VERTEX_SHADER, VERTEX);
        int fs = shader(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        int newProgram = GL20.glCreateProgram();
        GL20.glAttachShader(newProgram, vs);
        GL20.glAttachShader(newProgram, fs);
        GL20.glLinkProgram(newProgram);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(newProgram, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            String info = GL20.glGetProgramInfoLog(newProgram);
            GL20.glDeleteProgram(newProgram);
            throw new IllegalStateException(info);
        }
        program = newProgram;
        mvpUniform = GL20.glGetUniformLocation(program, "uMvp");
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            vao = GL30.glGenVertexArrays();
            analyticVao = GL30.glGenVertexArrays();
            vertexVbo = GL15.glGenBuffers();
            instanceVbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 1L, GL15.GL_STATIC_DRAW);
            attribute(0,3,VERTEX_FLOATS*Float.BYTES,0,0);
            attribute(1,2,VERTEX_FLOATS*Float.BYTES,3L*Float.BYTES,0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 128L*STRIDE,GL15.GL_STREAM_DRAW);
            attribute(2,3,STRIDE,0,1);
            attribute(3,3,STRIDE,3L*Float.BYTES,1);
            attribute(4,4,STRIDE,6L*Float.BYTES,1);
            attribute(5,4,STRIDE,10L*Float.BYTES,1);
            attribute(6,2,STRIDE,14L*Float.BYTES,1);
        } finally {
            GL30.glBindVertexArray(oldVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArray);
        }
    }

    private static void attribute(int location,int count,int stride,long offset,int divisor) {
        GL20.glEnableVertexAttribArray(location);
        GL20.glVertexAttribPointer(location,count,GL11.GL_FLOAT,false,stride,offset);
        GL33.glVertexAttribDivisor(location,divisor);
    }

    private static int shader(int type,String source) {
        int id=GL20.glCreateShader(type);
        GL20.glShaderSource(id,source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) {
            String error=GL20.glGetShaderInfoLog(id);
            GL20.glDeleteShader(id);
            throw new IllegalStateException(error);
        }
        return id;
    }

    private static void destroy() {
        RenderSystem.assertOnRenderThread();
        VfxBakedModelMeshCache.clear();
        if (vertexVbo != 0) GL15.glDeleteBuffers(vertexVbo);
        if (instanceVbo != 0) GL15.glDeleteBuffers(instanceVbo);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (analyticVao != 0) GL30.glDeleteVertexArrays(analyticVao);
        if (program != 0) GL20.glDeleteProgram(program);
        vertexVbo=instanceVbo=vao=analyticVao=program=0;
    }
}
