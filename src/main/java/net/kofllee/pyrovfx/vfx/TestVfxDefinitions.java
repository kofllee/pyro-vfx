package net.kofllee.pyrovfx.vfx;

import net.kofllee.pyrovfx.PyroVfx;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class TestVfxDefinitions {
    public static final VfxDefinition TEST_SMOKE_BURST = new VfxDefinition(
            id("test_smoke_burst"),
            1,
            List.of(
                    new VfxEmitterDefinition(
                            VfxEmitterShapeDefinition.sphere(VfxVec3.ZERO, 10, true),
                            VfxEmitterMode.BURST,
                            1000,
                            minecraftParticle("minecraft:campfire_cosy_smoke", 0)
                    )
            )
    );

    public static final VfxDefinition TEST_FLASH = new VfxDefinition(
            id("test_flash"),
            1,
            List.of(
                    new VfxEmitterDefinition(
                            VfxEmitterShapeDefinition.point(VfxVec3.ZERO),
                            VfxEmitterMode.BURST,
                            8,
                            minecraftParticle("minecraft:flash", 0.02)
                    )
            )
    );

    public static final VfxDefinition TEST_SPARKS = new VfxDefinition(
            id("test_sparks"),
            1,
            List.of(
                    new VfxEmitterDefinition(
                            VfxEmitterShapeDefinition.point(VfxVec3.ZERO),
                            VfxEmitterMode.BURST,
                            20,
                            minecraftParticle("minecraft:flame", 10)
                    )
            )
    );

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, path);
    }

    private static VfxParticleDefinition minecraftParticle(String id, double speed) {
        return new VfxParticleDefinition(
                new VfxAppearanceDefinition(
                        VfxParticleRenderType.MINECRAFT_PARTICLE,
                        ResourceLocation.parse(id)
                ),
                VfxMotionDefinition.simple(VfxVelocityDefinition.constant(new VfxVec3(0, 1, 0), speed, 0))
        );
    }

    private TestVfxDefinitions() {}
}
