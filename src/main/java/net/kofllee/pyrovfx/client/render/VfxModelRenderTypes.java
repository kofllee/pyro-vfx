package net.kofllee.pyrovfx.client.render;

import net.kofllee.pyrovfx.vfx.type.VfxModelRenderLayer;
import net.minecraft.client.renderer.RenderType;

public final class VfxModelRenderTypes {
    private VfxModelRenderTypes() {}

    public static RenderType get(VfxModelRenderLayer layer) {
        return switch (layer) {
            case SOLID -> net.minecraft.client.renderer.Sheets.solidBlockSheet();
            case CUTOUT -> net.minecraft.client.renderer.Sheets.cutoutBlockSheet();
            case TRANSLUCENT -> net.minecraft.client.renderer.Sheets.translucentCullBlockSheet();
        };
    }
}
