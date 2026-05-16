package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxBlendMode;

public record VfxMaterialDefinition(VfxBlendMode blendMode) {
    public static VfxMaterialDefinition defaultMaterial() {
        return new VfxMaterialDefinition(VfxBlendMode.ALPHA);
    }
}
