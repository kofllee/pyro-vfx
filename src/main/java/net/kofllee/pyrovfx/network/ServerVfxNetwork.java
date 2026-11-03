package net.kofllee.pyrovfx.network;

import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server knows only effect commands, source identity and interested players.
 * No definition registry, particle simulation, client class or Java Provider crosses the wire.
 */
@EventBusSubscriber(modid = PyroVfx.MOD_ID)
public final class ServerVfxNetwork {
    private static final int MAX_LIVE = 4096;
    private static final double MAX_RADIUS = 256;
    private static final Map<ServerLevel, Map<UUID, Remote>> LEVELS = new IdentityHashMap<>();
    private ServerVfxNetwork() {}

    private static class Source {
        final byte kind;
        final UUID entityUuid;
        final int entityId;
        final BlockPos blockPos;
        final Block originalBlock;
        final WeakReference<BlockEntity> originalBlockEntity;
        VfxTransform pose;

        private Source(byte kind, int entityId, UUID entityUuid, BlockPos blockPos,
                       Block originalBlock, BlockEntity blockEntity, VfxTransform pose) {
            this.kind = kind; this.entityId = entityId; this.entityUuid = entityUuid;
            this.blockPos = blockPos; this.originalBlock = originalBlock;
            this.originalBlockEntity = blockEntity == null ? null : new WeakReference<>(blockEntity);
            this.pose = pose;
        }

        static Source fixed(VfxTransform pose, boolean streamed) {
            return new Source(streamed ? VfxPackets.Anchor.STREAM : VfxPackets.Anchor.FIXED,
                    0, null, null, null, null, pose);
        }

        static Source entity(Entity entity) {
            return new Source(VfxPackets.Anchor.ENTITY, entity.getId(), entity.getUUID(),
                    null, null, null, entityPose(entity));
        }

        static Source block(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
            BlockPos immutable = pos.immutable();
            return new Source(blockEntity == null ? VfxPackets.Anchor.BLOCK : VfxPackets.Anchor.BLOCK_ENTITY,
                    0, null, immutable, level.getBlockState(immutable).getBlock(), blockEntity,
                    VfxTransform.at(Vec3.atCenterOf(immutable)));
        }

        /** Only resolve loaded sources; never force chunks or retain removed entities. */
        VfxTransform resolve(ServerLevel level) {
            if (kind == VfxPackets.Anchor.FIXED || kind == VfxPackets.Anchor.STREAM) return pose;
            if (kind == VfxPackets.Anchor.ENTITY) {
                Entity entity = level.getEntity(entityUuid);
                return entity == null || entity.isRemoved() || entity.getId() != entityId
                        ? null : entityPose(entity);
            }
            if (!level.hasChunkAt(blockPos)
                    || level.getBlockState(blockPos).isAir()
                    || level.getBlockState(blockPos).getBlock() != originalBlock) return null;
            if (kind == VfxPackets.Anchor.BLOCK_ENTITY) {
                BlockEntity original = originalBlockEntity.get();
                if (original == null || original.isRemoved() || level.getBlockEntity(blockPos) != original)
                    return null;
            }
            return pose;
        }

        VfxPackets.Anchor anchor(VfxTransform resolved) {
            return switch (kind) {
                case VfxPackets.Anchor.ENTITY -> VfxPackets.Anchor.entity(entityId, entityUuid, resolved);
                case VfxPackets.Anchor.BLOCK -> VfxPackets.Anchor.block(blockPos, false, resolved);
                case VfxPackets.Anchor.BLOCK_ENTITY -> VfxPackets.Anchor.block(blockPos, true, resolved);
                case VfxPackets.Anchor.STREAM -> VfxPackets.Anchor.stream(resolved);
                default -> VfxPackets.Anchor.fixed(resolved);
            };
        }

