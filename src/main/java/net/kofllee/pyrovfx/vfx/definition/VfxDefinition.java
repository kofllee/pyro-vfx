package net.kofllee.pyrovfx.vfx.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record VfxDefinition(ResourceLocation id, int lifeTimeTicks, List<VfxEmitterDefinition> emitters) {
}
