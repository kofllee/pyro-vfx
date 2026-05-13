package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxParticleRenderType;
import net.minecraft.resources.ResourceLocation;

public record VfxAppearanceDefinition(VfxParticleRenderType renderType, ResourceLocation minecraftParticleId) {
}
