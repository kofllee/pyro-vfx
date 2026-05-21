package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer;
import net.minecraft.resources.ResourceLocation;

public record VfxModelRenderDefinition (ResourceLocation model, VfxModelRenderLayer renderLayer) {
}
