package net.kofllee.pyrovfx.client.render;

/** Shaderpacks do not globally disable custom GPU VFX renderers.
 * Geometry support, GL capabilities, initialization failures and explicit
 * user/debug overrides are still checked by their respective renderers. */
public final class VfxRenderCompatibility {
    private VfxRenderCompatibility() {}

    public static boolean eligible(boolean forcedLegacy, boolean activeShaderPipeline) {
        return !forcedLegacy;
    }

    public static boolean compatibleEnvironment() {
        return eligible(Boolean.getBoolean("pyrovfx.render.forceLegacy"), false);
    }

    public static String reason() {
        if (Boolean.getBoolean("pyrovfx.render.forceLegacy"))
            return "user/test forced Minecraft RenderType";
        return "GPU VFX allowed with shaderpacks; renderer capability and GL checks still apply";
    }
}