        private static VfxTransform entityPose(Entity e) {
            return new VfxTransform(e.position(), new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(-e.getYRot()), (float) Math.toRadians(e.getXRot()), 0));
        }
    }

    private static final class Remote {
        final UUID id = UUID.randomUUID();
        final ResourceLocation effect;
        final double radiusSq;
        final int maxTicks;
        final Map<String, Double> parameters;
        final Set<UUID> viewers = new HashSet<>();
        Source source;
        int ticks;
        int lastPoseTick;
        VfxTransform lastSentPose;

        Remote(ResourceLocation effect, Source source, Map<String, Double> parameters,
               double radius, int maxTicks) {
            this.effect = effect; this.source = source;
            this.parameters = new HashMap<>(parameters);
            this.radiusSq = radius * radius;
            this.maxTicks = maxTicks;
        }
    }

    private static boolean serverThread(ServerLevel level) {
        return level != null && level.getServer().isSameThread();
    }

    /** A duration of -1 is explicitly indefinite and must be stopped by the caller. */
    private static VfxRemoteHandle create(ServerLevel level, ResourceLocation effect, Source source,
                                          Map<String, Double> parameters, double radius, int maxTicks) {
        if (!serverThread(level) || effect == null || source == null || parameters == null
                || !Double.isFinite(radius) || radius <= 0 || radius > MAX_RADIUS
                || (maxTicks != -1 && (maxTicks < 1 || maxTicks > 20 * 60 * 10))
                || parameters.size() > 32) return VfxRemoteHandle.inactive();
        for (var entry : parameters.entrySet()) {
            if (!validName(entry.getKey()) || entry.getValue() == null
                    || !Double.isFinite(entry.getValue())) return VfxRemoteHandle.inactive();
        }
        if (source.resolve(level) == null) return VfxRemoteHandle.inactive();
        var remotes = LEVELS.computeIfAbsent(level, ignored -> new HashMap<>());
        if (remotes.size() >= MAX_LIVE) return VfxRemoteHandle.inactive();
        Remote remote = new Remote(effect, source, parameters, radius, maxTicks);
        remotes.put(remote.id, remote);
        refresh(level, remote);
        return new VfxRemoteHandle(level, remote.id);
    }

    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation effect, Vec3 position,
                                       Map<String, Double> parameters, double radius, int maxTicks) {
        if (position == null) return VfxRemoteHandle.inactive();
        return play(level, effect, VfxTransform.at(position), parameters, radius, maxTicks);
    }

    /** Static position and rotation in world coordinates. */
    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation effect, VfxTransform pose,
                                       Map<String, Double> parameters, double radius, int maxTicks) {
        return pose == null ? VfxRemoteHandle.inactive()
                : create(level, effect, Source.fixed(pose, false), parameters, radius, maxTicks);
    }

    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation effect, Entity entity,
                                       Map<String, Double> parameters, double radius, int maxTicks) {
        if (entity == null || entity.level() != level) return VfxRemoteHandle.inactive();
        return create(level, effect, Source.entity(entity), parameters, radius, maxTicks);
    }

    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation effect, BlockPos pos,
                                       Map<String, Double> parameters, double radius, int maxTicks) {
        if (level == null || pos == null || !level.hasChunkAt(pos) || level.getBlockState(pos).isAir())
            return VfxRemoteHandle.inactive();
        return create(level, effect, Source.block(level, pos, null), parameters, radius, maxTicks);
    }

    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation effect, BlockEntity block,
                                       Map<String, Double> parameters, double radius, int maxTicks) {
        if (block == null || block.isRemoved() || block.getLevel() != level)
            return VfxRemoteHandle.inactive();
        return create(level, effect, Source.block(level, block.getBlockPos(), block), parameters, radius, maxTicks);
    }

    /**
     * Controlled custom source synchronization. The creator supplies snapshots explicitly;
     * Java lambdas and model-part callbacks never leave their own side.
     */
    public static VfxRemoteHandle playStreamed(ServerLevel level, ResourceLocation effect, VfxTransform initial,
                                               Map<String, Double> parameters, double radius, int maxTicks) {
        return initial == null ? VfxRemoteHandle.inactive()
                : create(level, effect, Source.fixed(initial, true), parameters, radius, maxTicks);
    }

    private static boolean validName(String name) { return name != null && !name.isBlank() && name.length() <= 64; }

    private static Remote find(ServerLevel level, UUID id) {
        if (!serverThread(level) || id == null) return null;
        Map<UUID, Remote> instances = LEVELS.get(level);
        return instances == null ? null : instances.get(id);
    }

    public static boolean active(ServerLevel level, UUID id) { return find(level, id) != null; }

    public static boolean stop(ServerLevel level, UUID id) {
        Remote remote = find(level, id);
        if (remote == null) return false;
        LEVELS.get(level).remove(id);
        stopViewers(level, remote);
        return true;
    }

    public static boolean parameter(ServerLevel level, UUID id, String name, double value) {
        Remote remote = find(level, id);
        if (remote == null || !validName(name) || !Double.isFinite(value)) return false;
        // Server has no client JSON registry. Clients validate the parameter against their definition.
        if (!remote.parameters.containsKey(name) && remote.parameters.size() >= 32) return false;
        remote.parameters.put(name, value);
        send(level, remote, new VfxPackets.Parameter(id, level.dimension().location(), name, value));
        return true;
    }

    public static boolean trigger(ServerLevel level, UUID id, String event) {
        Remote remote = find(level, id);
        if (remote == null || !validName(event)) return false;
        send(level, remote, new VfxPackets.Trigger(id, level.dimension().location(), event));
        return true;
    }

    public static boolean attach(ServerLevel level, UUID id, Entity entity) {
        return entity != null && entity.level() == level && rebind(level, id, Source.entity(entity));
    }

    public static boolean attach(ServerLevel level, UUID id, BlockPos pos) {
        return level != null && pos != null && level.hasChunkAt(pos) && !level.getBlockState(pos).isAir()
                && rebind(level, id, Source.block(level, pos, null));
    }

    public static boolean attach(ServerLevel level, UUID id, BlockEntity block) {
        return block != null && !block.isRemoved() && block.getLevel() == level
                && rebind(level, id, Source.block(level, block.getBlockPos(), block));
    }

    public static boolean attach(ServerLevel level, UUID id, VfxTransform pose) {
        return pose != null && rebind(level, id, Source.fixed(pose, false));
    }

    public static boolean attachStreamed(ServerLevel level, UUID id, VfxTransform pose) {
        return pose != null && rebind(level, id, Source.fixed(pose, true));
    }

    /** Freeze the last resolved source pose without stopping the effect. */
    public static boolean detach(ServerLevel level, UUID id) {
        Remote remote = find(level, id);
        if (remote == null) return false;
        VfxTransform pose = remote.source.resolve(level);
        return pose != null && rebind(level, id, Source.fixed(pose, false));
    }

    public static boolean stream(ServerLevel level, UUID id, VfxTransform pose) {
        Remote remote = find(level, id);
        if (remote == null || pose == null || remote.source.kind != VfxPackets.Anchor.STREAM) return false;
        remote.source.pose = pose;
        // One pose packet per two server ticks at most. Only send meaningful changes.
        if (remote.ticks - remote.lastPoseTick < 2) return true;
        if (remote.lastSentPose != null && remote.lastSentPose.position().distanceToSqr(pose.position()) < 0.0001
                && Math.abs(remote.lastSentPose.rotation().dot(pose.rotation())) > 0.9999f) return true;
        remote.lastPoseTick = remote.ticks;
        remote.lastSentPose = pose;
        refresh(level, remote);
        send(level, remote, new VfxPackets.Pose(id, level.dimension().location(), pose));
        return true;
    }

    private static boolean rebind(ServerLevel level, UUID id, Source next) {
        Remote remote = find(level, id);
        if (remote == null) return false;
        VfxTransform pose = next.resolve(level);
        if (pose == null) return false;
        remote.source = next;
        refresh(level, remote);
        send(level, remote, new VfxPackets.Rebind(id, level.dimension().location(), next.anchor(pose)));
        return true;
    }

    private static void send(ServerLevel level, Remote remote, CustomPacketPayload payload) {
        for (UUID uuid : remote.viewers) {
            ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(uuid);
            if (viewer != null && viewer.serverLevel() == level)
                PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    private static void stopViewers(ServerLevel level, Remote remote) {
        send(level, remote, new VfxPackets.Stop(remote.id, level.dimension().location()));
        remote.viewers.clear();
    }

    /** Called on creation, on movement, and each server tick. No per-particle traffic. */
    private static void refresh(ServerLevel level, Remote remote) {
        VfxTransform pose = remote.source.resolve(level);
        if (pose == null) return;
        BlockPos pos = BlockPos.containing(pose.position());
        Set<UUID> interested = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isRemoved() || player.position().distanceToSqr(pose.position()) > remote.radiusSq
                    || !player.getChunkTrackingView().contains(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            interested.add(player.getUUID());
            if (remote.viewers.add(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new VfxPackets.Start(remote.id,
                        level.dimension().location(), remote.effect, remote.source.anchor(pose),
                        Map.copyOf(remote.parameters)));
            }
        }
        for (UUID past : new ArrayList<>(remote.viewers)) {
            if (interested.contains(past)) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(past);
            if (player != null && player.serverLevel() == level)
                PacketDistributor.sendToPlayer(player, new VfxPackets.Stop(remote.id, level.dimension().location()));
            remote.viewers.remove(past);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var entries = LEVELS.get(level);
            if (entries == null || entries.isEmpty()) continue;
            for (Remote remote : new ArrayList<>(entries.values())) {
                if (!entries.containsKey(remote.id)) continue;
                if ((remote.maxTicks != -1 && ++remote.ticks >= remote.maxTicks)
                        || remote.source.resolve(level) == null) {
                    stop(level, remote.id);
                } else {
                    if (remote.maxTicks == -1) remote.ticks++;
                    refresh(level, remote);
                }
            }
        }
    }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var entries = LEVELS.remove(level);
        if (entries != null) for (Remote remote : entries.values()) stopViewers(level, remote);
    }

    public static int liveCount(ServerLevel level) {
        return serverThread(level) && LEVELS.containsKey(level) ? LEVELS.get(level).size() : 0;
    }
}
