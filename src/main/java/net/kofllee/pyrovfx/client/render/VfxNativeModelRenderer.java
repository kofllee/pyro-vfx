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
 * GPU-resident baked model geometry expanded via Transform Feedback and
 * submitted through Minecraft's native particle ShaderInstance.
 * Entity animation, dynamic tint and sorted translucent models use RenderType.
 */
public final class VfxNativeModelRenderer {
    /** GPU transform-feeback vertex expansion. Minecraft owns the final fragment shader. */
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
            uniform int uAnalytic, uLocalSpace, uUseLight;
            uniform vec3 uCamera, uAcceleration, uSharedScale, uParametricOffset;
            uniform vec3 uSourcePosition;
            uniform vec4 uSourceRotation, uSharedColor, uSharedRotation;
            uniform vec2 uMotion;
            out vec3 outPosition;
            out vec2 outUv;
            flat out uint outColor;
            flat out uint outLight;
            vec3 rotateSource(vec3 v) {
                vec3 t = 2.0 * cross(uSourceRotation.xyz, v);
                return v + uSourceRotation.w * t + cross(uSourceRotation.xyz, t);
            }
            void main() {
                vec3 scale = uAnalytic != 0 ? uSharedScale : iScale;
                vec4 rot = uAnalytic != 0 ? uSharedRotation : iRotation;
                vec3 origin = iPosition;
                if (uAnalytic != 0)
                    origin = iPosition - uCamera + iBirthVelocity * uMotion.x
                            + uAcceleration * uMotion.y + uParametricOffset;
                vec3 p = (aPosition - vec3(.5)) * scale;
                vec3 t = 2.0 * cross(rot.xyz, p);
                vec3 point = origin + p + rot.w * t + cross(rot.xyz, t);
                if (uAnalytic != 0 && uLocalSpace != 0)
                    point = uSourcePosition + rotateSource(point + uCamera) - uCamera;
                outPosition = point;
                outUv = aUv;
                vec4 color = (uAnalytic != 0 ? uSharedColor : iColor) * aBakedColor;
                uvec4 rgba = uvec4(clamp(color,0.,1.)*255. + .5);
                outColor = rgba.r | (rgba.g<<8u) | (rgba.b<<16u) | (rgba.a<<24u);
                if (uUseLight != 0) {
                    uvec2 light = uvec2(clamp(floor(iLight * 16.),vec2(0.),vec2(15.)));
                    outLight = light.x*16u | ((light.y*16u)<<16u);
                } else outLight = 240u | (240u<<16u);
                gl_Position = vec4(0.,0.,0.,1.);
            }
            """;
    private static final String TRANSFORM_FEEDBACK_FRAGMENT = """
            #version 330 core
            out vec4 color;
            void main() { color = vec4(1.); }
            """;
    private static final int INSTANCE_FLOATS = 16, STRIDE = INSTANCE_FLOATS * Float.BYTES;
    private static final int VERTEX_FLOATS = 9;
    private static final int MAX_BATCH = 16384;
    private static int batchCapacity(Mesh mesh) {
        return Math.min(MAX_BATCH, Math.max(1, MAX_OUTPUT_BYTES /
                Math.max(1, mesh.vertexCount() * NATIVE_VERTEX_STRIDE)));
    }

    private static ByteBuffer instances;
    private static int program, instanceVbo, vao, analyticVao, vertexVbo;
    private static int nativeVao, nativeVbo, nativeCapacityVertices;
    private static final int NATIVE_VERTEX_STRIDE = 28, MAX_OUTPUT_BYTES = 16 * 1024 * 1024;
    private static int capacity;
    private static boolean unavailable;
    private static volatile boolean reloadPending;
    private static Object lastLevel;
    private static int lastDrawCalls;
    private static int lastInstances;
    private static int residentUploads, residentUploadBytes;
    private static final Quaternionf ROTATION = new Quaternionf();


    private VfxNativeModelRenderer() {}

    public static boolean accepts(VfxEmitterDefinition definition) {
        if (Boolean.getBoolean("pyrovfx.model.forceLegacy")
                || !VfxNativeSpriteBatcher.compatibleEnvironment()
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

    /** Render-thread lifecycle: handle shader reload, world replacement and logout. */
    public static void maintain() {
        RenderSystem.assertOnRenderThread();
        var world = Minecraft.getInstance().level;
        if (reloadPending || lastLevel != world) {
            destroy();
            reloadPending = false;
            unavailable = false;
            lastLevel = world;
        }
    }

    public static int lastDrawCalls() { return lastDrawCalls; }
    public static int lastInstances() { return lastInstances; }
    public static int residentUploads() { return residentUploads; }
    public static int residentUploadBytes() { return residentUploadBytes; }
    public static void resetFrameStats() { lastDrawCalls = lastInstances = residentUploads = residentUploadBytes = 0; }

    /** One birth-state VBO per cohort; model transforms are advanced by the vertex shader. */
    public static boolean renderAnalytic(VfxAnalyticParticlePool pool, PoseStack poses,
                                         Camera camera, float partialTick) {
        // Per-particle lifetime masks, birth expressions and world-light
        // probes need their own model attributes. Until supported in this
        // shader they must use the correct vanilla CPU model path.
        if (!pool.model() || pool.liveCount() <= 0 || !accepts(pool.definition())
                || pool.gpuBirthAppearance() || pool.individualLifetimes()
                || pool.definition().render().environmentLighting()) return false;
        RenderSystem.assertOnRenderThread();
        maintain();
        var resource = pool.definition().render().model();
        Mesh mesh = VfxBakedModelMeshCache.get(resource);
        if (mesh == null) return false;
        boolean submitted = false;
        try {
            if (program == 0) initialize();
            if (pool.gpuBuffer() == 0 || pool.gpuGeneration() != VfxGpuBirthBuffers.generation()
                    || pool.dirty()) {
                if (pool.gpuBuffer() != 0) VfxGpuBirthBuffers.retireBuffer(pool.gpuBuffer());
                int oldArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                int buffer = GL15.glGenBuffers();
                boolean uploaded = false;
                try {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
                    ByteBuffer data = pool.data();
                    residentUploadBytes += data.remaining();
                    GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
                    residentUploads++;
                    pool.setGpuBuffer(buffer, VfxGpuBirthBuffers.generation());
                    uploaded = true;
                } finally {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArray);
                    if (!uploaded) GL15.glDeleteBuffers(buffer);
                }
            }
            pool.updateAppearance(partialTick);
            Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                    .mul(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
            int chunk = batchCapacity(mesh);
            for (int start = 0; start < pool.liveCount(); start += chunk) {
                int count = Math.min(chunk, pool.liveCount() - start);
                drawResident(mesh, mvp, count, resource.effectiveLayer(),
                        pool, start, camera, partialTick);
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
        maintain();
        VfxModelRenderDefinition resource = pool.definition().render().model();
        Mesh mesh = VfxBakedModelMeshCache.get(resource);
        if (mesh == null) return false;
        final boolean[] drawAttempted = {false};
        try {
            if (program == 0) initialize();
            Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                    .mul(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
            Vec3 cameraPos = camera.getPosition();
            int chunk = batchCapacity(mesh);
            ensureCapacity(chunk);
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
                if (++batched[0] == chunk) {
                    instances.flip();
                    drawAttempted[0] = true;
                    draw(readyMesh, mvp, batched[0], resource.effectiveLayer(),
                            pool.definition().render().environmentLighting());
                    lastDrawCalls++;
                    lastInstances += batched[0];
                    batched[0] = 0;
                    instances.clear();
                }
            }, partialTick);
            if (batched[0] > 0) {
                instances.flip();
                drawAttempted[0] = true;
                draw(mesh, mvp, batched[0], resource.effectiveLayer(),
                        pool.definition().render().environmentLighting());
                lastDrawCalls++;
                lastInstances += batched[0];
            }
            return true;
        } catch (RuntimeException ex) {
            unavailable = true;
            com.mojang.logging.LogUtils.getLogger().error(
                    "Dense model GPU instancing failed; switching to legacy renderer", ex);
            // A pre-draw failure must fall back immediately. If a draw was
            // attempted, it might have submitted pixels before the error.
            return drawAttempted[0];
        }
    }

    public static boolean render(List<ClientVfxParticle> particles, PoseStack poses,
                                 Camera camera, float partialTick) {
        if (particles.isEmpty() || !accepts(particles.getFirst().emitterDefinition())) return false;
        RenderSystem.assertOnRenderThread();
        maintain();
        VfxEmitterDefinition definition = particles.getFirst().emitterDefinition();
        VfxModelRenderDefinition resource = definition.render().model();
        Mesh mesh = VfxBakedModelMeshCache.get(resource);
        if (mesh == null) return false;
        // No partial rendering: if any instance needs a semantics-rich path,
        // the entire emitter is left to the Minecraft renderer.
        for (ClientVfxParticle particle : particles) {
            if (particle.emitterDefinition() != definition) return false;
            if (resource.effectiveLayer() == VfxModelRenderLayer.SOLID
                    && Math.abs(particle.interpolatedColor(partialTick).a() - 1.0) > 1.0e-6) return false;
        }
        boolean drawAttempted = false;
        try {
            if (program == 0) initialize();
            Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                    .mul(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
            Vec3 cameraPos = camera.getPosition();
            int start = 0;
            while (start < particles.size()) {
                int batchCount = Math.min(batchCapacity(mesh), particles.size() - start);
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
                    drawAttempted = true;
                    draw(mesh, mvp, drawn, resource.effectiveLayer(),
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
            return drawAttempted; // pre-draw errors can safely fall back
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
        // Only geometry generation belongs to Pyro. The final fragment pass
        // is always Minecraft's ShaderInstance, including with shaderpacks.
        var state = new VfxNativeSpriteBatcher.GlState();
        state.capture();
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevTf = GL11.glGetInteger(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING);
        int prevIndexedTf = GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING, 0);
        boolean prevDiscard = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        int prevDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevShaderLight = RenderSystem.getShaderTexture(2);
        int prevTexture = RenderSystem.getShaderTexture(0);
        var prevShader = RenderSystem.getShader();
        try {
            int vertices = Math.multiplyExact(mesh.vertexCount(), count);
            if ((long) vertices * NATIVE_VERTEX_STRIDE > MAX_OUTPUT_BYTES)
                throw new IllegalArgumentException("Native model batch exceeds geometry budget");
            if (vertices > nativeCapacityVertices) {
                GL30.glBindVertexArray(nativeVao);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, nativeVbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER,
                        (long) vertices * NATIVE_VERTEX_STRIDE, GL15.GL_STREAM_DRAW);
                nativeCapacityVertices = vertices;
            }
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(resident == null ? vao : analyticVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.geometryVbo());
            attribute(0, 3, VERTEX_FLOATS * Float.BYTES, 0, 0);
            attribute(1, 2, VERTEX_FLOATS * Float.BYTES, 3L * Float.BYTES, 0);
            attribute(7, 4, VERTEX_FLOATS * Float.BYTES, 5L * Float.BYTES, 0);
            if (resident == null) {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STREAM_DRAW);
            } else {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, resident.gpuBuffer());
                long base = (long) start * resident.stride();
                attribute(2, 3, resident.stride(), base, 1);
                attribute(8, 3, resident.stride(), base + 12L, 1);
            }
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"uAnalytic"), resident == null?0:1);
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"uUseLight"), worldLit?1:0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program,"uLocalSpace"),
                    resident != null && resident.localSpace()?1:0);
            if (resident != null) {
                var source = resident.sourceTransform();
                var rotation = source.rotation();
                GL20.glUniform3f(GL20.glGetUniformLocation(program,"uSourcePosition"),
                        (float) source.position().x,(float) source.position().y,(float) source.position().z);
                GL20.glUniform4f(GL20.glGetUniformLocation(program,"uSourceRotation"),
                        rotation.x,rotation.y,rotation.z,rotation.w);
                var coefficients = resident.plan().coefficients(resident.age(),partialTick);
                GL20.glUniform2f(GL20.glGetUniformLocation(program,"uMotion"),
                        (float) coefficients[0],(float) coefficients[1]);
                GL20.glUniform3f(GL20.glGetUniformLocation(program,"uCamera"),
                        (float)camera.getPosition().x,(float)camera.getPosition().y,
                        (float)camera.getPosition().z);
                var acceleration=resident.effectiveAcceleration();
                GL20.glUniform3f(GL20.glGetUniformLocation(program,"uAcceleration"),
                        (float)acceleration.x,(float)acceleration.y,(float)acceleration.z);
                var parametric=resident.parametricOffset(partialTick);
                GL20.glUniform3f(GL20.glGetUniformLocation(program,"uParametricOffset"),
                        (float)parametric.x,(float)parametric.y,(float)parametric.z);
                GL20.glUniform3f(GL20.glGetUniformLocation(program,"uSharedScale"),
                        (float)resident.scale().x,(float)resident.scale().y,(float)resident.scale().z);
                GL20.glUniform4f(GL20.glGetUniformLocation(program,"uSharedColor"),
                        (float)resident.color().r(),(float)resident.color().g(),
                        (float)resident.color().b(),(float)resident.color().a());
                var angles=resident.modelRotation(partialTick);
                ROTATION.rotationXYZ((float)Math.toRadians(angles.x),
                        (float)Math.toRadians(angles.y),(float)Math.toRadians(angles.z));
                GL20.glUniform4f(GL20.glGetUniformLocation(program,"uSharedRotation"),
                        ROTATION.x,ROTATION.y,ROTATION.z,ROTATION.w);
            }
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,nativeVbo);
            GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            boolean began=false;
            try {
                GL30.glBeginTransformFeedback(GL11.GL_POINTS);
                began=true;
                GL31.glDrawArraysInstanced(GL11.GL_POINTS,0,mesh.vertexCount(),count);
            } finally {
                if (began) GL30.glEndTransformFeedback();
                GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            }

            var mc = Minecraft.getInstance();
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0,InventoryMenu.BLOCK_ATLAS);
            var shader = RenderSystem.getShader();
            shader.setDefaultUniforms(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES,
                    RenderSystem.getModelViewMatrix(),RenderSystem.getProjectionMatrix(),
                    mc.getWindow());
            if (layer == VfxModelRenderLayer.ADDITIVE) {
                GL11.glEnable(GL11.GL_BLEND);
                org.lwjgl.opengl.GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE,
                        GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            } else GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(layer != VfxModelRenderLayer.ADDITIVE);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL30.glBindVertexArray(nativeVao);
            shader.apply();
            try {
                GL11.glDrawArrays(GL11.GL_TRIANGLES,0,vertices);
            } finally {
                shader.clear();
            }
        } finally {
            if(prevDiscard) GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            else GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,0,prevIndexedTf);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER,prevTf);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,prevDrawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,prevReadFbo);
            GL20.glUseProgram(prevProgram);
            GL30.glBindVertexArray(prevVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,prevArray);
            if(prevShader != null) RenderSystem.setShader(()->prevShader);
            RenderSystem.setShaderTexture(0,prevTexture);
            RenderSystem.setShaderTexture(2,prevShaderLight);
            state.restore();
            if(Boolean.getBoolean("pyrovfx.render.stateAudit")){
                state.assertRestored();
                if(GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING,0)!=prevIndexedTf
                        ||GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD)!=prevDiscard
                        ||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=prevDrawFbo
                        ||GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)!=prevReadFbo)
                    throw new IllegalStateException("Native model TF state leak");
            }
        }
    }

    private static void initialize() {
        int vs = shader(GL20.GL_VERTEX_SHADER, VERTEX);
        int fs;
        try {
            fs = shader(GL20.GL_FRAGMENT_SHADER, TRANSFORM_FEEDBACK_FRAGMENT);
        } catch (RuntimeException | Error failure) {
            GL20.glDeleteShader(vs);
            throw failure;
        }
        int newProgram = 0;
        try {
            newProgram = GL20.glCreateProgram();
            GL20.glAttachShader(newProgram, vs);
            GL20.glAttachShader(newProgram, fs);
            GL30.glTransformFeedbackVaryings(newProgram,
                    new String[]{"outPosition", "outUv", "outColor", "outLight"},
                    GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(newProgram);
            if (GL20.glGetProgrami(newProgram, GL20.GL_LINK_STATUS) != GL11.GL_TRUE)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(newProgram));
        } catch (RuntimeException | Error failure) {
            if (newProgram != 0) GL20.glDeleteProgram(newProgram);
            throw failure;
        } finally {
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
        }
        program = newProgram;
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        try {
            vao = GL30.glGenVertexArrays();
            analyticVao = GL30.glGenVertexArrays();
            nativeVao = GL30.glGenVertexArrays();
            nativeVbo = GL15.glGenBuffers();
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
            GL30.glBindVertexArray(nativeVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,nativeVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,1L,GL15.GL_STREAM_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glEnableVertexAttribArray(2);
            GL20.glEnableVertexAttribArray(3);
            GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,NATIVE_VERTEX_STRIDE,0L);
            GL20.glVertexAttribPointer(1,2,GL11.GL_FLOAT,false,NATIVE_VERTEX_STRIDE,12L);
            GL20.glVertexAttribPointer(2,4,GL11.GL_UNSIGNED_BYTE,true,NATIVE_VERTEX_STRIDE,20L);
            GL30.glVertexAttribIPointer(3,2,GL11.GL_UNSIGNED_SHORT,NATIVE_VERTEX_STRIDE,24L);
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
        if (nativeVbo != 0) GL15.glDeleteBuffers(nativeVbo);
        if (instanceVbo != 0) GL15.glDeleteBuffers(instanceVbo);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (analyticVao != 0) GL30.glDeleteVertexArrays(analyticVao);
        if (nativeVao != 0) GL30.glDeleteVertexArrays(nativeVao);
        if (program != 0) GL20.glDeleteProgram(program);
        vertexVbo=instanceVbo=vao=analyticVao=nativeVao=nativeVbo=program=0;
        nativeCapacityVertices=0;
    }
}
