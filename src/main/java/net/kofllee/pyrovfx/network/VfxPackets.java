package net.kofllee.pyrovfx.network;

import net.kofllee.pyrovfx.client.ClientVfxNetwork;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Versioned clientbound commands, never particle state or Java transform callbacks. */
public final class VfxPackets {
    private static final ResourceLocation NAMESPACE = ResourceLocation.fromNamespaceAndPath("pyro_vfx", "network");
    private VfxPackets() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // Protocol 3 adds client-resolved named sockets and server elapsed age.
        var packets = event.registrar("3");
        packets.playToClient(Start.TYPE, Start.CODEC, (message, context) -> ClientHandler.start(message));
        packets.playToClient(Stop.TYPE, Stop.CODEC, (message, context) -> ClientHandler.stop(message));
        packets.playToClient(Parameter.TYPE, Parameter.CODEC, (message, context) -> ClientHandler.parameter(message));
        packets.playToClient(Trigger.TYPE, Trigger.CODEC, (message, context) -> ClientHandler.trigger(message));
        packets.playToClient(Rebind.TYPE, Rebind.CODEC, (message, context) -> ClientHandler.rebind(message));
        packets.playToClient(Pose.TYPE, Pose.CODEC, (message, context) -> ClientHandler.pose(message));
    }

    /** Source identity is explicit, not a serialized Strategy. */
    public record Anchor(byte kind, int entityId, UUID entityUuid, BlockPos block,
                         VfxTransform pose, VfxTransform local, String socket) {
        public static final byte FIXED = 0, ENTITY = 1, BLOCK = 2, BLOCK_ENTITY = 3, STREAM = 4;
        private static final VfxTransform IDENTITY = VfxTransform.at(Vec3.ZERO);
        public Anchor {
            if (kind < FIXED || kind > STREAM || entityUuid == null || block == null
                    || pose == null || local == null || socket == null || socket.length() > 128
                    || (!socket.isEmpty() && (kind != ENTITY && kind != BLOCK_ENTITY))
                    || (!socket.isEmpty() && !socket.matches("[a-z0-9_.:-]+(?:/[a-z0-9_.:-]+)*")))
                throw new IllegalArgumentException("Invalid network VFX anchor");
        }
        public static Anchor fixed(VfxTransform pose) {
            return new Anchor(FIXED, 0, new UUID(0, 0), BlockPos.ZERO, pose, IDENTITY, "");
        }
        public static Anchor stream(VfxTransform pose) {
            return new Anchor(STREAM, 0, new UUID(0, 0), BlockPos.ZERO, pose, IDENTITY, "");
        }
        public static Anchor entity(int id, UUID uuid, VfxTransform pose) {
            return entity(id, uuid, pose, IDENTITY);
        }
        public static Anchor entity(int id, UUID uuid, VfxTransform pose, VfxTransform local) {
            return entity(id, uuid, pose, local, "");
        }
        public static Anchor entity(int id, UUID uuid, VfxTransform pose,
                                    VfxTransform local, String socket) {
            return new Anchor(ENTITY, id, uuid, BlockPos.ZERO, pose, local, socket);
        }
        public static Anchor block(BlockPos pos, boolean blockEntity, VfxTransform pose) {
            return block(pos, blockEntity, pose, IDENTITY);
        }
        public static Anchor block(BlockPos pos, boolean blockEntity,
                                   VfxTransform pose, VfxTransform local) {
            return block(pos, blockEntity, pose, local, "");
        }
        public static Anchor block(BlockPos pos, boolean blockEntity,
                                   VfxTransform pose, VfxTransform local, String socket) {
            return new Anchor(blockEntity ? BLOCK_ENTITY : BLOCK, 0,
                    new UUID(0, 0), pos.immutable(), pose, local, socket);
        }
    }

    static void writeAnchor(FriendlyByteBuf b, Anchor a) {
        b.writeByte(a.kind());
        switch (a.kind()) {
            case Anchor.ENTITY -> { b.writeVarInt(a.entityId()); b.writeUUID(a.entityUuid()); }
            case Anchor.BLOCK, Anchor.BLOCK_ENTITY -> b.writeBlockPos(a.block());
            default -> {}
        }
        writeTransform(b, a.pose());
        writeTransform(b, a.local());
        b.writeUtf(a.socket(), 128);
    }

    static Anchor readAnchor(FriendlyByteBuf b) {
        byte kind = b.readByte();
        int id = 0;
        UUID uuid = new UUID(0, 0);
        BlockPos pos = BlockPos.ZERO;
        switch (kind) {
            case Anchor.ENTITY -> { id = b.readVarInt(); uuid = b.readUUID(); }
            case Anchor.BLOCK, Anchor.BLOCK_ENTITY -> pos = b.readBlockPos();
            case Anchor.FIXED, Anchor.STREAM -> {}
            default -> throw new IllegalArgumentException("Unknown VFX network anchor " + kind);
        }
        return new Anchor(kind, id, uuid, pos, readTransform(b), readTransform(b), b.readUtf(128));
    }

    static void writeTransform(FriendlyByteBuf b, VfxTransform t) {
        Vec3 p = t.position();
        Quaternionf q = t.rotation();
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
        b.writeFloat(q.x); b.writeFloat(q.y); b.writeFloat(q.z); b.writeFloat(q.w);
    }

    static VfxTransform readTransform(FriendlyByteBuf b) {
        Vec3 p = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        Quaternionf q = new Quaternionf(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat());
        return new VfxTransform(p, q);
    }

    static void writeParameters(FriendlyByteBuf b, Map<String, Double> parameters) {
        if (parameters.size() > 32) throw new IllegalArgumentException("Too many VFX parameters");
        b.writeVarInt(parameters.size());
        for (var entry : parameters.entrySet()) {
            b.writeUtf(entry.getKey(), 64);
            b.writeDouble(entry.getValue());
        }
    }

    static Map<String, Double> readParameters(FriendlyByteBuf b) {
        int size = b.readVarInt();
        if (size < 0 || size > 32) throw new IllegalArgumentException("Invalid VFX parameter count");
        Map<String, Double> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String name = b.readUtf(64);
            double value = b.readDouble();
            if (!Double.isFinite(value) || values.putIfAbsent(name, value) != null)
                throw new IllegalArgumentException("Invalid VFX parameter");
        }
        return Map.copyOf(values);
    }

    public record Start(UUID id, ResourceLocation dimension, ResourceLocation effect, Anchor anchor,
                        Map<String, Double> parameters, int elapsedTicks) implements CustomPacketPayload {
        // The server lease may be indefinite. Do not cap a late observer's phase
        // at the 10-minute maximum for *finite* leases.
        public static final int MAX_ELAPSED_TICKS = 2_000_000_000;
        public Start {
            if (elapsedTicks < 0 || elapsedTicks > MAX_ELAPSED_TICKS)
                throw new IllegalArgumentException("Invalid remote VFX elapsed ticks");
        }
        public Start(UUID id, ResourceLocation dimension, ResourceLocation effect, Anchor anchor,
                     Map<String, Double> parameters) {
            this(id, dimension, effect, anchor, parameters, 0);
        }
        public static final Type<Start> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pyro_vfx", "start"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = StreamCodec.of(
                (b, v) -> { b.writeUUID(v.id); b.writeResourceLocation(v.dimension); b.writeResourceLocation(v.effect);
                            writeAnchor(b, v.anchor); writeParameters(b, v.parameters); b.writeVarInt(v.elapsedTicks); },
                b -> new Start(b.readUUID(), b.readResourceLocation(), b.readResourceLocation(),
                        readAnchor(b), readParameters(b), b.readVarInt()));
        @Override public Type<Start> type() { return TYPE; }
    }

    public record Stop(UUID id, ResourceLocation dimension) implements CustomPacketPayload {
        public static final Type<Stop> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pyro_vfx", "stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Stop> CODEC = StreamCodec.of(
                (b, v) -> { b.writeUUID(v.id); b.writeResourceLocation(v.dimension); },
                b -> new Stop(b.readUUID(), b.readResourceLocation()));
        @Override public Type<Stop> type() { return TYPE; }
    }

    public record Parameter(UUID id, ResourceLocation dimension, String name, double value) implements CustomPacketPayload {
        public static final Type<Parameter> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pyro_vfx", "parameter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Parameter> CODEC = StreamCodec.of(
                (b, v) -> { b.writeUUID(v.id); b.writeResourceLocation(v.dimension); b.writeUtf(v.name, 64); b.writeDouble(v.value); },
                b -> new Parameter(b.readUUID(), b.readResourceLocation(), b.readUtf(64), b.readDouble()));
        @Override public Type<Parameter> type() { return TYPE; }
    }

    public record Trigger(UUID id, ResourceLocation dimension, String event) implements CustomPacketPayload {
        public static final Type<Trigger> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pyro_vfx", "trigger"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Trigger> CODEC = StreamCodec.of(
                (b, v) -> { b.writeUUID(v.id); b.writeResourceLocation(v.dimension); b.writeUtf(v.event, 64); },
                b -> new Trigger(b.readUUID(), b.readResourceLocation(), b.readUtf(64)));
        @Override public Type<Trigger> type() { return TYPE; }
    }

    public record Rebind(UUID id, ResourceLocation dimension, Anchor anchor) implements CustomPacketPayload {
        public static final Type<Rebind> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pyro_vfx", "rebind"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rebind> CODEC = StreamCodec.of(
                (b, v) -> { b.writeUUID(v.id); b.writeResourceLocation(v.dimension); writeAnchor(b, v.anchor); },
                b -> new Rebind(b.readUUID(), b.readResourceLocation(), readAnchor(b)));
        @Override public Type<Rebind> type() { return TYPE; }
    }

    public record Pose(UUID id, ResourceLocation dimension, VfxTransform pose) implements CustomPacketPayload {
        public static final Type<Pose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pyro_vfx", "pose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pose> CODEC = StreamCodec.of(
                (b, v) -> { b.writeUUID(v.id); b.writeResourceLocation(v.dimension); writeTransform(b, v.pose); },
                b -> new Pose(b.readUUID(), b.readResourceLocation(), readTransform(b)));
        @Override public Type<Pose> type() { return TYPE; }
    }

    /** This class is resolved only by clientbound callbacks, never by the dedicated server. */
    private static final class ClientHandler {
        static void start(Start m) { ClientVfxNetwork.start(m); }
        static void stop(Stop m) { ClientVfxNetwork.stop(m); }
        static void parameter(Parameter m) { ClientVfxNetwork.parameter(m); }
        static void trigger(Trigger m) { ClientVfxNetwork.trigger(m); }
        static void rebind(Rebind m) { ClientVfxNetwork.rebind(m); }
        static void pose(Pose m) { ClientVfxNetwork.pose(m); }
    }
}
