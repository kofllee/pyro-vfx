package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.curve.VfxCurveSet;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record VfxDefinition(
        ResourceLocation id,
        String format,
        VfxMetadataDefinition metadata,
        VfxLifetimeDefinition lifetime,
        Map<String, VfxParameterDefinition> parameters,
        VfxCurveSet curves,
        List<VfxEmitterDefinition> emitters,
        Map<String, VfxEventDefinition> events,
        List<VfxTriggerDefinition> triggers
) {
}
