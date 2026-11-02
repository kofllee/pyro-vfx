package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer;
import net.kofllee.pyrovfx.vfx.type.VfxModelSourceType;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.kofllee.pyrovfx.vfx.type.VfxRotationMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scoped GPU instancing for baked, unlit, untinted CUSTOM/SOLID models.
 * The source quads are baked once per resource reload and never re-uploaded
 * per particle. Rotating/tinted/lit/translucent/other models use RenderType.
 */
public final class VfxInstancedModelRenderer {
    private static final String VERTEX = """
            #version 330 core
            layout(location=0) in vec3 aPosition;
            layout(location=1) in vec2 aUv;
            layout(location=2) in vec3 iPosition;
            layout(location=3) in vec3 iScale;
            layout(location=4) in vec4 iColor;
            uniform mat4 uMvp;
            out vec2 vUv;
            out vec4 vColor;
            void main() {
                vec3 point = iPosition + (aPosition - vec3(0.5)) * iScale;
                gl_Position = uMvp * vec4(point, 1.0);
                vUv = aUv;
                vColor = iColor;
            }
            """;
    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec4 vColor;
            uniform sampler2D uTexture;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTexture, vUv) * vColor;
            }
            """;
    private static final int INSTANCE_FLOATS = 10, STRIDE = INSTANCE_FLOATS * Float.BYTES;
    private static final int VERTEX_FLOATS = 5;
    private static final int MAX_BATCH = 16384;

    private static final Map<ResourceLocation, Mesh> meshes = new HashMap<>();
    private static final FloatBuffer MATRIX_BUFFER = ByteBuffer.allocateDirect(16 * Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static ByteBuffer instances;
    private static int program, instanceVbo, vao, vertexVbo;
    private static int capacity, mvpUniform;
    private static boolean unavailable;
    private static volatile boolean reloadPending;
    private static int lastDrawCalls;
    private static int lastInstances;
    private static final Mesh UNSUPPORTED = new Mesh(0, 0);

    /** Geometry is uploaded to one immutable GL VBO when first baked. */
    private record Mesh(int geometryVbo, int vertexCount) {}

    private VfxInstancedModelRenderer() {}

    public static boolean accepts(VfxEmitterDefinition definition) {
        if (!VfxInstancedSpriteRenderer.compatibleEnvironment()
                || unavailable || !GL.getCapabilities().OpenGL33) return false;
        if (definition.render().type() != VfxRenderType.MODEL
                || definition.render().model() == null
                || definition.render().environmentLighting()
                || definition.rotation().mode() != VfxRotationMode.NONE) return false;
        var model = definition.render().model();
        return model.source() == VfxModelSourceType.CUSTOM
                && model.renderLayer() == VfxModelRenderLayer.SOLID;
    }

    public static void invalidateAfterReload() {
        reloadPending = true;
        unavailable = false;
    }

    public static int lastDrawCalls() { return lastDrawCalls; }
    public static int lastInstances() { return lastInstances; }
    public static void resetFrameStats() { lastDrawCalls = lastInstances = 0; }

    public static boolean render(List<ClientVfxParticle> particles, PoseStack poses,
                                 Camera camera, float partialTick) {
        if (particles.isEmpty() || !accepts(particles.getFirst().emitterDefinition())) return false;
        RenderSystem.assertOnRenderThread();
        if (reloadPending) {
            destroy();
            reloadPending = false;
        }
        VfxEmitterDefinition definition = particles.getFirst().emitterDefinition();
        ResourceLocation resource = definition.render().model().model();
        Mesh mesh = meshes.get(resource);
        if (mesh == null) {
            mesh = bake(resource);
            meshes.put(resource, mesh == null ? UNSUPPORTED : mesh);
        }
        if (mesh == null || mesh == UNSUPPORTED) return false;
        // No partial rendering: if any instance needs a semantics-rich path,
        // the entire emitter is left to the Minecraft renderer.
        for (ClientVfxParticle particle : particles) {
            if (particle.emitterDefinition() != definition) return false;
            if (Math.abs(particle.interpolatedColor(partialTick).a() - 1.0) > 1.0e-6) return false;
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
                    drawn++;
                }
                if (drawn != 0) {
                    instances.flip();
                    draw(mesh, mvp, drawn);
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

    /** Snapshot only un-tinted, stable quads. Other/custom multipart models fall back. */
    private static Mesh bake(ResourceLocation resource) {
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(ModelResourceLocation.standalone(resource));
        if (model == null) return null;
        RenderType renderType = VfxModelRenderTypes.get(VfxModelRenderLayer.SOLID);
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            quads.addAll(model.getQuads(null, direction, random, ModelData.EMPTY, renderType));
        }
        quads.addAll(model.getQuads(null, null, random, ModelData.EMPTY, renderType));
        if (quads.isEmpty()) return null;
        // Each quad has 4 vertices. Preserve the original face vertex winding.
        FloatBuffer meshData = ByteBuffer.allocateDirect(quads.size() * 6 * VERTEX_FLOATS * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (BakedQuad quad : quads) {
            if (quad.isTinted()) return null;
            int[] data = quad.getVertices();
            if (data.length < 4 * 6 || data.length % 4 != 0) return null;
            int stride = data.length / 4;
            for (int index : new int[]{0,1,2,0,2,3}) {
                int base = index * stride;
                if (data[base+3] != -1) return null; // only uncolored faces
                meshData.put(Float.intBitsToFloat(data[base]));
                meshData.put(Float.intBitsToFloat(data[base+1]));
                meshData.put(Float.intBitsToFloat(data[base+2]));
                meshData.put(Float.intBitsToFloat(data[base+4]));
                meshData.put(Float.intBitsToFloat(data[base+5]));
            }
        }
        meshData.flip();
        int oldArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int geometry = GL15.glGenBuffers();
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, geometry);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, meshData, GL15.GL_STATIC_DRAW);
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, oldArray);
        }
        return new Mesh(geometry, quads.size() * 6);
    }

    private static void ensureCapacity(int count) {
        if (capacity >= count && instances != null) return;
        capacity = Math.min(MAX_BATCH, Math.max(128, Math.max(count, capacity * 2)));
        instances = ByteBuffer.allocateDirect(capacity * STRIDE).order(ByteOrder.nativeOrder());
    }

    private static void draw(Mesh mesh, Matrix4f mvp, int count) {
        VfxInstancedSpriteRenderer.GlState state = new VfxInstancedSpriteRenderer.GlState();
        state.capture();
        try {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, mesh.geometryVbo());
            attribute(0,3,VERTEX_FLOATS*Float.BYTES,0,0);
            attribute(1,2,VERTEX_FLOATS*Float.BYTES,3L*Float.BYTES,0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instances, GL15.GL_STREAM_DRAW);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, Minecraft.getInstance().getTextureManager()
                    .getTexture(InventoryMenu.BLOCK_ATLAS).getId());
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "uTexture"), 0);
            MATRIX_BUFFER.clear();
            mvp.get(MATRIX_BUFFER);
            MATRIX_BUFFER.position(0);
            MATRIX_BUFFER.limit(16);
            GL20.glUniformMatrix4fv(mvpUniform, false, MATRIX_BUFFER);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, mesh.vertexCount(), count);
        } finally {
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
        for (Mesh mesh : meshes.values()) {
            if (mesh.geometryVbo() != 0) GL15.glDeleteBuffers(mesh.geometryVbo());
        }
        if (vertexVbo != 0) GL15.glDeleteBuffers(vertexVbo);
        if (instanceVbo != 0) GL15.glDeleteBuffers(instanceVbo);
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (program != 0) GL20.glDeleteProgram(program);
        vertexVbo=instanceVbo=vao=program=0;
        meshes.clear();
    }
}
