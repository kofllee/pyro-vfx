package net.kofllee.pyrovfx.client.dev;

import com.mojang.logging.LogUtils;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.client.ClientVfxNetwork;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.VfxSockets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/** Logs independently observed network results on each connected Minecraft client. */
@EventBusSubscriber(modid = PyroVfx.MOD_ID, value = Dist.CLIENT)
public final class VfxTwoClientProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CLIENT = System.getProperty("pyrovfx.multiclient.client", "");
    private static final boolean ENABLED = !FMLEnvironment.production
            && (CLIENT.equals("A") || CLIENT.equals("B"));
    private static String pending;
    private static int pendingTicks;
    private static int checks;
    private static int failures;
    private static boolean done;
    private static int doneTicks;

    private VfxTwoClientProbe() {}

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (!ENABLED || event.isOverlay()) return;
        String text = event.getMessage().getString();
        if (!text.startsWith("PYRO_2CLIENT_PHASE:")) return;
        String phase = text.substring("PYRO_2CLIENT_PHASE:".length());
        LOGGER.info("PYRO_2CLIENT {} received phase {}", CLIENT, phase);
        if (phase.equals("READY")) return;
        if (phase.equals("DONE") || phase.equals("FAILED")) {
            done = true;
            if (phase.equals("FAILED")) failures++;
            return;
        }
        if (pending != null) {
            LOGGER.error("PYRO_2CLIENT {} FAIL overlapping phase {} before {}", CLIENT, phase, pending);
            failures++;
        }
        pending = phase;
        pendingTicks = 22;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        if (pending != null && --pendingTicks <= 0) {
            String phase = pending;
            pending = null;
            check(phase);
        }
        if (done && ++doneTicks >= 30) {
            if (pending != null) {
                LOGGER.error("PYRO_2CLIENT {} FAIL unfinished phase {}", CLIENT, pending);
                failures++;
            }
            if (checks != 8) {
                LOGGER.error("PYRO_2CLIENT {} FAIL only {} / 8 phases checked", CLIENT, checks);
                failures++;
            }
            LOGGER.info("PYRO_2CLIENT {} RESULT: {} ({} checks, {} failures)",
                    CLIENT, failures == 0 ? "PASS" : "FAIL", checks, failures);
            done = false;
            Minecraft.getInstance().stop();
        }
    }

    private static void check(String phase) {
        var mc = Minecraft.getInstance();
        int remote = ClientVfxNetwork.activeRemoteCount();
        int instances = ClientVfxManager.getActiveInstanceCount();
        boolean clientB = CLIENT.equals("B");
        int expected = switch (phase) {
            case "START", "RETURN", "DIM_IN", "RECREATE" -> 1;
            case "OUT", "DIM_OUT" -> clientB ? 0 : 1;
            case "REMOVE", "STOP" -> 0;
            default -> -1;
        };
        boolean valid = remote == expected && instances == expected;
        int age = instances == 1 ? ClientVfxManager.instances().getFirst().ageTicks() : -1;
        if (expected == 1 && mc.level != null) {
            var pigs = mc.level.getEntitiesOfClass(Pig.class, new AABB(-15, 120, -15, 15, 160, 15));
            if (pigs.isEmpty()) {
                valid = false;
            } else {
                var pig = pigs.getFirst();
                var socket = Vfx.at(pig).socket("head").offset(0, 0.2, 0).get(1);
                if (socket == null || !VfxSockets.vanillaParts(pig, 1).contains("head")
                        || instances != 1
                        || ClientVfxManager.instances().getFirst().renderTransform(1)
                                .position().distanceToSqr(socket.position()) > 0.04)
                    valid = false;
            }
        }
        if (clientB && phase.equals("RETURN") && age < 40) valid = false;
        if (clientB && phase.equals("DIM_IN") && age < 95) valid = false;
        if (phase.equals("DIM_OUT") && clientB && mc.level != null
                && mc.level.dimension() != net.minecraft.world.level.Level.NETHER) valid = false;
        if (phase.equals("DIM_IN") && mc.level != null
                && mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) valid = false;
        checks++;
        if (!valid) failures++;
        String report = "PYRO_2CLIENT " + CLIENT + " CHECK " + (valid ? "PASS" : "FAIL")
                + " phase=" + phase + " remote=" + remote + " instances=" + instances
                + " age=" + age + " dimension="
                + (mc.level == null ? "null" : mc.level.dimension().location());
        if (valid) LOGGER.info(report);
        else LOGGER.error(report);
    }
}