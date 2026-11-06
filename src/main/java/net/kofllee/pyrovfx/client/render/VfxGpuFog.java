package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;

/** Restores Minecraft's world fog for native ShaderInstance and RenderType
 * particle passes if the LevelRenderer has reset fog for the next stage.
 * Does not implement or override the fragment shader itself.
 */
public final class VfxGpuFog {
    // Retain the fog computed for world geometry before Minecraft's
    // BEFORE_TRANSLUCENT and AFTER_PARTICLES VFX stages. Some alternate
    // render pipelines disable fog between intermediate world stages.
    private static float frameStart, frameEnd;
    private static float[] frameColor = {1, 1, 1};
    private static FogShape frameShape = FogShape.SPHERE;
    private static boolean captured;
    private static float lastAppliedStart, lastAppliedEnd;
    private VfxGpuFog() {}

    public static void captureWorldFog() {
        // A world without Pyro effects should not allocate a cloned fog
        // color every rendered frame.
        if (net.kofllee.pyrovfx.client.vfx.ClientVfxManager.getActiveParticleCount() == 0) {
            captured = false;
            return;
        }
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        captured = Float.isFinite(start) && Float.isFinite(end) && end > start;
        if (captured) {
            frameStart = start;
            frameEnd = end;
            frameColor = RenderSystem.getShaderFogColor().clone();
            frameShape = RenderSystem.getShaderFogShape();
        }
    }

    public static float lastAppliedStart() { return lastAppliedStart; }
    public static float lastAppliedEnd() { return lastAppliedEnd; }

    /** Native ShaderInstance and RenderType read RenderSystem's fog uniforms when
     * applied. Restore world fog for the late VFX pass too, then restore
     * the original disabled state for Minecraft/other mods.
     */
    public static FogScope openWorldPass() {
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        if (Float.isFinite(start) && Float.isFinite(end) && end > start)
            return FogScope.NO_CHANGE;
        float[] originalColor = RenderSystem.getShaderFogColor().clone();
        FogShape originalShape = RenderSystem.getShaderFogShape();
        if (captured) {
            RenderSystem.setShaderFogStart(frameStart);
            RenderSystem.setShaderFogEnd(frameEnd);
            RenderSystem.setShaderFogColor(frameColor[0], frameColor[1],
                    frameColor[2], frameColor.length > 3 ? frameColor[3] : 1f);
            RenderSystem.setShaderFogShape(frameShape);
        } else if (Minecraft.getInstance().gameRenderer.getMainCamera()
                .getFluidInCamera() == FogType.WATER) {
            RenderSystem.setShaderFogStart(-8f);
            RenderSystem.setShaderFogEnd(Float.isFinite(end)
                    && end > -8f && end < 1.0e20f ? end : 96f);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        } else {
            return FogScope.NO_CHANGE;
        }
        lastAppliedStart = RenderSystem.getShaderFogStart();
        lastAppliedEnd = RenderSystem.getShaderFogEnd();
        return new FogScope(true, start, end, originalColor, originalShape);
    }

    public record FogScope(boolean changed, float start, float end,
                           float[] color, FogShape shape) implements AutoCloseable {
        private static final FogScope NO_CHANGE =
                new FogScope(false, 0f, 0f, null, FogShape.SPHERE);

        @Override
        public void close() {
            if (!changed) return;
            RenderSystem.setShaderFogStart(start);
            RenderSystem.setShaderFogEnd(end);
            RenderSystem.setShaderFogColor(color[0], color[1], color[2],
                    color.length > 3 ? color[3] : 1f);
            RenderSystem.setShaderFogShape(shape);
        }
    }

}
