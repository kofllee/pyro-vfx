package net.kofllee.pyrovfx.client.dev;

import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;
import net.kofllee.pyrovfx.api.VfxOptions;
import net.kofllee.pyrovfx.client.ClientVfxNetwork;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.VfxSockets;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Real integrated-server packets, vanilla off-render model poses, and custom socket rebinds. */
final class VfxM7SocketProbe {
    private static final String CUSTOM = "pyro_vfx:test_muzzle";
    private static boolean registered;
    private final AtomicReference<String> error = new AtomicReference<>();
    private volatile VfxHandle handle;
    private volatile int entityId;
    private volatile UUID entityUuid;
    private final UUID playerUuid;
    private final ResourceLocation effect;
    private final BlockPos chestPos;
    private net.minecraft.world.level.block.state.BlockState previousBlock;
    private boolean verified;

    private VfxM7SocketProbe(UUID playerUuid, ResourceLocation effect, Vec3 at) {
        this.playerUuid = playerUuid;
        this.effect = effect;
        this.chestPos = BlockPos.containing(at).offset(3, 0, 0);
    }

    static VfxM7SocketProbe start(ResourceLocation effect, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null || mc.player == null)
            throw new IllegalStateException("Integrated server required for socket probe");
        if (!registered) {
            VfxSockets.registerEntity(CUSTOM,
                    (entity, partial) -> VfxTransform.at(entity.getEyePosition(partial).add(0, 0.25, 0)));
            registered = true;
        }
        var probe = new VfxM7SocketProbe(mc.player.getUUID(), effect, at);
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(probe.playerUuid);
                if (player == null) throw new IllegalStateException("No test player");
                var pig = EntityType.PIG.create(player.serverLevel());
                if (pig == null) throw new IllegalStateException("Cannot create pig");
                pig.setPos(at.x + 1, at.y, at.z + 1);
                if (!player.serverLevel().addFreshEntity(pig))
                    throw new IllegalStateException("Cannot spawn pig");
                probe.entityId = pig.getId();
                probe.entityUuid = pig.getUUID();
                probe.handle = Vfx.play(effect, Vfx.at(pig).socket("head").offset(0, 0.2, 0),
                        VfxOptions.DEFAULT.withDurationTicks(150));
                if (!probe.handle.isActive()) throw new IllegalStateException("Socket lease rejected");
            } catch (RuntimeException ex) {
                probe.error.set("Socket setup: " + ex);
            }
        });
        return probe;
    }

    void afterTick(int index) {
        if (error.get() != null) throw new IllegalStateException(error.get());
        var client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (index == 23) {
            var entity = client.level.getEntity(entityId);
            if (entity == null || !entity.getUUID().equals(entityUuid)
                    || !VfxSockets.vanillaParts(entity, 1.0f).contains("head"))
                throw new IllegalStateException("Vanilla head ModelPart was not discovered automatically");
            var expected = Vfx.at(entity).socket("head").offset(0, 0.2, 0).get(1);
            if (expected == null || expected.position().distanceToSqr(entity.position()) > 16
                    || ClientVfxNetwork.activeRemoteCount() != 1
                    || ClientVfxManager.getActiveInstanceCount() != 1
                    || !matches(ClientVfxManager.instances().getFirst().renderTransform(1), expected))
                throw new IllegalStateException("Client did not resolve the networked vanilla head socket");
        }
        if (index == 29) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerUuid);
            var pig = player == null ? null : player.serverLevel().getEntity(entityUuid);
            if (pig == null || !handle.attach(Vfx.at(pig).socket(CUSTOM).offset(0.3, 0, 0)))
                error.set("Could not rebind a client-resolved custom socket");
        });
        if (index == 44) {
            var pig = client.level.getEntity(entityId);
            var expected = pig == null ? null
                    : Vfx.at(pig).socket(CUSTOM).offset(0.3, 0, 0).get(1);
            if (expected == null || ClientVfxNetwork.activeRemoteCount() != 1
                    || ClientVfxManager.getActiveInstanceCount() != 1
                    || !matches(ClientVfxManager.instances().getFirst().renderTransform(1), expected))
                throw new IllegalStateException("Custom socket identity/rebind was not synchronized");
        }
        if (index == 52) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerUuid);
            var pig = player == null ? null : player.serverLevel().getEntity(entityUuid);
            if (pig != null) pig.discard();
        });
        if (index == 70) {
            if (ClientVfxNetwork.activeRemoteCount() != 0 || ClientVfxManager.getActiveInstanceCount() != 0)
                throw new IllegalStateException("Socket source removal failed to stop the remote effect");
        }
        if (index == 76) server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(playerUuid);
                if (player == null) throw new IllegalStateException("Missing player for chest socket");
                var level = player.serverLevel();
                previousBlock = level.getBlockState(chestPos);
                level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
                var chest = level.getBlockEntity(chestPos);
                if (chest == null) throw new IllegalStateException("No chest BlockEntity");
                handle = Vfx.play(effect, Vfx.at(chest).socket("lid").offset(0, 0.15, 0),
                        VfxOptions.DEFAULT.withDurationTicks(140));
                if (!handle.isActive()) throw new IllegalStateException("Chest lid socket lease rejected");
            } catch (RuntimeException ex) {
                error.set("Chest socket: " + ex);
            }
        });
        if (index == 103) {
            var chest = client.level.getBlockEntity(chestPos);
            var expected = chest == null ? null
                    : Vfx.at(chest).socket("lid").offset(0, 0.15, 0).get(1);
            if (chest == null || !VfxSockets.vanillaParts(chest).contains("lid")
                    || expected == null || ClientVfxNetwork.activeRemoteCount() != 1
                    || ClientVfxManager.getActiveInstanceCount() != 1
                    || !matches(ClientVfxManager.instances().getFirst().renderTransform(1), expected))
                throw new IllegalStateException("Vanilla chest lid was not network-socket resolved");
        }
        if (index == 109) server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) player.serverLevel().setBlockAndUpdate(chestPos,
                    previousBlock == null ? Blocks.AIR.defaultBlockState() : previousBlock);
        });
        if (index == 133) {
            if (ClientVfxNetwork.activeRemoteCount() != 0 || ClientVfxManager.getActiveInstanceCount() != 0)
                throw new IllegalStateException("Removed chest did not stop the network socket effect");
            verified = true;
        }
    }

    private static boolean matches(VfxTransform a, VfxTransform b) {
        return a != null && b != null && a.position().distanceToSqr(b.position()) < 0.01
                && Math.abs(a.rotation().dot(b.rotation())) > 0.999;
    }

    boolean verified() { return verified; }
}