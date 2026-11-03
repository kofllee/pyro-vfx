package net.kofllee.pyrovfx.client.dev;

import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;
import net.kofllee.pyrovfx.api.VfxOptions;
import net.kofllee.pyrovfx.client.ClientVfxNetwork;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Lose and reacquire a real server audience, checking phase rather than restarting at age zero. */
final class VfxM7LateJoinProbe {
    private final UUID playerUuid;
    private final Vec3 origin;
    private final AtomicReference<String> failure = new AtomicReference<>();
    private volatile VfxHandle handle;
    private boolean verified;

    private VfxM7LateJoinProbe(UUID playerUuid, Vec3 origin) {
        this.playerUuid = playerUuid;
        this.origin = origin;
    }

    static VfxM7LateJoinProbe start(ResourceLocation effect, Vec3 origin) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null)
            throw new IllegalStateException("Integrated server required");
        var probe = new VfxM7LateJoinProbe(mc.player.getUUID(), origin);
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(probe.playerUuid);
                if (player == null) throw new IllegalStateException("Player missing");
                probe.handle = Vfx.play(effect, player.serverLevel(), origin,
                        VfxOptions.DEFAULT.withRadius(64).withDurationTicks(155));
                if (!probe.handle.isActive()) throw new IllegalStateException("Server start failed");
            } catch (RuntimeException ex) {
                probe.failure.set(ex.toString());
            }
        });
        return probe;
    }

    void afterTick(int index) {
        if (failure.get() != null) throw new IllegalStateException(failure.get());
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (index == 20 && ClientVfxNetwork.activeRemoteCount() != 1)
            throw new IllegalStateException("Remote source not started before audience exit");
        if (index == 25) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) player.teleportTo(origin.x + 210, origin.y, origin.z + 210);
        });
        if (index == 48 && ClientVfxNetwork.activeRemoteCount() != 0)
            throw new IllegalStateException("Leaving radius did not receive remote Stop");
        if (index == 51) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) player.teleportTo(origin.x, origin.y, origin.z);
        });
        if (index == 82) {
            if (ClientVfxNetwork.activeRemoteCount() != 1
                    || ClientVfxManager.getActiveInstanceCount() != 1
                    || ClientVfxManager.instances().getFirst().ageTicks() < 60)
                throw new IllegalStateException("Returning viewer restarted the effect rather than joining its timeline");
        }
        if (index == 86) server.execute(() -> {
            if (handle != null) handle.stop();
        });
        if (index == 102) {
            if (ClientVfxNetwork.activeRemoteCount() != 0
                    || ClientVfxManager.getActiveInstanceCount() != 0)
                throw new IllegalStateException("Late viewer Stop did not clean client state");
            verified = true;
        }
    }

    boolean verified() { return verified; }
}