package net.kofllee.pyrovfx.client;

import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.vfx.resource.VfxReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(
        modid = PyroVfx.MOD_ID,
        value = Dist.CLIENT
)
public final class PyroVfxClient {
    private PyroVfxClient() {}

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new VfxReloadListener());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        ClientVfxManager.tick();
    }
}