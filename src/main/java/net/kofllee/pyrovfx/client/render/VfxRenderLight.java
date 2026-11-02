package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.kofllee.pyrovfx.client.vfx.VfxSpriteParticleView;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class VfxRenderLight {
    private static final Long2IntOpenHashMap LIGHT_CACHE = new Long2IntOpenHashMap();
    private static final int MAX_CACHED_POSITIONS = 8192;
    static { LIGHT_CACHE.defaultReturnValue(-1); }

    private VfxRenderLight() {}

    /** Reuse block light within the render frame without retaining stale world values. */
    public static void beginFrame() { LIGHT_CACHE.clear(); }

    public static int getParticleLight(VfxSpriteParticleView particle, float partialTick) {
        Vec3 position = particle.interpolatedPosition(partialTick);
        return getParticleLight(position.x, position.y, position.z);
    }

    public static int getParticleLight(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return LightTexture.FULL_BRIGHT;
        }

        int bx = net.minecraft.util.Mth.floor(x);
        int by = net.minecraft.util.Mth.floor(y);
        int bz = net.minecraft.util.Mth.floor(z);
        long packed = BlockPos.asLong(bx, by, bz);
        int cached = LIGHT_CACHE.get(packed);
        if (cached != -1) return cached;
        int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.of(packed));
        if (LIGHT_CACHE.size() < MAX_CACHED_POSITIONS) LIGHT_CACHE.put(packed, light);
        return light;
    }
}
