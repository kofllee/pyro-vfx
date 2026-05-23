package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxBlendMode;
import net.minecraft.resources.ResourceLocation;

public record VfxSpriteRenderDefinition (ResourceLocation texture, VfxSpriteUvDefinition uv, VfxBlendMode blendMode) {
}
