package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.curve.VfxCurveSet;
import net.kofllee.pyrovfx.vfx.expression.VfxProgram;
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
        List<VfxTriggerDefinition> triggers,
        VfxProgram program
) {
    public VfxDefinition {
        parameters = Map.copyOf(parameters);
        emitters = List.copyOf(emitters);
        events = Map.copyOf(events);
        triggers = List.copyOf(triggers);
    }

    /** Keep source-level compatibility for definitions assembled without the JSON compiler. */
    public VfxDefinition(ResourceLocation id, String format, VfxMetadataDefinition metadata,
                         VfxLifetimeDefinition lifetime, Map<String, VfxParameterDefinition> parameters,
                         VfxCurveSet curves, List<VfxEmitterDefinition> emitters,
                         Map<String, VfxEventDefinition> events, List<VfxTriggerDefinition> triggers) {
        this(id, format, metadata, lifetime, parameters, curves, emitters, events, triggers,
                VfxProgram.fromLegacyDefinition(emitters, events, parameters));
    }
}
