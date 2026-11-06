package net.kofllee.pyrovfx.client;

import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.VfxTransformProviders;
import net.kofllee.pyrovfx.api.Vfx;
import net.kofllee.pyrovfx.network.VfxPackets;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Client-owned remote instance lookup; reuses public-handle-backed runtime and existing particle
 * backends. Network callbacks run on the Minecraft main thread via NeoForge payload registration.
 */
public final class ClientVfxNetwork {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final int SOURCE_GRACE_TICKS = 80;
    private static final Map<UUID, Remote> REMOTES = new HashMap<>();
    private static ClientLevel world;

    private ClientVfxNetwork() {}

    private static final class RemotePose implements VfxTransformProvider {
        VfxTransform previous, current, target;
        RemotePose(VfxTransform pose) { previous = current = target = pose; }
        void update(VfxTransform pose) { target = pose; }
        void tick() { previous = current; current = target; }
        @Override public VfxTransform get(float partialTick) {
            return VfxTransform.interpolate(previous, current, partialTick);
        }
    }

    private static final class Remote {
        final UUID id;
        final ResourceLocation effect;
        final Map<String, Double> parameters;
        final Deque<String> pendingEvents = new ArrayDeque<>();
        VfxPackets.Anchor anchor;
        RemotePose streamedPose;
        long handle;
        int waitTicks;
        int elapsedTicks;
        Remote(VfxPackets.Start m) {
            id = m.id(); effect = m.effect(); parameters = new HashMap<>(m.parameters()); anchor = m.anchor();
            elapsedTicks = m.elapsedTicks();
            if (anchor.kind() == VfxPackets.Anchor.STREAM) streamedPose = new RemotePose(anchor.pose());
        }
    }

