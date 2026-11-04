package net.kofllee.pyrovfx.client;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** No benchmarking code is linked by the release client. */
public final class VfxDevHooks {
    private static LongSupplier tickStart, renderStart;
    private static LongConsumer tickEnd, renderEnd;
    private VfxDevHooks() {}
    public static void install(LongSupplier beforeTick, LongConsumer afterTick,
                               LongSupplier beforeRender, LongConsumer afterRender) {
        tickStart = beforeTick; tickEnd = afterTick;
        renderStart = beforeRender; renderEnd = afterRender;
    }
    public static long beforeTick() { return tickStart == null ? 0 : tickStart.getAsLong(); }
    public static void afterTick(long start) { if (tickEnd != null) tickEnd.accept(start); }
    public static long beforeRender() { return renderStart == null ? 0 : renderStart.getAsLong(); }
    public static void afterRender(long start) { if (renderEnd != null) renderEnd.accept(start); }
}
