package net.kofllee.pyrovfx.vfx;

import net.kofllee.pyrovfx.PyroVfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class TestVfxDefinitions {
    public static final VfxDefinition TEST_SMOKE = new VfxDefinition(
            ResourceLocation.fromNamespaceAndPath(PyroVfx.MOD_ID, "test_smoke"),
            1,
            List.of(
                    new VfxEmitterDefinition(
                            VfxEmitterShape.POINT,
                            VfxEmitterMode.BURST,
                            24,
                            new  VfxParticleDefinition(
                                    VfxParticleRenderType.MINECRAFT_PARTICLE,
                                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                    0.08,
                                    0.12
                            )
                    )
            )
    );

    private TestVfxDefinitions() {}
}
