package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.kofllee.pyrovfx.vfx.definition.VfxMaterialDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxBlendMode;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public final class VfxSpriteRenderTypes extends RenderStateShard {
    private static final Map<Key, RenderType> CACHE = new HashMap<>();

    private VfxSpriteRenderTypes() {
        super("pyro_vfx_sprite_render_types", () -> {}, () -> {});
    }

    public static RenderType get(ResourceLocation texture, VfxMaterialDefinition material) {
        Key key = new Key(texture, material.blendMode());

        return CACHE.computeIfAbsent(key, VfxSpriteRenderTypes::create);
    }

    private static RenderType create(Key key) {
        if (key.blendMode == VfxBlendMode.ADDITIVE) {
            return additive(key.texture);
        }

        return alpha(key.texture);
    }

    private static RenderType alpha(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .createCompositeState(true);

        return RenderType.create(
                "pyro_vfx_alpha_",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                true,
                state
        );
    }

    private static RenderType additive(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(true);

        return RenderType.create(
                "pyro_vfx_additive",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                true,
                true,
                state
        );
    }

    private record Key(
            ResourceLocation texture,
            VfxBlendMode blendMode
    ) {
    }
}