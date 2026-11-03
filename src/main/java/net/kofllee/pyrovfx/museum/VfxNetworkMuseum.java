package net.kofllee.pyrovfx.museum;

import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.network.ServerVfxNetwork;
import net.kofllee.pyrovfx.network.VfxRemoteHandle;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Opt-in server-side museum controls for verifying remote instances on real clients. */
@EventBusSubscriber(modid = PyroVfx.MOD_ID)
public final class VfxNetworkMuseum {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private VfxNetworkMuseum() {}

    private static final class Session {
        final VfxRemoteHandle handle;
        final String kind;
        final Vec3 center;
        int ticks;
        Session(VfxRemoteHandle handle, String kind, Vec3 center) {
            this.handle = handle; this.kind = kind; this.center = center;
        }
    }

    public static int start(ServerPlayer player, String kind) {
        stop(player);
        var level = player.serverLevel();
        Vec3 center = player.position().add(0, 1.7, 0);
        ResourceLocation effect = ResourceLocation.fromNamespaceAndPath("pyro_vfx",
                switch (kind) {
                    case "entity" -> "museum/source_entity_world";
                    case "block" -> "museum/source_block";
                    case "stream" -> "museum/source_custom";
                    case "event" -> "examples/event_sequence";
                    default -> "museum/source_rotation";
                });
        Map<String, Double> params = kind.equals("event") ? Map.of() : Map.of("count", 12.0);
        VfxRemoteHandle handle = switch (kind) {
            case "entity" -> ServerVfxNetwork.play(level, effect, player, params, 72, 160);
            case "block" -> ServerVfxNetwork.play(level, effect, player.blockPosition().below(),
                    params, 72, 160);
            case "stream" -> ServerVfxNetwork.playStreamed(level, effect, VfxTransform.at(center), params, 72, 160);
            default -> ServerVfxNetwork.play(level, effect, center, params, 72, 160);
        };
        if (!handle.isActive()) {
            player.sendSystemMessage(Component.literal("Network museum: source invalid or server budget reached"));
            return 0;
        }
        SESSIONS.put(player.getUUID(), new Session(handle, kind, center));
        player.sendSystemMessage(Component.literal("Network museum: " + kind
                + " sent to nearby chunk-tracking clients. Use /pyrovfxmuseum network stop"));
        return 1;
    }

    public static int stop(ServerPlayer player) {
        Session old = SESSIONS.remove(player.getUUID());
        return old != null && old.handle.stop() ? 1 : 0;
    }

    public static int trigger(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.handle.trigger("start") ? 1 : 0;
    }

    public static int parameter(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.handle.setParameter("count", 30.0) ? 1 : 0;
    }

    public static int status(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Remote VFX leases in this dimension: "
                + ServerVfxNetwork.liveCount(player.serverLevel())));
        return 1;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        SESSIONS.entrySet().removeIf(entry -> {
            Session session = entry.getValue();
            if (!session.handle.isActive()) return true;
            session.ticks++;
            if (session.kind.equals("stream")) {
                double t = session.ticks * 0.085;
                VfxTransform transform = new VfxTransform(session.center.add(Math.sin(t) * 2, 0, Math.cos(t) * 2),
                        new Quaternionf().rotationY((float) t));
                session.handle.updatePose(transform);
            }
            return false;
        });
    }
}
