package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxRenderType;

public record VfxRenderDefinition (VfxRenderType type, VfxMinecraftParticleRenderDefinition minecraftParticle) {
    public static VfxRenderDefinition minecraftParticle(VfxMinecraftParticleRenderDefinition minecraftParticle) {
        return new VfxRenderDefinition(VfxRenderType.MINECRAFT_PARTICLE, minecraftParticle);
    }
}
