package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.type.VfxBlendMode;
import net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared material routing contract across analytic, dense, legacy and model
 * backends. Stages are selected by actual material semantics, never by the
 * names or positions of blocks between the camera and an emitter.
 */
public record VfxRenderMaterial(Kind kind, ResourceLocation texture,
                                boolean environmentLighting) {
    public enum Kind { OPAQUE, CUTOUT, ALPHA, ADDITIVE }
    public enum Pass { BEFORE_TRANSLUCENT_WORLD, AFTER_PARTICLES }

    public static VfxRenderMaterial of(VfxEmitterDefinition definition) {
        var render = definition.render();
        if (render.type() == VfxRenderType.SPRITE) {
            return new VfxRenderMaterial(
                    render.sprite().blendMode() == VfxBlendMode.ADDITIVE
                            ? Kind.ADDITIVE : Kind.ALPHA,
                    render.sprite().texture(), render.environmentLighting());
        }
        var layer = render.model().renderLayer();
        Kind kind = switch (layer) {
            case SOLID -> Kind.OPAQUE;
            case CUTOUT -> Kind.CUTOUT;
            case TRANSLUCENT -> Kind.ALPHA;
            case ADDITIVE -> Kind.ADDITIVE;
        };
        return new VfxRenderMaterial(kind, null, render.environmentLighting());
    }

    public Pass pass() {
        // Native solid geometry writes depth before world transparency.
        // Additive light is also composited behind the translucent-world pass;
        // Alpha uses the native particle target, where Fabulous has copied
        // opaque world depth, before it performs the final transparency composite.
        return kind == Kind.ALPHA ? Pass.AFTER_PARTICLES : Pass.BEFORE_TRANSLUCENT_WORLD;
    }

    public boolean requiresSorting() { return kind == Kind.ALPHA; }
    public boolean writesDepth() { return kind == Kind.OPAQUE || kind == Kind.CUTOUT; }
}
