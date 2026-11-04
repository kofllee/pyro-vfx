package net.kofllee.pyrovfx.client.dev;

import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;
import net.kofllee.pyrovfx.api.VfxOptions;
import net.kofllee.pyrovfx.client.ClientVfxNetwork;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Actual integrated-server -> Minecraft client packet check for custom automatic
 * streaming. The provider is evaluated only by the logical server.
 */
final class VfxM7AutoStreamProbe {
    private final AtomicReference<String> failure = new AtomicReference<>();
    private final AtomicReference<VfxHandle> handle = new AtomicReference<>();
    private final UUID player;
    private final Vec3 origin;
    private Vec3 firstPose;
    private boolean verified;

    private VfxM7AutoStreamProbe(UUID player, Vec3 origin) {
        this.player = player;
        this.origin = origin;
    }

    static VfxM7AutoStreamProbe start(ResourceLocation effect, Vec3 origin) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null)
            throw new IllegalStateException("Integrated server required");
        var probe = new VfxM7AutoStreamProbe(minecraft.player.getUUID(), origin);
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                var owner = server.getPlayerList().getPlayer(probe.player);
                if (owner == null) throw new IllegalStateException("Missing server player");
                AtomicInteger sample = new AtomicInteger();
                VfxHandle auto = Vfx.play(effect, owner.serverLevel(), partialTick -> {
                    int tick = sample.getAndIncrement();
                    return new VfxTransform(origin.add(tick * 0.045, 0, 0),
                            new Quaternionf().rotationY(tick * 0.04f));
                }, VfxOptions.DEFAULT.withDurationTicks(120)
                        .withParameters(Map.of("stage", 0.0)));
                if (!auto.isActive()) throw new IllegalStateException("Auto callback rejected");
                probe.handle.set(auto);
            } catch (RuntimeException exception) {
                probe.failure.set("Auto stream startup: " + exception);
            }
        });
        return probe;
    }

    void afterTick(int index) {
        if (failure.get() != null) throw new IllegalStateException(failure.get());
        if (index == 16) {
            if (handle.get() == null || ClientVfxNetwork.activeRemoteCount() != 1
                    || ClientVfxManager.getActiveInstanceCount() != 1)
                throw new IllegalStateException("Auto streamed Start never arrived on client");
            firstPose = ClientVfxManager.instances().getFirst().renderTransform(1).position();
        }
        if (index == 39) {
            if (ClientVfxManager.getActiveInstanceCount() != 1
                    || ClientVfxManager.instances().getFirst().renderTransform(1).position()
                            .distanceTo(firstPose) < 0.3)
                throw new IllegalStateException("Client did not receive automatic moving poses");
        }
        if (index == 45) {
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                VfxHandle auto = handle.get();
                if (auto == null || !auto.attach(partialTick ->
                        new VfxTransform(origin.add(2, 1, 0),
                                new Quaternionf().rotationY(0.8f))))
                    failure.set("Automatic server callback rebind rejected");
            });
        }
        if (index == 62) {
            if (ClientVfxNetwork.activeRemoteCount() != 1
                    || ClientVfxManager.getActiveInstanceCount() != 1)
                throw new IllegalStateException("Auto stream rebind lost its instance");
            var pose = ClientVfxManager.instances().getFirst().renderTransform(1);
            if (pose.position().distanceTo(origin.add(2, 1, 0)) > 0.15
                    || Math.abs(pose.rotation().dot(new Quaternionf().rotationY(0.8f))) < 0.995f)
                throw new IllegalStateException("Client missed automatic callback rebind pose");
        }
        if (index == 68) {
            var server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                VfxHandle auto = handle.get();
                if (auto == null || !auto.stop()) failure.set("Auto stream Stop rejected");
            });
        }
        if (index == 85) {
            if (ClientVfxNetwork.activeRemoteCount() != 0
                    || ClientVfxManager.getActiveInstanceCount() != 0)
                throw new IllegalStateException("Auto stream Stop did not clean up client state");
            verified = true;
        }
    }

    boolean verified() { return verified; }
}