package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer;
import net.kofllee.pyrovfx.vfx.type.VfxModelSourceType;
import net.minecraft.resources.ResourceLocation;

public record VfxModelRenderDefinition (VfxModelSourceType source, ResourceLocation model, String blockState, VfxModelRenderLayer renderLayer) {
}
