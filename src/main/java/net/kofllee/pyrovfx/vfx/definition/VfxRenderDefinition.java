package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxFacingMode;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;

public record VfxRenderDefinition (
        VfxRenderType type,
        VfxFacingMode facing,
        boolean environmentLighting,
        VfxSpriteRenderDefinition sprite,
        VfxModelRenderDefinition model,
        VfxParticleAppearanceDefinition appearance
) {
    public static VfxRenderDefinition sprite(
            VfxFacingMode facing,
            boolean environmentLighting,
            VfxSpriteRenderDefinition sprite,
            VfxParticleAppearanceDefinition appearance
    ) {
        return new VfxRenderDefinition(
                VfxRenderType.SPRITE,
                facing,
                environmentLighting,
                sprite,
                null,
                appearance
        );
    }

    public static VfxRenderDefinition model(
            boolean environmentLighting,
            VfxModelRenderDefinition model,
            VfxParticleAppearanceDefinition appearance
    ) {
        return new VfxRenderDefinition(
                VfxRenderType.MODEL,
                VfxFacingMode.WORLD_Z,
                environmentLighting,
                null,
                model,
                appearance
        );
    }
}
