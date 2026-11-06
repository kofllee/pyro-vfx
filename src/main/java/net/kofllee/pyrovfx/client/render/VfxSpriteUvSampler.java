package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.client.vfx.VfxSpriteParticleView;
import net.kofllee.pyrovfx.vfx.type.VfxUvMode;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public final class VfxSpriteUvSampler {
    private VfxSpriteUvSampler() {}

    public static VfxSpriteUvRect sample(VfxSpriteParticleView particle) {
        ClientVfxSpriteUvState uv = particle.spriteUv();

        if (uv == null || uv.mode() == VfxUvMode.FULL) {
            return VfxSpriteUvRect.full();
        }

        int frame = frameIndex(particle.age(), particle.ageNormalized(), uv);
        return sampleFrame(uv, frame);
    }

    /** Immutable sheet coordinates, reused when one GPU cohort has multiple
     * per-particle random start frames.
     */
    public static VfxSpriteUvRect sampleFrame(ClientVfxSpriteUvState uv, int frame) {
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

    public static int frameIndex(int age, double normalizedAge, ClientVfxSpriteUvState uv) {
        int frameCount = Math.max(1, uv.frameCount());

        if (uv.mode() == VfxUvMode.STATIC) {
            return Math.clamp(uv.randomStartFrame(), 0, frameCount - 1);
        }

        int frame;

        if (uv.stretchToLifetime()) {
            frame = (int) Math.floor(normalizedAge * frameCount);
        } else {
            frame = (int) Math.floor((age / 20.0) * uv.fps());
        }

        frame += uv.randomStartFrame();

        if (uv.loop()) {
            return Math.floorMod(frame, frameCount);
        }

        return Math.clamp(frame, 0, frameCount - 1);
    }
}
