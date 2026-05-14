package net.kofllee.pyrovfx.vfx.value;

import net.kofllee.pyrovfx.vfx.expression.VfxExpressionNode;

public record VfxColor(double r, double g, double b, double a) {
    public static final VfxColor WHITE = new VfxColor(1.0, 1.0, 1.0, 1.0);

    public static VfxColor rgba(double r, double g, double b, double a) {
        return new VfxColor(
                Math.clamp(r, 0.0, 1.0),
                Math.clamp(g, 0.0, 1.0),
                Math.clamp(b, 0.0, 1.0),
                Math.clamp(a, 0.0, 1.0)
        );
    }
}
