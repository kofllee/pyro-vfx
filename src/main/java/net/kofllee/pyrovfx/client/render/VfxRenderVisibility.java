package net.kofllee.pyrovfx.client.render;

import org.joml.Matrix4f;

/**
 * Conservative clip-space sphere checks. Coordinates are camera-relative.
 * A sprite/model touching the frustum remains eligible for rendering.
 *
 * LOD only discards subpixel points; it never changes their geometry, motion,
 * alpha or UV. A configurable 0 threshold disables LOD completely.
 */
public final class VfxRenderVisibility {
    private VfxRenderVisibility() {}

    public static boolean visible(Matrix4f mvp, double x, double y, double z, double radius) {
        float fx = (float) x, fy = (float) y, fz = (float) z;
        float w = mvp.m03() * fx + mvp.m13() * fy + mvp.m23() * fz + mvp.m33();
        float cx = mvp.m00() * fx + mvp.m10() * fy + mvp.m20() * fz + mvp.m30();
        float cy = mvp.m01() * fx + mvp.m11() * fy + mvp.m21() * fz + mvp.m31();
        float cz = mvp.m02() * fx + mvp.m12() * fy + mvp.m22() * fz + mvp.m32();
        // Overestimation of the projected radius prevents edge clipping,
        // including when nonuniform scale and camera rotations are present.
        float r = (float) radius * Math.max(1f, Math.max(
                Math.abs(mvp.m00()) + Math.abs(mvp.m10()) + Math.abs(mvp.m20()),
                Math.abs(mvp.m01()) + Math.abs(mvp.m11()) + Math.abs(mvp.m21())));
        return !(cx < -w-r || cx > w+r || cy < -w-r || cy > w+r || cz < -w-r || cz > w+r);
    }

    public static boolean exceedsPixelFootprint(Matrix4f mvp, double x, double y, double z,
                                                 double radius, int viewportHeight,
                                                 double minDiameterPixels) {
        if (minDiameterPixels <= 0 || viewportHeight <= 0) return true;
        float fx = (float) x, fy = (float) y, fz = (float) z;
        float w = mvp.m03() * fx + mvp.m13() * fy + mvp.m23() * fz + mvp.m33();
        if (w <= 0.0f) return true;
        float projectedRadius = (float) radius * Math.max(
                Math.abs(mvp.m00()) + Math.abs(mvp.m10()) + Math.abs(mvp.m20()),
                Math.abs(mvp.m01()) + Math.abs(mvp.m11()) + Math.abs(mvp.m21()));
        return 2.0 * projectedRadius / w * viewportHeight / 2.0 >= minDiameterPixels;
    }
}
