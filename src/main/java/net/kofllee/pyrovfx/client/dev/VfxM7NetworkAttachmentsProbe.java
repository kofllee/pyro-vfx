package net.kofllee.pyrovfx.client.dev;

import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.api.VfxHandle;
import net.kofllee.pyrovfx.api.VfxOptions;
import net.kofllee.pyrovfx.client.ClientVfxNetwork;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Integrated server + real client prove entity/block IDs resolve, and source removal sends Stop. */
final class VfxM7NetworkAttachmentsProbe {
    private final AtomicReference<String> failure = new AtomicReference<>();
    private volatile VfxHandle entity, block;
    private boolean verified;
    private final Vec3 origin;
    private final UUID playerId;
    private volatile BlockState oldBlock;

    private VfxM7NetworkAttachmentsProbe(Vec3 origin, UUID playerId) {
        this.origin = origin;
        this.playerId = playerId;
    }

    static VfxM7NetworkAttachmentsProbe start(ResourceLocation effect, Vec3 origin) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null || minecraft.player == null)
            throw new IllegalStateException("Integrated server required");
        var probe = new VfxM7NetworkAttachmentsProbe(origin, minecraft.player.getUUID());
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(probe.playerId);
                if (player == null) throw new IllegalStateException("No server player");
                var level = player.serverLevel();
                var anchor = BlockPos.containing(origin);
                probe.oldBlock = level.getBlockState(anchor);
                level.setBlockAndUpdate(anchor, Blocks.STONE.defaultBlockState());
                VfxOptions options = VfxOptions.DEFAULT.withParameters(Map.of("stage", 0.0))
                        .withDurationTicks(120);
                probe.entity = Vfx.play(effect, player, options);
                probe.block = Vfx.play(effect, level, anchor, options);
                if (!probe.entity.isActive() || !probe.block.isActive())
                    throw new IllegalStateException("Server rejected entity or block source");
            } catch (RuntimeException ex) {
                probe.failure.set("Start attachments: " + ex);
            }
        });
        return probe;
    }

    void afterTick(int index) {
        if (failure.get() != null) throw new IllegalStateException(failure.get());
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (index == 22) {
            if (entity == null || block == null || ClientVfxNetwork.activeRemoteCount() != 2
                    || ClientVfxManager.getActiveInstanceCount() != 2)
                throw new IllegalStateException("Entity and block attachments not delivered to client");
            Vec3 player = Minecraft.getInstance().player.position();
            boolean foundPlayer = ClientVfxManager.instances().stream()
                    .anyMatch(instance -> instance.renderTransform(1).position().distanceToSqr(player) < 1);
            boolean foundBlock = ClientVfxManager.instances().stream()
                    .anyMatch(instance -> instance.renderTransform(1).position().distanceToSqr(
                            Vec3.atCenterOf(BlockPos.containing(origin))) < 0.1);
            if (!foundPlayer || !foundBlock)
                throw new IllegalStateException("Client resolved incorrect entity/block transform");
        }
        if (index == 26) {
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player != null)
                    player.serverLevel().setBlockAndUpdate(BlockPos.containing(origin), Blocks.AIR.defaultBlockState());
            });
        }
        if (index == 37) {
            if (ClientVfxNetwork.activeRemoteCount() != 1
                    || ClientVfxManager.getActiveInstanceCount() != 1)
                throw new IllegalStateException("Destroyed block attachment did not stop on client");
        }
        if (index == 40) {
            server.execute(() -> {
                if (entity == null || !entity.stop() || entity.isActive())
                    failure.set("Entity attachment stop failed on server");
                var player = server.getPlayerList().getPlayer(playerId);
                if (player != null && oldBlock != null)
                    player.serverLevel().setBlockAndUpdate(BlockPos.containing(origin), oldBlock);
            });
        }
        if (index == 54) {
            if (ClientVfxNetwork.activeRemoteCount() != 0
                    || ClientVfxManager.getActiveInstanceCount() != 0)
                throw new IllegalStateException("Entity source stop did not clean client state");
            verified = true;
        }
    }

    boolean verified() { return verified; }
}
