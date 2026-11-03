package net.kofllee.pyrovfx.client;

import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.VfxTransformProviders;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

/** Client-only adapter; common API does not expose renderer, instance or manager types. */
public final class ClientVfxAccess {
    private ClientVfxAccess() {}

    private static boolean ready() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.isSameThread() && minecraft.level != null;
    }

    /** Reject stale client worlds and calls from the integrated logical server thread. */
    public static boolean inWorld(Level level) {
        return ready() && level != null && level == Minecraft.getInstance().level;
    }

    public static Level currentLevel() {
        return ready() ? Minecraft.getInstance().level : null;
    }

    public static long play(ResourceLocation id, VfxTransformProvider source, Map<String, Double> parameters) {
        return ready() ? ClientVfxManager.playManaged(id, source, parameters) : 0;
    }

    /** Null means the source cannot be captured on this client thread/world. */
    public static VfxTransformProvider sourceEntity(Entity entity) {
        return ready() && entity != null && entity.level() == Minecraft.getInstance().level
                ? VfxTransformProviders.entity(entity) : null;
    }

    public static VfxTransformProvider sourceBlock(BlockPos block) {
        if (!ready() || block == null) return null;
        ClientLevel level = Minecraft.getInstance().level;
        return VfxTransformProviders.block(level, block);
    }

    public static VfxTransformProvider sourceBlockEntity(BlockEntity entity) {
        return ready() && entity != null && entity.getLevel() == Minecraft.getInstance().level
                ? VfxTransformProviders.blockEntity(entity) : null;
    }

    public static boolean active(long id) {
        return ready() && ClientVfxManager.handleActive(id);
    }

    public static boolean stop(long id) {
        return ready() && ClientVfxManager.stopHandle(id);
    }

    public static boolean parameter(long id, String name, double value) {
        return ready() && ClientVfxManager.setHandleParameter(id, name, value);
    }

    public static boolean trigger(long id, String event) {
        return ready() && ClientVfxManager.triggerHandle(id, event);
    }

    public static boolean attach(long id, VfxTransformProvider source) {
        return ready() && ClientVfxManager.attachHandle(id, source);
    }

    public static boolean detach(long id) {
        return ready() && ClientVfxManager.detachHandle(id);
    }
}