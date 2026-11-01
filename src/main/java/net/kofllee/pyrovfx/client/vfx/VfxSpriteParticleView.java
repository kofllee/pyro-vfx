package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvState;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.world.phys.Vec3;

/** Read-only render view. A dense pool reuses a single cursor, not one object per particle. */
public interface VfxSpriteParticleView {
    VfxEmitterDefinition emitterDefinition();
    Vec3 interpolatedPosition(float partialTick);
    Vec3 interpolatedRotation(float partialTick);
    Vec3 interpolatedScale(float partialTick);
    VfxColor interpolatedColor(float partialTick);
    Vec3 velocity();
    ClientVfxSpriteUvState spriteUv();
    int age();
    double ageNormalized();
}