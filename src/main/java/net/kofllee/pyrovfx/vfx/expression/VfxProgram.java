package net.kofllee.pyrovfx.vfx.expression;

import java.util.List;
import java.util.Set;
import java.util.Map;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxEventDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxParameterDefinition;
import java.util.ArrayList;

/** Frozen compile-on-JSON-load metadata; never owns mutable particle or effect state. */
public record VfxProgram(VfxVariableLayout variables, List<ExpressionInfo> expressions,
                         List<VfxEmitterDefinition> emitters,
                         Map<String, VfxEventDefinition> events,
                         List<ParameterBinding> parameterBindings) {
    public VfxProgram {
        expressions = List.copyOf(expressions);
        emitters = List.copyOf(emitters);
        events = Map.copyOf(events);
        parameterBindings = List.copyOf(parameterBindings);
    }

    public VfxProgram(VfxVariableLayout variables, List<ExpressionInfo> expressions) {
        this(variables, expressions, List.of(), Map.of(), List.of());
    }

    public static VfxProgram fromLegacyDefinition(List<VfxEmitterDefinition> emitters,
                                                  Map<String, VfxEventDefinition> events,
                                                  Map<String, VfxParameterDefinition> parameters) {
        VfxVariableLayout.Builder variables = new VfxVariableLayout.Builder();
        List<ParameterBinding> parametersBySlot = new ArrayList<>();
        for (String name : parameters.keySet()) {
            parametersBySlot.add(new ParameterBinding(name, variables.intern("param." + name)));
        }
        return new VfxProgram(variables.build(), List.of(), emitters, events, parametersBySlot);
    }

    public record ParameterBinding(String id, int slot) {}

    public enum Frequency {
        DEFINITION, EFFECT_START, EMITTER_START, PARTICLE_SPAWN,
        EFFECT_TICK, EMITTER_TICK, PARTICLE_TICK
    }

    public record ExpressionInfo(String source, Frequency frequency, boolean parameterDependent,
                                 boolean randomCall, boolean curveCall, Set<String> variables) {
        public ExpressionInfo {
            variables = Set.copyOf(variables);
        }
        /** Parameter writes are intentionally never treated as cache-safe constants. */
        public boolean constant() {
            return frequency == Frequency.DEFINITION && !parameterDependent
                    && !randomCall && !curveCall;
        }
    }
}