    private static boolean current(ResourceLocation dimension) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.dimension().location().equals(dimension);
    }

    private static void ensureWorld() {
        ClientLevel level = Minecraft.getInstance().level;
        if (world == level) return;
        for (Remote remote : REMOTES.values())
            if (remote.handle != 0) ClientVfxManager.stopHandle(remote.handle);
        REMOTES.clear();
        world = level;
    }

    /** Retry a tracked attachment until vanilla has delivered its entity/chunk. */
    public static void tick() {
        ensureWorld();
        if (world == null) return;
        Iterator<Remote> it = REMOTES.values().iterator();
        while (it.hasNext()) {
            Remote remote = it.next();
            if (remote.streamedPose != null) remote.streamedPose.tick();
            if (remote.handle != 0) {
                if (!ClientVfxManager.handleActive(remote.handle)) it.remove();
                continue;
            }
            if (tryStart(remote)) continue;
            remote.elapsedTicks = Math.min(VfxPackets.Start.MAX_ELAPSED_TICKS,
                    remote.elapsedTicks + 1);
            if (++remote.waitTicks == 20) {
                var anchor = remote.anchor;
                Entity entity = anchor.kind() == VfxPackets.Anchor.ENTITY
                        ? world.getEntity(anchor.entityId()) : null;
                LOGGER.warn("VFX remote {} for {} unresolved after 20 ticks: kind={} entityId={} entityPresent={} uuidMatches={} socket={} socketPresent={}",
                        remote.id, remote.effect, anchor.kind(), anchor.entityId(),
                        entity != null,
                        entity != null && entity.getUUID().equals(anchor.entityUuid()),
                        anchor.socket(),
                        entity != null && (anchor.socket().isEmpty()
                                || net.kofllee.pyrovfx.client.vfx.VfxSockets.vanillaParts(entity, 1.0f)
                                        .contains(anchor.socket())));
            }
            if (remote.waitTicks > SOURCE_GRACE_TICKS) it.remove();
        }
    }

    public static void start(VfxPackets.Start message) {
        ensureWorld();
        if (!current(message.dimension()) || REMOTES.containsKey(message.id())) return;
        if (net.kofllee.pyrovfx.vfx.resource.VfxRegistry.get(message.effect()) == null) {
            LOGGER.warn("Ignoring remote VFX {}: client does not have effect definition {}",
                    message.id(), message.effect());
            return;
        }
        Remote remote = new Remote(message);
        REMOTES.put(message.id(), remote);
        tryStart(remote);
    }

    private static boolean tryStart(Remote remote) {
        VfxTransformProvider source = resolve(remote);
        if (source == null) return false;
        remote.handle = ClientVfxAccess.play(remote.effect, source, remote.parameters, remote.elapsedTicks);
        if (remote.handle == 0 && remote.waitTicks == 0)
            LOGGER.warn("VFX remote {} resolved its source but local runtime rejected effect {}",
                    remote.id, remote.effect);
        if (remote.handle != 0) {
            while (!remote.pendingEvents.isEmpty())
                ClientVfxManager.triggerHandle(remote.handle, remote.pendingEvents.removeFirst());
        }
        // The client may not have this JSON or may reject it due to budgets; do not force it.
        return remote.handle != 0;
    }

    private static VfxTransformProvider resolve(Remote remote) {
        if (world == null) return null;
        VfxPackets.Anchor anchor = remote.anchor;
        VfxTransformProvider source = switch (anchor.kind()) {
            case VfxPackets.Anchor.FIXED -> partial -> anchor.pose();
            case VfxPackets.Anchor.STREAM -> remote.streamedPose;
            case VfxPackets.Anchor.ENTITY -> {
                Entity entity = world.getEntity(anchor.entityId());
                if (entity == null || entity.isRemoved() || !entity.getUUID().equals(anchor.entityUuid()))
                    yield null;
                VfxTransformProvider provider = anchor.socket().isEmpty()
                        ? VfxTransformProviders.entity(entity) : Vfx.at(entity).socket(anchor.socket());
                yield provider.get(1.0f) == null ? null : provider;
            }
            case VfxPackets.Anchor.BLOCK -> {
                if (world.getChunkSource().getChunk(anchor.block().getX() >> 4,
                        anchor.block().getZ() >> 4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null
                        || world.getBlockState(anchor.block()).isAir()) yield null;
                yield VfxTransformProviders.block(world, anchor.block());
            }
            case VfxPackets.Anchor.BLOCK_ENTITY -> {
                if (world.getChunkSource().getChunk(anchor.block().getX() >> 4,
                        anchor.block().getZ() >> 4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) yield null;
                BlockEntity entity = world.getBlockEntity(anchor.block());
                if (entity == null || entity.isRemoved()) yield null;
                VfxTransformProvider provider = anchor.socket().isEmpty()
                        ? VfxTransformProviders.blockEntity(entity) : Vfx.at(entity).socket(anchor.socket());
                yield provider.get(1.0f) == null ? null : provider;
            }
            default -> null;
        };
        return source == null ? null : source.compose(anchor.local());
    }

    public static void stop(VfxPackets.Stop message) {
        ensureWorld();
        if (!current(message.dimension())) return;
        Remote remote = REMOTES.remove(message.id());
        if (remote != null && remote.handle != 0) ClientVfxManager.stopHandle(remote.handle);
    }

    public static void parameter(VfxPackets.Parameter message) {
        ensureWorld();
        if (!current(message.dimension()) || !Double.isFinite(message.value())) return;
        Remote remote = REMOTES.get(message.id());
        if (remote != null) {
            remote.parameters.put(message.name(), message.value());
            if (remote.handle != 0)
                ClientVfxManager.setHandleParameter(remote.handle, message.name(), message.value());
        }
    }

    public static void trigger(VfxPackets.Trigger message) {
        ensureWorld();
        if (!current(message.dimension())) return;
        Remote remote = REMOTES.get(message.id());
        if (remote != null) {
            if (remote.handle != 0) ClientVfxManager.triggerHandle(remote.handle, message.event());
            else if (remote.pendingEvents.size() < 128) remote.pendingEvents.addLast(message.event());
        }
    }

    public static void rebind(VfxPackets.Rebind message) {
        ensureWorld();
        if (!current(message.dimension())) return;
        Remote remote = REMOTES.get(message.id());
        if (remote == null) return;
        remote.anchor = message.anchor();
        remote.streamedPose = remote.anchor.kind() == VfxPackets.Anchor.STREAM
                ? new RemotePose(remote.anchor.pose()) : null;
        VfxTransformProvider provider = resolve(remote);
        if (remote.handle == 0) {
            remote.waitTicks = 0;
        } else if (provider == null || !ClientVfxManager.attachHandle(remote.handle, provider)) {
            ClientVfxManager.stopHandle(remote.handle);
            remote.handle = 0;
            remote.waitTicks = 0;
        }
    }

    /** Only streamed source positions are sent; vanilla moves entity attachments itself. */
    public static void pose(VfxPackets.Pose message) {
        ensureWorld();
        if (!current(message.dimension())) return;
        Remote remote = REMOTES.get(message.id());
        if (remote != null && remote.streamedPose != null)
            remote.streamedPose.update(message.pose());
    }

    public static int activeRemoteCount() { return REMOTES.size(); }
}
