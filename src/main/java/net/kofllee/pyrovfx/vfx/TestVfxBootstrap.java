package net.kofllee.pyrovfx.vfx;

import net.kofllee.pyrovfx.vfx.resource.VfxRegistry;

public final class TestVfxBootstrap {
    private static boolean initialized = false;

    private TestVfxBootstrap() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        VfxRegistry.register(TestVfxDefinitions.TEST_SMOKE);
    }
}
