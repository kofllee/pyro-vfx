package net.kofllee.pyrovfx.vfx.definition;

public record  VfxMetadataDefinition (String author, String description,
                                      net.kofllee.pyrovfx.vfx.type.VfxQualityPriority priority) {
    public VfxMetadataDefinition(String author, String description) {
        this(author, description, net.kofllee.pyrovfx.vfx.type.VfxQualityPriority.NORMAL);
    }
    public static VfxMetadataDefinition empty() {
        return new VfxMetadataDefinition("", "");
    }
}
