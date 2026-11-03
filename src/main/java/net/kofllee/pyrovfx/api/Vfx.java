package net.kofllee.pyrovfx.api;

import net.kofllee.pyrovfx.client.ClientVfxAccess;
import net.kofllee.pyrovfx.network.ServerVfxNetwork;
import net.kofllee.pyrovfx.network.VfxRemoteHandle;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.joml.Quaternionf;

import java.util.Map;

/**
 * Public playback entry point. World-aware overloads dispatch by logical side:
 * server calls broadcast to tracking players; client calls play locally.
 * Position-only and Java Transform Provider overloads remain explicitly client-local.
 */
public final class Vfx {
    private static final VfxTransformProvider UNAVAILABLE = partialTick -> null;

    private Vfx() {}

    /** Client-local provider for composing offsets/rotations; not network serializable. */
    public static VfxTransformProvider at(Entity entity) {
        return available(client() ? ClientVfxAccess.sourceEntity(entity) : null);
    }

    /** Creates a source at the center of a client block, with world-aligned axes. */
    public static VfxTransformProvider at(BlockPos block) {
        return available(client() ? ClientVfxAccess.sourceBlock(block) : null);
    }

    /** Creates a tracking source for a client block entity. */
    public static VfxTransformProvider at(BlockEntity blockEntity) {
        return available(client() ? ClientVfxAccess.sourceBlockEntity(blockEntity) : null);
    }

    public static VfxHandle play(ResourceLocation id, Vec3 worldPosition) {
        return play(id, VfxTransformProvider.fixed(worldPosition));
    }

    public static VfxHandle play(ResourceLocation id, Vec3 worldPosition, Map<String, Double> parameters) {
        return play(id, VfxTransformProvider.fixed(worldPosition), parameters);
    }

    /** Fixed world position with a rotation applied to emitter-local offsets. */
    public static VfxHandle play(ResourceLocation id, Vec3 worldPosition, Quaternionf rotation) {
        return play(id, worldPosition, rotation, Map.of());
    }

    /** Fixed world position and rotation with effect parameters. */
    public static VfxHandle play(ResourceLocation id, Vec3 worldPosition, Quaternionf rotation,
                                 Map<String, Double> parameters) {
        VfxTransform pose = new VfxTransform(worldPosition, rotation);
        return play(id, partialTick -> pose, parameters);
    }

    public static VfxHandle play(ResourceLocation id, VfxTransformProvider source) {
        return play(id, source, Map.of());
    }

    public static VfxHandle play(ResourceLocation id, VfxTransformProvider source, Map<String, Double> parameters) {
        return new VfxHandle(client() ? ClientVfxAccess.play(id, source, parameters) : 0);
    }

    public static VfxHandle play(ResourceLocation id, Entity entity) {
        return play(id, entity, VfxOptions.DEFAULT);
    }

    public static VfxHandle play(ResourceLocation id, Entity entity, Map<String, Double> parameters) {
        return play(id, entity, VfxOptions.DEFAULT.withParameters(parameters));
    }

    /** Automatically broadcast on the server or play locally on the client. */
    public static VfxHandle play(ResourceLocation id, Entity entity, VfxOptions options) {
        if (entity == null || options == null || entity.isRemoved()) return VfxHandle.inactive();
        Level level = entity.level();
        if (level instanceof ServerLevel server) {
            return new VfxHandle(ServerVfxNetwork.play(server, id, entity,
                    options.parameters(), options.radius(), options.durationTicks()));
        }
        return clientWorld(level) ? play(id, at(entity), options.parameters()) : VfxHandle.inactive();
    }

    public static VfxHandle play(ResourceLocation id, BlockPos block) {
        return play(id, block, Map.of());
    }

    public static VfxHandle play(ResourceLocation id, BlockPos block, Map<String, Double> parameters) {
        return play(id, at(block), parameters);
    }

    public static VfxHandle play(ResourceLocation id, BlockEntity blockEntity) {
        return play(id, blockEntity, VfxOptions.DEFAULT);
    }

    public static VfxHandle play(ResourceLocation id, BlockEntity blockEntity, Map<String, Double> parameters) {
        return play(id, blockEntity, VfxOptions.DEFAULT.withParameters(parameters));
    }

    public static VfxHandle play(ResourceLocation id, BlockEntity blockEntity, VfxOptions options) {
        if (blockEntity == null || blockEntity.isRemoved() || options == null)
            return VfxHandle.inactive();
        Level level = blockEntity.getLevel();
        if (level instanceof ServerLevel server) {
            return new VfxHandle(ServerVfxNetwork.play(server, id, blockEntity,
                    options.parameters(), options.radius(), options.durationTicks()));
        }
        return clientWorld(level) ? play(id, at(blockEntity), options.parameters()) : VfxHandle.inactive();
    }

    /** A fixed world-space position: a Level is needed to identify its dimension. */
    public static VfxHandle play(ResourceLocation id, Level level, Vec3 position) {
        return play(id, level, position, VfxOptions.DEFAULT);
    }

    public static VfxHandle play(ResourceLocation id, Level level, Vec3 position,
                                 Map<String, Double> parameters) {
        return play(id, level, position, VfxOptions.DEFAULT.withParameters(parameters));
    }

