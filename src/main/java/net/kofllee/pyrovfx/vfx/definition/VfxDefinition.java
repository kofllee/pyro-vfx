package net.kofllee.pyrovfx.vfx.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record VfxDefinition(
        ResourceLocation id,
        String format,
        VfxMetadataDefinition metadata,
        VfxLifetimeDefinition lifetime,
        List<VfxEmitterDefinition> emitters,
        Map<String, VfxEventDefinition> events,
        List<VfxTriggerDefinition> triggers
) {
}
