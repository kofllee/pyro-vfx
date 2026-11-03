package net.kofllee.pyrovfx.api;

import net.kofllee.pyrovfx.client.ClientVfxAccess;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.joml.Quaternionf;

import java.util.Map;

/**
 * Entry point for client-side effects. No registration or internal runtime access required.
 * Calls on a dedicated server (or off the client thread) return an inactive handle.
 * Server-to-client playback is introduced separately with networking.
 */
public final class Vfx {
    private Vfx() {}

    public static VfxHandle play(ResourceLocation id, Vec3 worldPosition) {
        return play(id, VfxTransformProvider.fixed(worldPosition));
    }

    public static VfxHandle play(ResourceLocation id, Vec3 worldPosition, Map<String, Double> parameters) {
        return play(id, VfxTransformProvider.fixed(worldPosition), parameters);
    }

    /** Fixed world position with a rotation applied to emitter-local offsets. */
    public static VfxHandle play(ResourceLocation id, Vec3 worldPosition, Quaternionf rotation) {
        VfxTransform pose = new VfxTransform(worldPosition, rotation);
        return play(id, partialTick -> pose);
    }

    public static VfxHandle play(ResourceLocation id, VfxTransformProvider source) {
        return play(id, source, Map.of());
    }

    public static VfxHandle play(ResourceLocation id, VfxTransformProvider source, Map<String, Double> parameters) {
        return new VfxHandle(client() ? ClientVfxAccess.play(id, source, parameters) : 0);
    }

    public static VfxHandle play(ResourceLocation id, Entity entity) {
        return play(id, entity, Map.of());
    }

    public static VfxHandle play(ResourceLocation id, Entity entity, Map<String, Double> parameters) {
        return new VfxHandle(client() ? ClientVfxAccess.playEntity(id, entity, parameters) : 0);
    }

    public static VfxHandle play(ResourceLocation id, BlockPos block) {
        return play(id, block, Map.of());
    }

    public static VfxHandle play(ResourceLocation id, BlockPos block, Map<String, Double> parameters) {
        return new VfxHandle(client() ? ClientVfxAccess.playBlock(id, block, parameters) : 0);
    }

    public static VfxHandle play(ResourceLocation id, BlockEntity blockEntity) {
        return play(id, blockEntity, Map.of());
    }

    public static VfxHandle play(ResourceLocation id, BlockEntity blockEntity, Map<String, Double> parameters) {
        return new VfxHandle(client() ? ClientVfxAccess.playBlockEntity(id, blockEntity, parameters) : 0);
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
        return client() && ClientVfxAccess.attachEntity(id, entity);
    }

    static boolean attach(long id, BlockPos block) {
        return client() && ClientVfxAccess.attachBlock(id, block);
    }

    static boolean attach(long id, BlockEntity blockEntity) {
        return client() && ClientVfxAccess.attachBlockEntity(id, blockEntity);
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
}