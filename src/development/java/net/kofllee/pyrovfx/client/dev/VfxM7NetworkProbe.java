package net.kofllee.pyrovfx.client.dev;

import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;
import net.kofllee.pyrovfx.api.VfxOptions;
import net.kofllee.pyrovfx.client.ClientVfxNetwork;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes real NeoForge clientbound packet handling on an integrated server.
 * Dedicated server round-trip and source codecs are checked by GameTests.
 */
final class VfxM7NetworkProbe {
    private final UUID player;
    private final ResourceLocation effect;
    private final Vec3 origin;
    private final AtomicReference<VfxHandle> remote = new AtomicReference<>();
    private final AtomicReference<String> serverFailure = new AtomicReference<>();
    private boolean verified;

    private VfxM7NetworkProbe(UUID player, ResourceLocation effect, Vec3 origin) {
        this.player = player;
        this.effect = effect;
        this.origin = origin;
    }

    static VfxM7NetworkProbe start(ResourceLocation effect, Vec3 origin) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("Integrated server required for network probe");
        var probe = new VfxM7NetworkProbe(mc.player.getUUID(), effect, origin);
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(probe.player);
                if (player == null) throw new IllegalStateException("No server player");
                VfxHandle handle = Vfx.play(effect, player.serverLevel(), origin,
                        VfxOptions.DEFAULT.withParameters(Map.of("stage", 0.0)).withDurationTicks(120));
                if (!handle.isActive()) throw new IllegalStateException("Remote start rejected");
                probe.remote.set(handle);
            } catch (RuntimeException exception) {
                probe.serverFailure.set("Remote start: " + exception);
            }
        });
        return probe;
    }

    void afterTick(int index) {
        if (serverFailure.get() != null)
            throw new IllegalStateException(serverFailure.get());
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (index == 18) {
            if (remote.get() == null || ClientVfxNetwork.activeRemoteCount() != 1
                    || ClientVfxManager.getActiveInstanceCount() != 1
                    || ClientVfxManager.instances().getFirst().parameters().getOrDefault("stage", -1.0) != 2.0)
                throw new IllegalStateException("Remote start/initial event not received by the real client");
        }
        if (index == 23) {
            server.execute(() -> {
                VfxHandle handle = remote.get();
                if (handle == null || !handle.setParameter("stage", 7.0) || !handle.trigger("increment"))
                    serverFailure.set("Remote parameter or event rejected on server thread");
            });
        }
        if (index == 35) {
            if (ClientVfxManager.getActiveInstanceCount() != 1
                    || ClientVfxManager.instances().getFirst().parameters().getOrDefault("stage", -1.0) != 8.0)
                throw new IllegalStateException("Remote parameter/event update was not applied on client tick");
        }
        if (index == 40) {
            server.execute(() -> {
                VfxHandle handle = remote.get();
                if (handle == null || !handle.stop() || handle.stop() || handle.isActive())
                    serverFailure.set("Remote stop was not idempotent");
            });
        }
        if (index == 55) {
            if (ClientVfxNetwork.activeRemoteCount() != 0
                    || ClientVfxManager.getActiveInstanceCount() != 0)
                throw new IllegalStateException("Remote stop not delivered, client effect leaked");
            if (remote.get() == null)
                throw new IllegalStateException("Server remote handle was not created");
            verified = true;
        }
    }

    boolean verified() { return verified; }
}
