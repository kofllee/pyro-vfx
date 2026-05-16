package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxEventType;

public record VfxTimelineEventDefinition (int timeTicks, VfxEventType type, String emitterId) {
}
