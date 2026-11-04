package net.kofllee.pyrovfx.client.dev;

import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.VfxDevHooks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Installs optional benchmark profiling on the first development client tick. */
@EventBusSubscriber(modid = PyroVfx.MOD_ID, value = Dist.CLIENT)
public final class VfxDevelopmentBootstrap {
    private static boolean installed;
    private VfxDevelopmentBootstrap() {}
    @SubscribeEvent public static void setup(ClientTickEvent.Pre event) {
        if (installed) return;
        installed = true;
        VfxDevHooks.install(VfxBenchHarness::beforeTick, VfxBenchHarness::afterTick,
                            VfxBenchHarness::beforeRender, VfxBenchHarness::afterRender);
    }
}
