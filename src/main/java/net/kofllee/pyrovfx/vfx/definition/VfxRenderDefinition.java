package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxFacingMode;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;

public record VfxRenderDefinition (
        VfxRenderType type,
        VfxFacingMode facing,
        VfxMaterialDefinition material,
        boolean environmentLighting,
        VfxMinecraftParticleRenderDefinition minecraftParticle,
        VfxSpriteRenderDefinition sprite,
        VfxParticleAppearanceDefinition appearance
) {
    public static VfxRenderDefinition minecraftParticle(VfxMinecraftParticleRenderDefinition minecraftParticle) {
        return new VfxRenderDefinition(
                VfxRenderType.MINECRAFT_PARTICLE,
                VfxFacingMode.CAMERA,
                VfxMaterialDefinition.defaultMaterial(),
                true,
                minecraftParticle,
                null,
                VfxParticleAppearanceDefinition.defaultAppearance()
        );
    }

    public static VfxRenderDefinition sprite(
            VfxFacingMode facing,
            VfxMaterialDefinition material,
            boolean environmentLighting,
            VfxSpriteRenderDefinition sprite,
            VfxParticleAppearanceDefinition appearance
    ) {
        return new VfxRenderDefinition(
                VfxRenderType.SPRITE,
                facing,
                material,
                environmentLighting,
                null,
                sprite,
                appearance
        );
    }
}
