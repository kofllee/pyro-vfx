package net.kofllee.pyroui;

/** Public drawing API for Minecraft UI. The production renderer lives in Pyro UI. */
public interface UiCanvas {
    void fill(int x, int y, int width, int height, int argb);
    void text(String text, int x, int y, int argb);
    int measure(String text);
    /** Draw a named sprite from the native Pyro UI icon atlas. */
    default void icon(String id, int x, int y, int size, int color) {}
    /** Typography roles use the same backend for metrics and rendering. */
    default void textHeading(String value, int x, int y, int color) { text(value, x, y, color); }
    default int measureHeading(String value) { return measure(value); }
    default void textMeta(String value, int x, int y, int color) { text(value, x, y, color); }
    default int measureMeta(String value) { return measure(value); }
    /** Opacity and small translation, never a scale transform on glyphs. */
    default void pushLayer(float opacity, int translateY) {}
    default void popLayer() {}
    void clip(UiRect bounds);
    void unclip();

    /** Draw the given Minecraft-managed PNG region. Implemented by the native renderer. */
    default void image(UiTextureRegion region, UiRect destination, int argb) {
        throw new UnsupportedOperationException("Texture drawing requires a Pyro UI renderer");
    }
    /** Native Minecraft camera frame, isolated from the desktop background. */
    default void minecraftFrame(UiRect destination) {
        fill(destination, UiTheme.BG);
    }

    default void drawImage(net.minecraft.resources.ResourceLocation id,
                           int sourceWidth, int sourceHeight, UiRect destination, int tint) {
        image(UiTextureRegion.full(id, sourceWidth, sourceHeight), destination, tint);
    }

    /** Stretch nine segments while leaving the four source corners unscaled. */
    default void nineSlice(UiTextureRegion image, UiRect dst,
                           int left, int top, int right, int bottom, int tint) {
        UiNineSlice.draw(this, image, dst, left, top, right, bottom, tint);
    }

