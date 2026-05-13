package net.kofllee.pyrovfx.vfx;

import net.kofllee.pyrovfx.PyroVfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class TestVfxDefinitions {
    public static final VfxDefinition TEST_SMOKE_BURST = new VfxDefinition(
            id("test_smoke_burst"),
            1,
            List.of(
                    new VfxEmitterDefinition(
                            VfxEmitterShape.POINT,
                            VfxEmitterMode.BURST,
                            32,
                            minecraftParticle("minecraft:campfire_cosy_smoke", 0.08, 0.18)
                    )
            )
    );

    public static final VfxDefinition TEST_FLASH = new VfxDefinition(
            id("test_flash"),
            1,
            List.of(
                    new VfxEmitterDefinition(
                            VfxEmitterShape.POINT,
                            VfxEmitterMode.BURST,
                            8,
                            minecraftParticle("minecraft:flash", 0.02, 0.04)
                    )
            )
    );

    public static final VfxDefinition TEST_SPARKS = new VfxDefinition(
            id("test_sparks"),
            1,
            List.of(
                    new VfxEmitterDefinition(
                            VfxEmitterShape.POINT,
                            VfxEmitterMode.BURST,
                            20,
                            minecraftParticle("minecraft:flame", 0.18, 0.08)
                    )
            )
    );

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, path);
    }

    private static VfxParticleDefinition minecraftParticle(String id, double speed, double spread) {
        return new VfxParticleDefinition(
                new VfxAppearanceDefinition(
                        VfxParticleRenderType.MINECRAFT_PARTICLE,
                        ResourceLocation.parse(id)
                ),
                speed,
                spread
        );
    }

    private TestVfxDefinitions() {}
}
