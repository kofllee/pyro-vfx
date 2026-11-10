package net.kofllee.pyroui;

/**
 * Ignition / Deep Dark: neutral Spotify-like elevation with precise Pyro heat.
 * Geometry and motion are shared tokens, not per-widget magic numbers.
 */
public final class UiTheme {
    public static final int BG = 0xFF000000;
    public static final int BASE = 0xFF121212;
    public static final int PANEL = 0xFF181818;
    public static final int ELEVATED = 0xFF242424;
    public static final int HOVER = 0xFF303030;
    public static final int DIVIDER = 0xFF333333;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int SECONDARY = 0xFFB3B3B3;
    public static final int ACCENT = 0xFFFF6432;
    public static final int HIGHLIGHT = 0xFFFFB067;
    public static final int ERROR = 0xFFFF7272;
    public static final int WARNING = 0xFFFFC266;
    public static final int SELECTION = 0xFF342017;
    public static final int SELECTION_HOVER = 0xFF42291D;
    public static final int QUIET = 0xFF747474;

    public static final int R_CONTROL = 1;
    public static final int R_PANEL = 1;
    public static final int R_FLOAT = 1;
    public static final int R_PILL = 12;

    // Retained aliases so existing plugins built against the first library do not break.
    @Deprecated public static final int CARBON = BG;
    @Deprecated public static final int IRON = PANEL;
    @Deprecated public static final int STEEL = ELEVATED;
    @Deprecated public static final int BORDER = DIVIDER;
    @Deprecated public static final int EMBER = ACCENT;
    @Deprecated public static final int GLOW = HIGHLIGHT;
    @Deprecated public static final int MUTED = SECONDARY;

    public int accent = ACCENT;
    public int contextAccent = ACCENT;
    public boolean animations = true;
    public static final int SPACE_INLINE = 3, SPACE_ITEM = 5, SPACE_GROUP = 12;
    public static final int SCALE_MIN = 40, SCALE_MAX = 250, SCALE_STEP = 5;
    public int padding = 6, spacing = SPACE_ITEM, rowHeight = 23, titleHeight = 23;
    public int scalePercent = 100;

    public void reset() {
        accent = ACCENT;
        contextAccent = ACCENT;
        animations = true;
        scalePercent = 100;
    }

    public int scale(int px) {
        return Math.max(1, px);
    }

    public float progress(long since, int durationMs) {
        if (!animations || since <= 0) return 1f;
        return clamp01((System.nanoTime() - since) / (durationMs * 1_000_000f));
    }
    public static float clamp01(float t) { return Math.max(0, Math.min(1, t)); }
    public static float smooth(float t) {
        t = clamp01(t);
        return t*t*(3f-2f*t);
    }
    public static float out(float t) {
        t = 1f-clamp01(t);
        return 1f-t*t*t;
    }
    public float fade(long started, int durationMs) { return smooth(progress(started, durationMs)); }
    public static int alpha(int argb, float fraction) {
        int a = Math.round(((argb >>> 24) & 255) * clamp01(fraction));
        return (argb & 0xFFFFFF) | (a << 24);
    }
    public static int mix(int from, int to, float t) {
        t = clamp01(t);
        int a = channel(from,to,24,t), r = channel(from,to,16,t);
        int g = channel(from,to,8,t), b = channel(from,to,0,t);
        return (a<<24)|(r<<16)|(g<<8)|b;
    }
    private static int channel(int a, int b, int shift, float t) {
        return Math.round(((a >>> shift) & 255) * (1-t) + ((b >>> shift) & 255) * t);
    }
    /** A low-saturation surface tint, not full-bright color. */
    public int contextSurface() { return mix(PANEL, contextAccent, .12f); }
}
