package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Client-side named socket resolvers. A server only sends the owner identity and
 * socket name; each client computes the world-space pose from its own animation.
 * Mods can supply positions for custom renderers, GeckoLib bones and animated
 * BlockEntities without making Pyro VFX depend on any animation library.
 */
public final class VfxSockets {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, BiFunction<Entity, Float, VfxTransform>> ENTITIES = new ConcurrentHashMap<>();
    private static final Map<String, BiFunction<BlockEntity, Float, VfxTransform>> BLOCK_ENTITIES = new ConcurrentHashMap<>();

    private VfxSockets() {}

    /** Register a globally named client-only point, e.g. "pyrotechnica:muzzle". */
    public static void registerEntity(String socket,
                                      BiFunction<Entity, Float, VfxTransform> resolver) {
        requireNamespaced(socket);
        if (ENTITIES.putIfAbsent(socket, java.util.Objects.requireNonNull(resolver)) != null)
            throw new IllegalArgumentException("Duplicate VFX entity socket: " + socket);
    }

    /** Register a socket belonging to a custom BlockEntity renderer. */
    public static void registerBlockEntity(String socket,
                                           BiFunction<BlockEntity, Float, VfxTransform> resolver) {
        requireNamespaced(socket);
        if (BLOCK_ENTITIES.putIfAbsent(socket, java.util.Objects.requireNonNull(resolver)) != null)
            throw new IllegalArgumentException("Duplicate VFX block entity socket: " + socket);
    }

    private static void requireNamespaced(String socket) {
        if (socket == null || socket.length() > 128
                || !socket.matches("[a-z0-9_.-]+:[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*"))
            throw new IllegalArgumentException("Use a namespaced socket identifier: " + socket);
    }

    public static VfxTransform resolve(Entity entity, String name, float partialTick) {
        if (entity == null || entity.isRemoved() || entity.level() != Minecraft.getInstance().level)
            return null;
        var custom = ENTITIES.get(name);
        if (custom != null) {
            try { return custom.apply(entity, partialTick); }
            catch (RuntimeException ex) {
                LOGGER.warn("VFX entity socket '{}' resolver failed", name, ex);
                return null;
            }
        }
        // Do not pretend that an unknown namespaced mod socket is a vanilla bone.
        return name.indexOf(':') == -1
                ? VanillaModelSockets.resolve(entity, name, partialTick) : null;
    }

    public static VfxTransform resolve(BlockEntity blockEntity, String name, float partialTick) {
        if (blockEntity == null || blockEntity.isRemoved()
                || blockEntity.getLevel() != Minecraft.getInstance().level) return null;
        var custom = BLOCK_ENTITIES.get(name);
        if (custom == null) return name.indexOf(':') < 0
                ? VanillaBlockSockets.resolve(blockEntity, name, partialTick) : null;
        try { return custom.apply(blockEntity, partialTick); }
        catch (RuntimeException ex) {
            LOGGER.warn("VFX block entity socket '{}' resolver failed", name, ex);
            return null;
        }
    }

    /** Vanilla model part paths available on this client, including nested paths. */
    public static java.util.Set<String> vanillaParts(Entity entity, float partialTick) {
        return VanillaModelSockets.paths(entity, partialTick);
    }

    public static java.util.Set<String> vanillaParts(BlockEntity blockEntity) {
        return VanillaBlockSockets.paths(blockEntity);
    }
}