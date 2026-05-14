package net.kofllee.pyrovfx.vfx.definition;

public record  VfxMetadataDefinition (String author, String description) {
    public static VfxMetadataDefinition empty() {
        return new VfxMetadataDefinition("", "");
    }
}
