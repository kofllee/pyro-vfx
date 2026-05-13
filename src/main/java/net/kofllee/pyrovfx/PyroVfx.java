package net.kofllee.pyrovfx;

import com.mojang.logging.LogUtils;
import net.kofllee.pyrovfx.vfx.TestVfxBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(PyroVfx.MOD_ID)
public final class PyroVfx {
    public static final String MOD_ID = "pyro_vfx";

    private static final Logger LOGGER = LogUtils.getLogger();

    public PyroVfx(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Pyro VFX initialized");

        TestVfxBootstrap.init();
    }
}