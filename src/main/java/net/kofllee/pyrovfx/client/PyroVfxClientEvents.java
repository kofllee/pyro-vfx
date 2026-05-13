package net.kofllee.pyrovfx.client;

import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(
        modid = PyroVfx.MOD_ID,
        value = Dist.CLIENT
)
public final class PyroVfxClientEvents {
    private PyroVfxClientEvents(){}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event){
        ClientVfxManager.tick();
    }
}