    public static VfxHandle play(ResourceLocation id, Level level, Vec3 position, VfxOptions options) {
        if (position == null) return VfxHandle.inactive();
        return play(id, level, VfxTransform.at(position), options);
    }

    public static VfxHandle play(ResourceLocation id, Level level, VfxTransform pose) {
        return play(id, level, pose, VfxOptions.DEFAULT);
    }

    public static VfxHandle play(ResourceLocation id, Level level, VfxTransform pose, VfxOptions options) {
        if (pose == null || options == null) return VfxHandle.inactive();
        if (level instanceof ServerLevel server) {
            return new VfxHandle(ServerVfxNetwork.play(server, id, pose,
                    options.parameters(), options.radius(), options.durationTicks()));
        }
        return clientWorld(level) ? play(id, partialTick -> pose, options.parameters()) : VfxHandle.inactive();
    }

    /** A block position represents an attachment, unlike the fixed Vec3 overload. */
    public static VfxHandle play(ResourceLocation id, Level level, BlockPos pos) {
        return play(id, level, pos, VfxOptions.DEFAULT);
    }

    public static VfxHandle play(ResourceLocation id, Level level, BlockPos pos,
                                 Map<String, Double> parameters) {
        return play(id, level, pos, VfxOptions.DEFAULT.withParameters(parameters));
    }

    public static VfxHandle play(ResourceLocation id, Level level, BlockPos pos, VfxOptions options) {
        if (pos == null || options == null) return VfxHandle.inactive();
        if (level instanceof ServerLevel server) {
            return new VfxHandle(ServerVfxNetwork.play(server, id, pos,
                    options.parameters(), options.radius(), options.durationTicks()));
        }
        return clientWorld(level) ? play(id, at(pos), options.parameters()) : VfxHandle.inactive();
    }

    /**
     * Explicit custom server pose streaming. Normal Entity/Block attachments need no streaming.
     * The returned handle supports updatePose(VfxTransform).
     */
    public static VfxHandle playStreamed(ResourceLocation id, ServerLevel level, VfxTransform pose,
                                         VfxOptions options) {
        if (options == null || pose == null) return VfxHandle.inactive();
        return new VfxHandle(ServerVfxNetwork.playStreamed(level, id, pose,
                options.parameters(), options.radius(), options.durationTicks()));
    }

    public static VfxHandle playStreamed(ResourceLocation id, ServerLevel level, VfxTransform pose) {
        return playStreamed(id, level, pose, VfxOptions.DEFAULT);
    }

    /** Legacy explicit network overload, kept for existing mods; prefer Vfx.play(id, source). */
    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation id, Vec3 position,
                                       Map<String, Double> parameters, double radius, int durationTicks) {
        return ServerVfxNetwork.play(level, id, position, parameters, radius, durationTicks);
    }

    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation id, VfxTransform pose,
                                       Map<String, Double> parameters, double radius, int durationTicks) {
        return ServerVfxNetwork.play(level, id, pose, parameters, radius, durationTicks);
    }

    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation id, Entity entity,
                                       Map<String, Double> parameters, double radius, int durationTicks) {
        return ServerVfxNetwork.play(level, id, entity, parameters, radius, durationTicks);
    }

    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation id, BlockPos pos,
                                       Map<String, Double> parameters, double radius, int durationTicks) {
        return ServerVfxNetwork.play(level, id, pos, parameters, radius, durationTicks);
    }

    public static VfxRemoteHandle play(ServerLevel level, ResourceLocation id, BlockEntity block,
                                       Map<String, Double> parameters, double radius, int durationTicks) {
        return ServerVfxNetwork.play(level, id, block, parameters, radius, durationTicks);
    }

    /** Custom poses are explicitly updated; arbitrary Java providers are never network serialized. */
    public static VfxRemoteHandle playStreamed(ServerLevel level, ResourceLocation id, VfxTransform pose,
                                               Map<String, Double> parameters, double radius, int durationTicks) {
        return ServerVfxNetwork.playStreamed(level, id, pose, parameters, radius, durationTicks);
    }

    static boolean active(long id) {
        return client() && ClientVfxAccess.active(id);
    }

    static boolean stop(long id) {
        return client() && ClientVfxAccess.stop(id);
    }

    static boolean parameter(long id, String name, double value) {
        return client() && ClientVfxAccess.parameter(id, name, value);
    }

    static boolean trigger(long id, String event) {
        return client() && ClientVfxAccess.trigger(id, event);
    }

    static boolean attach(long id, VfxTransformProvider provider) {
        return client() && ClientVfxAccess.attach(id, provider);
    }

    static boolean attach(long id, Entity entity) {
        return attach(id, at(entity));
    }

    static boolean attach(long id, BlockPos block) {
        return attach(id, at(block));
    }

    static boolean attach(long id, BlockEntity blockEntity) {
        return attach(id, at(blockEntity));
    }

    static boolean attach(long id, Vec3 position) {
        return attach(id, VfxTransformProvider.fixed(position));
    }

    static boolean detach(long id) {
        return client() && ClientVfxAccess.detach(id);
    }

    private static boolean client() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    private static boolean clientWorld(Level level) {
        return client() && ClientVfxAccess.inWorld(level);
    }

    private static VfxTransformProvider available(VfxTransformProvider source) {
        return source != null ? source : UNAVAILABLE;
    }
}