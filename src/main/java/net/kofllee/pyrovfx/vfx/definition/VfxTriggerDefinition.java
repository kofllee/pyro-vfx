package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxTriggerType;

public record VfxTriggerDefinition (VfxTriggerType type, String eventId, int timeTicks, double distance) {
    public static VfxTriggerDefinition onCreation(String eventId) {
        return new VfxTriggerDefinition(
                VfxTriggerType.ON_CREATION,
                eventId,
                0,
                0.0
        );
    }

    public static VfxTriggerDefinition onExpiration(String eventId) {
        return new VfxTriggerDefinition(
                VfxTriggerType.ON_EXPIRATION,
                eventId,
                0,
                0.0
        );
    }

    public static VfxTriggerDefinition timeline(int timeTicks, String eventId) {
        return new VfxTriggerDefinition(
                VfxTriggerType.TIMELINE,
                eventId,
                timeTicks,
                0.0
        );
    }

    public static VfxTriggerDefinition travelDistance(double distance, String eventId, boolean looping) {
        return new VfxTriggerDefinition(
                looping ? VfxTriggerType.TRAVEL_DISTANCE_LOOPING : VfxTriggerType.TRAVEL_DISTANCE,
                eventId,
                0,
                distance
        );
    }

    public static VfxTriggerDefinition onCollision(String eventId) {
        return new VfxTriggerDefinition(
                VfxTriggerType.ON_COLLISION,
                eventId,
                0,
                0.0
        );
    }
}
