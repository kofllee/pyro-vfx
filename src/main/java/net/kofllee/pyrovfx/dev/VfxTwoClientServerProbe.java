package net.kofllee.pyrovfx.dev;

import com.mojang.logging.LogUtils;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;
import net.kofllee.pyrovfx.api.VfxOptions;
import net.kofllee.pyrovfx.network.ServerVfxNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** Test-only, localhost 2-client acceptance runner; never executes in production. */
@EventBusSubscriber(modid = PyroVfx.MOD_ID)
public final class VfxTwoClientServerProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = !FMLEnvironment.production
            && Boolean.getBoolean("pyrovfx.multiclient.server");
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath(
            "pyro_vfx", "museum/source_entity_world");
    private static final ResourceLocation STRESS_EFFECT = ResourceLocation.fromNamespaceAndPath(
            "pyro_vfx", "museum/source_custom");
    private static final Vec3 ORIGIN = new Vec3(0.5, 140, 0.5);
    private static int tick = -1;
    private static boolean finished;
    private static Pig pig;
    private static VfxHandle handle;
    private static final List<VfxHandle> LOAD = new ArrayList<>();

    private VfxTwoClientServerProbe() {}

    private static ServerPlayer a(MinecraftServer server) {
        return server.getPlayerList().getPlayerByName("PyroA");
    }

    private static ServerPlayer b(MinecraftServer server) {
        return server.getPlayerList().getPlayerByName("PyroB");
    }

    @SubscribeEvent
    public static void afterServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || finished) return;
        MinecraftServer server = event.getServer();
        var a = a(server);
        var b = b(server);
        if (tick == -1) {
            if (a == null || b == null) return;
            tick = 0;
            a.setNoGravity(true);
            b.setNoGravity(true);
            a.teleportTo(server.overworld(), ORIGIN.x, ORIGIN.y, ORIGIN.z + 4, 0, 0);
            b.teleportTo(server.overworld(), ORIGIN.x + 3, ORIGIN.y, ORIGIN.z + 4, 0, 0);
            LOGGER.info("PYRO_2CLIENT SERVER both independent connections established: {} and {}",
                    a.getGameProfile().getName(), b.getGameProfile().getName());
            phase(server, "READY");
            return;
        }
        tick++;
        if (a == null || b == null) {
            LOGGER.error("PYRO_2CLIENT SERVER FAIL: client disconnected unexpectedly at tick {}", tick);
            finished = true;
            return;
        }
        try {
            switch (tick) {
                case 30 -> {
                    spawn(server.overworld());
                    phase(server, "START");
                }
                case 60 -> {
                    b.teleportTo(server.overworld(), 220, ORIGIN.y, 220, 0, 0);
                    phase(server, "OUT");
                }
                case 95 -> {
                    b.teleportTo(server.overworld(), ORIGIN.x + 3, ORIGIN.y, ORIGIN.z + 4, 0, 0);
                    phase(server, "RETURN");
                }
                case 130 -> {
                    ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
                    if (nether == null) throw new IllegalStateException("Nether unavailable");
                    b.teleportTo(nether, 0, 80, 0, 0, 0);
                    phase(server, "DIM_OUT");
                }
                case 165 -> {
                    b.teleportTo(server.overworld(), ORIGIN.x + 3, ORIGIN.y, ORIGIN.z + 4, 0, 0);
                    phase(server, "DIM_IN");
                }
                case 200 -> {
                    if (pig != null) pig.discard();
                    phase(server, "REMOVE");
                }
                case 235 -> {
                    spawn(server.overworld());
                    phase(server, "RECREATE");
                }
                case 270 -> {
                    if (handle != null) handle.stop();
                    if (pig != null) pig.discard();
                    phase(server, "STOP");
                }
                case 280 -> {
                    for (int i = 0; i < 1_000; i++) {
                        VfxHandle effect = Vfx.play(STRESS_EFFECT, server.overworld(),
                                new Vec3(20.5, ORIGIN.y, 20.5),
                                VfxOptions.DEFAULT.withRadius(1).withDurationTicks(32));
                        if (!effect.isActive()) throw new IllegalStateException("Stress refused lease " + i);
                        LOAD.add(effect);
                    }
                    LOGGER.info("PYRO_2CLIENT SERVER 1000 stress effects active with two real clients");
                }
                case 300 -> {
                    ServerVfxNetwork.NetworkTelemetry m = ServerVfxNetwork.telemetry(server.overworld());
                    LOGGER.info("PYRO_2CLIENT SERVER stress measured: {}", m);
                    if (m == null || m.players() != 2 || m.effects() < 1_000)
                        throw new IllegalStateException("Missing real two-player load data: " + m);
                    for (VfxHandle effect : LOAD) effect.stop();
                    LOAD.clear();
                }
                case 320 -> {
                    phase(server, "DONE");
                    LOGGER.info("PYRO_2CLIENT SERVER COMPLETE: all scripted phases issued to two clients");
                    finished = true;
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("PYRO_2CLIENT SERVER FAILED at tick {}", tick, exception);
            phase(server, "FAILED");
            finished = true;
        }
    }

    private static void spawn(ServerLevel level) {
        Entity entity = EntityType.PIG.create(level);
        if (!(entity instanceof Pig fresh))
            throw new IllegalStateException("Cannot spawn pig test fixture");
        fresh.moveTo(ORIGIN.x, ORIGIN.y, ORIGIN.z);
        fresh.setNoGravity(true);
        if (!level.addFreshEntity(fresh)) throw new IllegalStateException("Pig fixture rejected");
        pig = fresh;
        handle = Vfx.play(EFFECT, Vfx.at(pig).socket("head").offset(0, 0.2, 0),
                VfxOptions.DEFAULT.withRadius(90).withDurationTicks(380));
        if (!handle.isActive()) throw new IllegalStateException("Network socket lease rejected");
    }

    private static void phase(MinecraftServer server, String name) {
        Component message = Component.literal("PYRO_2CLIENT_PHASE:" + name);
        var a = a(server);
        var b = b(server);
        if (a != null) a.sendSystemMessage(message);
        if (b != null) b.sendSystemMessage(message);
        LOGGER.info("PYRO_2CLIENT SERVER phase={} tick={}", name, tick);
    }
}