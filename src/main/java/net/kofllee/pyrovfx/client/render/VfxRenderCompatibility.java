package net.kofllee.pyrovfx.client.render;

import net.neoforged.fml.ModList;

/** Renderer admission based on actual active shader pipeline, not mod name. */
public final class VfxRenderCompatibility {
    private record IrisProbe(java.lang.reflect.Method instance, java.lang.reflect.Method active) {}
    private static volatile IrisProbe irisProbe;
    private VfxRenderCompatibility() {}

    public static boolean eligible(boolean forcedLegacy, boolean activeShaderPipeline) {
        return !forcedLegacy && !activeShaderPipeline;
    }

    public static boolean compatibleEnvironment() {
        return eligible(Boolean.getBoolean("pyrovfx.render.forceLegacy"),
                shaderPipelineActiveOrUnknown());
    }

    public static String reason() {
        if (Boolean.getBoolean("pyrovfx.render.forceLegacy"))
            return "user/test forced Minecraft RenderType";
        if (shaderPipelineActiveOrUnknown())
            return "active or unverified shaderpack pipeline: framebuffer/depth/composite stage not integrated";
        return "vanilla/OpenGL draw stage; GPU VFX permitted (driver/GL checks still apply)";
    }

    private static boolean shaderPipelineActiveOrUnknown() {
        ModList mods = ModList.get();
        if (mods == null) return false;
        boolean hasShaderMod = mods.isLoaded("iris") || mods.isLoaded("oculus")
                || mods.isLoaded("neoculus") || mods.isLoaded("forgeoculus")
                || mods.isLoaded("neopoculus");
        if (!hasShaderMod) {
            // Sodium and Embeddium alone replace terrain rendering, not
            // Minecraft's active shaderpack framebuffer/compositor.
            return false;
        }
        try {
            IrisProbe probe = irisProbe;
            if (probe == null) {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                probe = new IrisProbe(api.getMethod("getInstance"),
                        api.getMethod("isShaderPackInUse"));
                irisProbe = probe;
            }
            Object instance = probe.instance().invoke(null);
            return Boolean.TRUE.equals(probe.active().invoke(instance));
        } catch (ReflectiveOperationException | LinkageError ex) {
            // Unknown Iris-derived fork API: cannot prove compatibility with
            // arbitrary framebuffers, so preserve correctness.
            return true;
        }
    }
}
