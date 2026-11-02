package net.kofllee.pyrovfx;

import com.mojang.logging.LogUtils;
import net.kofllee.pyrovfx.client.vfx.VfxBudgetConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(PyroVfx.MOD_ID)
public final class PyroVfx {
    public static final String MOD_ID = "pyro_vfx";

    private static final Logger LOGGER = LogUtils.getLogger();

    public PyroVfx(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, VfxBudgetConfig.SPEC);
        LOGGER.info("Pyro VFX initialized");
    }
}