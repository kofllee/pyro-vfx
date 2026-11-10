package net.kofllee.pyroui;

/** Integer GUI-space rectangle. Coordinates are already scaled by the host. */
public record UiRect(int x, int y, int w, int h) {
    public UiRect {
        w = Math.max(0, w);
        h = Math.max(0, h);
    }
    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
    public UiRect intersect(UiRect other) {
        int l = Math.max(x, other.x), t = Math.max(y, other.y);
        int r = Math.min(x + w, other.x + other.w);
        int b = Math.min(y + h, other.y + other.h);
        return new UiRect(l, t, Math.max(0, r - l), Math.max(0, b - t));
    }
    public UiRect inset(int v) {
        return new UiRect(x + v, y + v, Math.max(0, w - 2 * v), Math.max(0, h - 2 * v));
    }
    public UiRect inset(int hInset, int vInset) {
        return new UiRect(x + hInset, y + vInset, Math.max(0, w - 2 * hInset), Math.max(0, h - 2 * vInset));
    }
    public static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(Math.max(min, max), n));
    }
}
