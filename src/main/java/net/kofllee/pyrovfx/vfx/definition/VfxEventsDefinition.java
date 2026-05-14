package net.kofllee.pyrovfx.vfx.definition;

public record VfxEventsDefinition() {
    private static final VfxEventsDefinition EMPTY = new VfxEventsDefinition();

    public static VfxEventsDefinition empty() {
        return EMPTY;
    }
}
