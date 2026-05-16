package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxEventType;

import java.util.List;

public record VfxEventDefinition(VfxEventType type, String emitterId, List<String> eventIds) {
    public static VfxEventDefinition emit(String emitterId) {
        return new VfxEventDefinition(
                VfxEventType.EMIT,
                emitterId,
                List.of()
        );
    }

    public static VfxEventDefinition sequence(List<String> eventIds) {
        return new VfxEventDefinition(
                VfxEventType.SEQUENCE,
                "",
                List.copyOf(eventIds)
        );
    }

    public static VfxEventDefinition randomize(List<String> eventIds) {
        return new VfxEventDefinition(
                VfxEventType.RANDOMIZE,
                "",
                List.copyOf(eventIds)
        );
    }
}
