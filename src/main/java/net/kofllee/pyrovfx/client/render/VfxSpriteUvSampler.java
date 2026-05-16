package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.vfx.type.VfxUvMode;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public final class VfxSpriteUvSampler {
    private VfxSpriteUvSampler() {}

    public static VfxSpriteUvRect sample(ClientVfxParticle particle) {
        ClientVfxSpriteUvState uv = particle.spriteUv();

        if (uv == null || uv.mode() == VfxUvMode.FULL) {
            return VfxSpriteUvRect.full();
        }

        int frame = resolveFrame(particle, uv);

        VfxVec3 textureSize = uv.textureSize();
        VfxVec3 start = uv.uvStart();
        VfxVec3 size = uv.uvSize();
        VfxVec3 step = uv.uvStep();

        double x = start.x() + step.x() * frame;
        double y = start.y() + step.y() * frame;

        float u0 = (float) (x / textureSize.x());
        float v0 = (float) (y / textureSize.y());
        float u1 = (float) ((x + size.x()) / textureSize.x());
        float v1 = (float) ((y + size.y()) / textureSize.y());

        return new VfxSpriteUvRect(u0, v0, u1, v1);
    }

    private static int resolveFrame(ClientVfxParticle particle, ClientVfxSpriteUvState uv) {
        int frameCount = Math.max(1, uv.frameCount());

        if (uv.mode() == VfxUvMode.STATIC) {
            return Math.clamp(uv.randomStartFrame(), 0, frameCount - 1);
        }

        int frame;

        if (uv.stretchToLifetime()) {
            frame = (int) Math.floor(particle.ageNormalized() * frameCount);
        } else {
            frame = (int) Math.floor((particle.age() / 20.0) * uv.fps());
        }

        frame += uv.randomStartFrame();

        if (uv.loop()) {
            return Math.floorMod(frame, frameCount);
        }

        return Math.clamp(frame, 0, frameCount - 1);
    }
}
