package net.kofllee.pyrovfx.client;

import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.VfxTransformProviders;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;

/** Client-only adapter; common API does not expose renderer, instance or manager types. */
public final class ClientVfxAccess {
    private ClientVfxAccess() {}

    private static boolean ready() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.isSameThread() && minecraft.level != null;
    }

    public static long play(ResourceLocation id, VfxTransformProvider source, Map<String, Double> parameters) {
        return ready() ? ClientVfxManager.playManaged(id, source, parameters) : 0;
    }

    public static long playEntity(ResourceLocation id, Entity entity, Map<String, Double> parameters) {
        if (!ready() || entity == null || entity.level() != Minecraft.getInstance().level) return 0;
        return play(id, VfxTransformProviders.entity(entity), parameters);
    }

    public static long playBlock(ResourceLocation id, BlockPos block, Map<String, Double> parameters) {
        if (!ready() || block == null) return 0;
        ClientLevel level = Minecraft.getInstance().level;
        return play(id, VfxTransformProviders.block(level, block), parameters);
    }

    public static long playBlockEntity(ResourceLocation id, BlockEntity entity, Map<String, Double> parameters) {
        if (!ready() || entity == null || entity.getLevel() != Minecraft.getInstance().level) return 0;
        return play(id, VfxTransformProviders.blockEntity(entity), parameters);
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

    public static boolean attachEntity(long id, Entity entity) {
        return ready() && entity != null && entity.level() == Minecraft.getInstance().level
                && attach(id, VfxTransformProviders.entity(entity));
    }

    public static boolean attachBlock(long id, BlockPos block) {
        return ready() && block != null
                && attach(id, VfxTransformProviders.block(Minecraft.getInstance().level, block));
    }

    public static boolean attachBlockEntity(long id, BlockEntity entity) {
        return ready() && entity != null && entity.getLevel() == Minecraft.getInstance().level
                && attach(id, VfxTransformProviders.blockEntity(entity));
    }

    public static boolean detach(long id) {
        return ready() && ClientVfxManager.detachHandle(id);
    }
}