    default void gradient(UiRect rect, int top, int bottom) {
        for (int y = 0; y < rect.h(); y++) {
            double t = rect.h() <= 1 ? 0 : y / (double)(rect.h() - 1);
            int a = channel(top, bottom, 24, t);
            int r = channel(top, bottom, 16, t);
            int g = channel(top, bottom, 8, t);
            int b = channel(top, bottom, 0, t);
            fill(rect.x(), rect.y() + y, rect.w(), 1, (a << 24) | (r << 16) | (g << 8) | b);
        }
    }
    default void gradientHorizontal(UiRect rect, int left, int right) {
        for (int x = 0; x < rect.w(); x++) {
            double t = rect.w() <= 1 ? 0 : x / (double)(rect.w() - 1);
            fill(rect.x() + x, rect.y(), 1, rect.h(),
                    (channel(left, right, 24, t) << 24)
                    | (channel(left, right, 16, t) << 16)
                    | (channel(left, right, 8, t) << 8)
                    | channel(left, right, 0, t));
        }
    }
    default void gradientStops(UiRect r, int... stops) {
        if (stops.length == 0) return;
        if (stops.length == 1) { fill(r, stops[0]); return; }
        for (int row = 0; row < r.h(); row++) {
            double segment = row * (stops.length - 1.0) / Math.max(1, r.h() - 1);
            int i = Math.min(stops.length - 2, (int) segment);
            double t = segment - i;
            int first = stops[i], second = stops[i + 1];
            int color = (channel(first, second, 24, t) << 24)
                    | (channel(first, second, 16, t) << 16)
                    | (channel(first, second, 8, t) << 8)
                    | channel(first, second, 0, t);
            fill(r.x(), r.y() + row, r.w(), 1, color);
        }
    }
    private static int channel(int a, int b, int shift, double t) {
        return (int) Math.round(((a >>> shift) & 255) * (1 - t) + ((b >>> shift) & 255) * t);
    }
    /** AA circle-derived corners, with a single opaque interior instead of
     * overlapping horizontal and vertical stripes or faux chamfers. */
    default void rounded(UiRect r, int radius, int color) {
        if (r.w() <= 0 || r.h() <= 0 || (color >>> 24) == 0) return;
        int rad = Math.max(0, Math.min(radius, Math.min(r.w(), r.h()) / 2));
        if (rad == 0) { fill(r, color); return; }
        if (rad == 1) {
            // Exactly one square GUI pixel omitted at each corner.
            // No radius approximation, fractional alpha or staircase.
            fill(r.x() + 1,r.y(),Math.max(0,r.w()-2),1,color);
            fill(r.x(),r.y()+1,r.w(),Math.max(0,r.h()-2),color);
            fill(r.x()+1,r.y()+r.h()-1,Math.max(0,r.w()-2),1,color);
            return;
        }
        fill(r.x(), r.y() + rad, r.w(), Math.max(0,r.h() - rad * 2), color);
        for (int row = 0; row < rad; row++) {
            double d = rad - row - .5;
            double leftEdge = rad - Math.sqrt(Math.max(0,rad * rad - d * d));
            int start = (int) Math.ceil(leftEdge);
            int pixels = Math.max(0,r.w() - 2 * start);
            fill(r.x() + start,r.y() + row,pixels,1,color);
            fill(r.x() + start,r.y() + r.h() - 1 - row,pixels,1,color);
            if (start > 0) {
                // Partially covered silhouette pixels approximate a smooth
                // quarter-circle without introducing a new shader.
                float coverage = (float)(start - leftEdge);
                if (coverage > 0f) {
                    int aa = alpha(color,coverage);
                    fill(r.x()+start-1,r.y()+row,1,1,aa);
                    fill(r.x()+r.w()-start,r.y()+row,1,1,aa);
                    fill(r.x()+start-1,r.y()+r.h()-1-row,1,1,aa);
                    fill(r.x()+r.w()-start,r.y()+r.h()-1-row,1,1,aa);
                }
            }
        }
    }
    /** Ring outline with matched outer/inner circular geometry. */
    default void roundedBorder(UiRect r, int radius, int color) {
        if (r.w() <= 1 || r.h() <= 1) return;
        int rad = Math.max(0,Math.min(radius,Math.min(r.w(),r.h())/2));
        if (rad == 0) { border(r,color); return; }
        if (rad == 1) {
            fill(r.x()+1,r.y(),Math.max(0,r.w()-2),1,color);
            fill(r.x()+1,r.y()+r.h()-1,Math.max(0,r.w()-2),1,color);
            fill(r.x(),r.y()+1,1,Math.max(0,r.h()-2),color);
            fill(r.x()+r.w()-1,r.y()+1,1,Math.max(0,r.h()-2),color);
            return;
        }
        fill(r.x(),r.y()+rad,1,Math.max(0,r.h()-2*rad),color);
        fill(r.x()+r.w()-1,r.y()+rad,1,Math.max(0,r.h()-2*rad),color);
        for (int row=0;row<rad;row++) {
            double d=rad-row-.5;
            int edge=(int)Math.ceil(rad-Math.sqrt(Math.max(0,rad*rad-d*d)));
            int inner=(int)Math.ceil((rad-1)-Math.sqrt(Math.max(0,
                    (rad-1)*(rad-1)-Math.max(0,(rad-row-1.5)*(rad-row-1.5)))));
            int extent=Math.max(1,inner-edge+1);
            fill(r.x()+edge,r.y()+row,extent,1,color);
            fill(r.x()+r.w()-edge-extent,r.y()+row,extent,1,color);
            fill(r.x()+edge,r.y()+r.h()-1-row,extent,1,color);
            fill(r.x()+r.w()-edge-extent,r.y()+r.h()-1-row,extent,1,color);
        }
        fill(r.x()+rad,r.y(),Math.max(0,r.w()-2*rad),1,color);
        fill(r.x()+rad,r.y()+r.h()-1,Math.max(0,r.w()-2*rad),1,color);
    }
    /** Alpha-weighted subpixel line. Draws directly into the host's GUI surface. */
    default void line(double x1, double y1, double x2, double y2, int color, float thickness) {
        boolean steep = Math.abs(y2 - y1) > Math.abs(x2 - x1);
        if (steep) {
            double t=x1; x1=y1; y1=t;
            t=x2; x2=y2; y2=t;
        }
        if (x1 > x2) {
            double t=x1; x1=x2; x2=t;
            t=y1; y1=y2; y2=t;
        }
        double dx = x2 - x1, dy = y2 - y1;
        double slope = Math.abs(dx) < 1e-8 ? 0 : dy / dx;
        int steps = Math.max(1, (int) Math.ceil(dx));
        for (int i=0; i<=steps; i++) {
            double x = x1 + dx*i/steps, y = y1 + (x - x1)*slope;
            int fy = (int)Math.floor(y);
            double f = y - fy;
            int ax = (int)Math.round(x);
            int c1 = alpha(color, 1 - f), c2 = alpha(color, f);
            if (steep) {
                fill(fy, ax, 1, 1, c1);
                if (f > 0) fill(fy+1, ax, 1, 1, c2);
            } else {
                fill(ax, fy, 1, 1, c1);
                if (f > 0) fill(ax, fy+1, 1, 1, c2);
            }
        }
    }
    private static int alpha(int color, double coverage) {
        int a = (int) Math.round(((color >>> 24) & 255) * Math.max(0, Math.min(1, coverage)));
        return (a << 24) | (color & 0x00ffffff);
    }
    default void fill(UiRect r, int argb) { if (r.w() > 0 && r.h() > 0) fill(r.x(), r.y(), r.w(), r.h(), argb); }
    default void border(UiRect r, int argb) {
        fill(r.x(), r.y(), r.w(), 1, argb);
        fill(r.x(), r.y() + r.h() - 1, r.w(), 1, argb);
        fill(r.x(), r.y(), 1, r.h(), argb);
        fill(r.x() + r.w() - 1, r.y(), 1, r.h(), argb);
    }
    default void textHeadingClipped(String text, int x, int y, int maxWidth, int argb) {
        if (text == null || maxWidth <= 0) return;
        if (measureHeading(text) <= maxWidth) { textHeading(text, x, y, argb); return; }
        String suffix = "…";
        int end = text.length();
        while (end > 0 && measureHeading(text.substring(0, end) + suffix) > maxWidth) end--;
        if (end > 0) textHeading(text.substring(0, end) + suffix, x, y, argb);
    }
    default void textMetaClipped(String text, int x, int y, int maxWidth, int argb) {
        if (text == null || maxWidth <= 0) return;
        if (measureMeta(text) <= maxWidth) { textMeta(text, x, y, argb); return; }
        String suffix = "…";
        int end = text.length();
        while (end > 0 && measureMeta(text.substring(0, end) + suffix) > maxWidth) end--;
        if (end > 0) textMeta(text.substring(0, end) + suffix, x, y, argb);
    }
    default void textClipped(String text, int x, int y, int maxWidth, int argb) {
        if (maxWidth <= 0 || text == null) return;
        if (measure(text) <= maxWidth) { text(text, x, y, argb); return; }
        String suffix = "...";
        if (measure(suffix) > maxWidth) return;
        int end = text.length();
        while (end > 0 && measure(text.substring(0, end) + suffix) > maxWidth) end--;
        text(text.substring(0, end) + suffix, x, y, argb);
    }
}
