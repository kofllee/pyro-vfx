package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxRenderType;

public record VfxRenderDefinition (
        VfxRenderType type,
        VfxMinecraftParticleRenderDefinition minecraftParticle,
        VfxSpriteRenderDefinition sprite,
        VfxParticleAppearanceDefinition appearance
) {
    public static VfxRenderDefinition minecraftParticle(VfxMinecraftParticleRenderDefinition minecraftParticle) {
        return new VfxRenderDefinition(
                VfxRenderType.MINECRAFT_PARTICLE,
                minecraftParticle,
                null,
                VfxParticleAppearanceDefinition.defaultAppearance()
        );
    }

    public static VfxRenderDefinition sprite(
            VfxSpriteRenderDefinition sprite,
            VfxParticleAppearanceDefinition appearance
    ) {
        return new VfxRenderDefinition(
                VfxRenderType.SPRITE,
                null,
                sprite,
                appearance
        );
    }
}
