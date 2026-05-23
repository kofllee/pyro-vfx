package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.EnumMap;
import java.util.Map;

public final class VfxModelRenderTypes extends RenderStateShard {
    private static final Map<VfxModelRenderLayer, RenderType> CACHE = new EnumMap<>(VfxModelRenderLayer.class);

    private VfxModelRenderTypes() {
        super("pyro_vfx_model_render_types", () -> {}, () -> {});
    }

    public static RenderType get(VfxModelRenderLayer layer) {
        return CACHE.computeIfAbsent(layer, VfxModelRenderTypes::create);
    }

    private static RenderType create(VfxModelRenderLayer layer) {
        return switch (layer){
            case SOLID -> solid();
            case CUTOUT -> cutout();
            case TRANSLUCENT -> translucent();
            case ADDITIVE -> additive();
        };
    }

    private static RenderType solid() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .createCompositeState(true);

        return RenderType.create(
                "pyro_vfx_model_solid",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                true,
                state
        );
    }

    private static RenderType cutout() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .createCompositeState(true);

        return RenderType.create(
                "pyro_vfx_model_cutout",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                false,
                state
        );
    }

    private static RenderType translucent() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .createCompositeState(true);

        return RenderType.create(
                "pyro_vfx_model_translucent",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                true,
                state
        );
    }

    private static RenderType additive() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .createCompositeState(true);

        return RenderType.create(
                "pyro_vfx_model_additive",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                true,
                state
        );
    }
}
