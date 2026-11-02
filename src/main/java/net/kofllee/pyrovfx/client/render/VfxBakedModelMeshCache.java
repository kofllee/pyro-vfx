package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.vfx.definition.VfxModelRenderDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxModelSourceType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Owns deterministic Minecraft baked model geometry and its GL lifetime. */
final class VfxBakedModelMeshCache {
    private static final int VERTEX_FLOATS = 9;
    private static final Map<VfxModelRenderDefinition, Mesh> MESHES = new HashMap<>();
    private static final Mesh UNSUPPORTED = new Mesh(0, 0);
    record Mesh(int geometryVbo, int vertexCount) {}
    private VfxBakedModelMeshCache() {}
    static Mesh get(VfxModelRenderDefinition definition) {
        Mesh found = MESHES.get(definition);
        if (found == null) {
            found = bake(definition);
            MESHES.put(definition, found == null ? UNSUPPORTED : found);
        }
        return found == UNSUPPORTED ? null : found;
    }
    static void clear() {
        for (Mesh mesh : MESHES.values()) {
            if (mesh.geometryVbo() != 0) GL15.glDeleteBuffers(mesh.geometryVbo());
        }
        MESHES.clear();
    }
    /** Snapshot only un-tinted, stable quads. Other/custom multipart models fall back. */
    private static Mesh bake(VfxModelRenderDefinition resource) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState blockState = null;
        ItemStack itemStack = ItemStack.EMPTY;
        BakedModel model;
        switch (resource.source()) {
            case CUSTOM -> model = minecraft.getModelManager().getModel(
                    ModelResourceLocation.standalone(resource.model()));
            case ITEM -> {
                itemStack = new ItemStack(BuiltInRegistries.ITEM.get(resource.model()));
                model = minecraft.getItemRenderer().getModel(itemStack, minecraft.level, null, 0);
            }
            case BLOCK -> {
                blockState = BlockModelVfxParticleRenderer.resolveBlockState(resource);
                model = minecraft.getBlockRenderer().getBlockModel(blockState);
            }
            default -> { return null; }
        }
        if (model == null) return null;
        // Item models are resolved by the item renderer against Minecraft's
        // baked item layer. Passing the custom entity RenderType into
        // getQuads can produce an empty mesh even when the item is drawable.
        RenderType renderType = resource.source() == VfxModelSourceType.ITEM
                ? null : VfxModelRenderTypes.get(resource.renderLayer());
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            quads.addAll(model.getQuads(blockState, direction, random, ModelData.EMPTY, renderType));
        }
        random.setSeed(42L);
        quads.addAll(model.getQuads(blockState, null, random, ModelData.EMPTY, renderType));
        if (quads.isEmpty()) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Model instancing fallback: {} has no deterministic baked quads", resource);
            return null;
        }
        // Each quad has 4 vertices. Preserve the original face vertex winding.
        FloatBuffer meshData = ByteBuffer.allocateDirect(quads.size() * 6 * VERTEX_FLOATS * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (BakedQuad quad : quads) {
            if (quad.isTinted() && resource.source() != VfxModelSourceType.ITEM) {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "Model instancing fallback: {} needs world-dependent quad tint", resource);
                return null;
            }
            int tint = quad.isTinted()
                    ? minecraft.getItemColors().getColor(itemStack, quad.getTintIndex()) : -1;
            int[] data = quad.getVertices();
            if (data.length < 4 * 6 || data.length % 4 != 0) {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "Model instancing fallback: {} has unexpected vertex format", resource);
                return null;
            }
            int stride = data.length / 4;
            for (int index : new int[]{0,1,2,0,2,3}) {
                int base = index * stride;
                int packed = data[base+3]; // baked color is ABGR
                meshData.put(Float.intBitsToFloat(data[base]));
                meshData.put(Float.intBitsToFloat(data[base+1]));
                meshData.put(Float.intBitsToFloat(data[base+2]));
                meshData.put(Float.intBitsToFloat(data[base+4]));
                meshData.put(Float.intBitsToFloat(data[base+5]));
                meshData.put((packed & 255) / 255f * ((tint >> 16) & 255) / 255f);
                meshData.put(((packed >> 8) & 255) / 255f * ((tint >> 8) & 255) / 255f);
                meshData.put(((packed >> 16) & 255) / 255f * (tint & 255) / 255f);
                meshData.put(((packed >>> 24) & 255) / 255f);
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

}
