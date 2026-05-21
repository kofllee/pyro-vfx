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
        VfxModelRenderDefinition model,
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
                null,
                appearance
        );
    }

    public static VfxRenderDefinition model(
            VfxMaterialDefinition material,
            boolean environmentLighting,
            VfxModelRenderDefinition model,
            VfxParticleAppearanceDefinition appearance
    ) {
        return new VfxRenderDefinition(
                VfxRenderType.MODEL,
                VfxFacingMode.WORLD_Z,
                material,
                environmentLighting,
                null,
                null,
                model,
                appearance
        );
    }
}
