package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.vfx.definition.VfxSpriteUvDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxUvMode;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public final class ClientVfxSpriteUvResolver {
    private ClientVfxSpriteUvResolver() {}

    public static ClientVfxSpriteUvState resolve(
            VfxSpriteUvDefinition definition,
            VfxExpressionContext particleSpawnContext,
            double particleRandom
    ){
        if (definition == null || definition.mode() == VfxUvMode.FULL) {
            return ClientVfxSpriteUvState.full();
        }

        VfxVec3 textureSize = sanitizeTextureSize(
                definition.textureSize().evaluate(particleSpawnContext)
        );

        VfxVec3 uvStart = definition.uvStart().evaluate(particleSpawnContext);
        VfxVec3 uvSize = sanitizeUvSize(
                definition.uvSize().evaluate(particleSpawnContext)
        );

        VfxVec3 uvStep = definition.uvStep().evaluate(particleSpawnContext);
        int frameCount = Math.max(
                1,
                (int) Math.round(definition.frameCount().evaluate(particleSpawnContext))
        );

        double fps = Math.max(
                0.0,
                definition.fps().evaluate(particleSpawnContext)
        );

        int randomStartFrame = definition.randomStartFrame()
                ? Math.floorMod((int) Math.floor(particleRandom * frameCount), frameCount)
                : 0;

        return new ClientVfxSpriteUvState(
                definition.mode(),
                textureSize,
                uvStart,
                uvSize,
                uvStep,
                frameCount,
                fps,
                definition.stretchToLifetime(),
                definition.loop(),
                randomStartFrame
        );
    }

    private static VfxVec3 sanitizeTextureSize(VfxVec3 value) {
        return new VfxVec3(
                Math.max(1.0, value.x()),
                Math.max(1.0, value.y()),
                value.z()
        );
    }

    private static VfxVec3 sanitizeUvSize(VfxVec3 value) {
        return new VfxVec3(
                Math.max(0.0001, value.x()),
                Math.max(0.0001, value.y()),
                value.z()
        );
    }
}
