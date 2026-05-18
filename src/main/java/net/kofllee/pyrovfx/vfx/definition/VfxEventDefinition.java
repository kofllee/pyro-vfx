package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.type.VfxEventType;

import java.util.List;

public record VfxEventDefinition(VfxEventType type, String emitterId, List<String> eventIds, String parameterId, VfxNumberExpression value) {
    public static VfxEventDefinition emit(String emitterId) {
        return new VfxEventDefinition(
                VfxEventType.EMIT,
                emitterId,
                List.of(),
                "",
                VfxNumberExpression.constant(0.0)
        );
    }

    public static VfxEventDefinition sequence(List<String> eventIds) {
        return new VfxEventDefinition(
                VfxEventType.SEQUENCE,
                "",
                List.copyOf(eventIds),
                "",
                VfxNumberExpression.constant(0.0)
        );
    }

    public static VfxEventDefinition randomize(List<String> eventIds) {
        return new VfxEventDefinition(
                VfxEventType.RANDOMIZE,
                "",
                List.copyOf(eventIds),
                "",
                VfxNumberExpression.constant(0.0)
        );
    }

    public static VfxEventDefinition setParam(String parameterId, VfxNumberExpression value) {
        return new VfxEventDefinition(
                VfxEventType.SET_PARAM,
                "",
                List.of(),
                parameterId,
                value
        );
    }
}
