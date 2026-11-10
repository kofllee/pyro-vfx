package net.kofllee.pyroui;

/** Nine-slice layout, shared by renderer and unit tests. No GPU state or allocations per segment. */
public final class UiNineSlice {
    private UiNineSlice() {}

    public static void draw(UiCanvas canvas, UiTextureRegion source, UiRect dst,
                            int left, int top, int right, int bottom, int color) {
        if (dst.w() <= 0 || dst.h() <= 0) return;
        if (left < 0 || top < 0 || right < 0 || bottom < 0
                || left + right > source.width() || top + bottom > source.height())
            throw new IllegalArgumentException("Invalid nine-slice margins");
        int dl = Math.min(left, dst.w()), dr = Math.min(right, dst.w() - dl);
        int dt = Math.min(top, dst.h()), db = Math.min(bottom, dst.h() - dt);
        int[] sx = {0, left, source.width() - right, source.width()};
        int[] sy = {0, top, source.height() - bottom, source.height()};
        int[] dx = {dst.x(), dst.x() + dl, dst.x() + dst.w() - dr, dst.x() + dst.w()};
        int[] dy = {dst.y(), dst.y() + dt, dst.y() + dst.h() - db, dst.y() + dst.h()};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            int sw = sx[col + 1] - sx[col], sh = sy[row + 1] - sy[row];
            int dw = dx[col + 1] - dx[col], dh = dy[row + 1] - dy[row];
            if (sw > 0 && sh > 0 && dw > 0 && dh > 0)
                canvas.image(source.subRegion(sx[col], sy[row], sw, sh),
                        new UiRect(dx[col], dy[row], dw, dh), color);
        }
    }
}