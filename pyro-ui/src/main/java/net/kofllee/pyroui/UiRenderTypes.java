package net.kofllee.pyroui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.1-compatible texture passes for GUI alpha images.
 *
 * Vanilla RenderType.text(texture) uses nearest texture filtering. Fonts and
 * high-density vector-derived icon PNGs need GL_LINEAR for fractional scaling.
 * Keep pixel-art intentionally nearest, and do not change global GL texture state.
 */
public final class UiRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> LINEAR_TEXTURE = Util.memoize(
            texture -> create("pyro_ui_linear_texture",
                    DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                    VertexFormat.Mode.QUADS, 1536, false, false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_TEXT_SHADER)
                            .setTextureState(new TextureStateShard(texture, true, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLightmapState(LIGHTMAP)
                            .createCompositeState(false)));

    private UiRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                          boolean affectsCrumbling, boolean sortOnUpload,
                          Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    public static RenderType smoothTexture(ResourceLocation texture) { return LINEAR_TEXTURE.apply(texture); }
    public static RenderType pixelTexture(ResourceLocation texture) { return RenderType.text(texture); }
}
