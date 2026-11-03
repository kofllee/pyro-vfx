package net.kofllee.pyrovfx.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Samples vanilla ModelPart hierarchies without depending on the entity being
 * inside the camera frustum. Animated poses are evaluated, then restored on
 * the renderer-owned model. This covers normal living-model animation; custom
 * renderer transforms/extra render layers use VfxSockets.registerEntity.
 */
final class VanillaModelSockets {
    private static final Field CHILDREN;
    private record Cached(long tick, float partial, Map<String, VfxTransform> parts) {}
    private static final Map<Entity, Cached> CACHE = new java.util.WeakHashMap<>();

    static {
        try {
            CHILDREN = ModelPart.class.getDeclaredField("children");
            CHILDREN.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Minecraft ModelPart hierarchy is unavailable", exception);
        }
    }

    private VanillaModelSockets() {}

    static VfxTransform resolve(Entity entity, String path, float partialTick) {
        return sample(entity, partialTick).get(path);
    }

    static Set<String> paths(Entity entity, float partialTick) {
        return Set.copyOf(sample(entity, partialTick).keySet());
    }

    private static Map<String, VfxTransform> sample(Entity entity, float partialTick) {
        if (!(entity instanceof LivingEntity living) || living.isRemoved()
                || entity.level() != Minecraft.getInstance().level) return Map.of();
        long tick = entity.level().getGameTime();
        Cached old = CACHE.get(entity);
        if (old != null && old.tick == tick && old.partial == partialTick) return old.parts;
        Map<String, VfxTransform> computed = compute(living, Mth.clamp(partialTick, 0, 1));
        CACHE.put(entity, new Cached(tick, partialTick, computed));
        return computed;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, VfxTransform> compute(LivingEntity entity, float partial) {
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)) return Map.of();
        EntityModel model = (EntityModel) livingRenderer.getModel();
        List<NamedPart> roots = roots(model);
        if (roots.isEmpty()) return Map.of();

        // Shared renderer models are mutable. Preserve the complete pose before
        // replaying their vanilla animation, including for culled/offscreen mobs.
        IdentityHashMap<ModelPart, PartPose> saved = new IdentityHashMap<>();
        for (NamedPart root : roots) saveTree(root.part(), saved);
        float oldAttack = model.attackTime;
        boolean oldRiding = model.riding, oldYoung = model.young;
        try {
            float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
            float headYaw = Mth.rotLerp(partial, entity.yHeadRotO, entity.yHeadRot);
            float headPitch = Mth.lerp(partial, entity.xRotO, entity.getXRot());
            float walkSpeed = entity.walkAnimation.speed(partial);
            float walkPosition = entity.walkAnimation.position(partial);
            model.young = entity.isBaby();
            model.riding = entity.isPassenger() && entity.getVehicle() != null
                    && entity.getVehicle().shouldRiderSit();
            model.attackTime = entity.getAttackAnim(partial);
            if (model.riding || !entity.isAlive()) {
                walkSpeed = 0;
                walkPosition = 0;
            }
            if (model.young) walkPosition *= 3;
            walkSpeed = Math.min(walkSpeed, 1);
            model.prepareMobModel(entity, walkPosition, walkSpeed, partial);
            model.setupAnim(entity, walkPosition, walkSpeed, entity.tickCount + partial,
                    Mth.wrapDegrees(headYaw - bodyYaw), headPitch);

            // Vanilla's standard upright living-model frame (feet origin).
            PoseStack pose = new PoseStack();
            pose.translate(Mth.lerp(partial, entity.xo, entity.getX()),
                    Mth.lerp(partial, entity.yo, entity.getY()),
                    Mth.lerp(partial, entity.zo, entity.getZ()));
            float scale = entity.getScale();
            pose.scale(scale, scale, scale);
            pose.mulPose(Axis.YP.rotationDegrees(180 - bodyYaw));
            pose.scale(-1, -1, 1);
            pose.translate(0, -1.501, 0);

            Map<String, VfxTransform> result = new LinkedHashMap<>();
            for (NamedPart root : roots) visit(root.part(), root.name(), pose, result);
            return Map.copyOf(result);
        } catch (RuntimeException exception) {
            // Third-party model callbacks may expect their own renderer lifecycle.
            return Map.of();
        } finally {
            saved.forEach(ModelPart::loadPose);
            model.attackTime = oldAttack;
            model.riding = oldRiding;
            model.young = oldYoung;
        }
    }

    private record NamedPart(String name, ModelPart part) {}

    private static List<NamedPart> roots(EntityModel<?> model) {
        if (model instanceof HierarchicalModel<?> hierarchical)
            return List.of(new NamedPart("root", hierarchical.root()));
        // Older vanilla models (HumanoidModel, QuadrupedModel, etc.) expose
        // their top-level parts as fields rather than a single root().
        List<NamedPart> parts = new ArrayList<>();
        IdentityHashMap<ModelPart, Boolean> seen = new IdentityHashMap<>();
        for (Class<?> owner = model.getClass(); owner != null && owner != Object.class;
             owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !ModelPart.class.isAssignableFrom(field.getType())
                        || !field.trySetAccessible()) continue;
                try {
                    if (field.get(model) instanceof ModelPart part && seen.put(part, true) == null)
                        parts.add(new NamedPart(snakeCase(field.getName()), part));
                } catch (IllegalAccessException ignored) {
                    // Cannot address a part exposed only through a renderer's private state.
                }
            }
        }
        return parts;
    }

    private static String snakeCase(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart part) {
        try {
            return (Map<String, ModelPart>) CHILDREN.get(part);
        } catch (IllegalAccessException exception) {
            return Map.of();
        }
    }

    private static void saveTree(ModelPart part, IdentityHashMap<ModelPart, PartPose> saved) {
        if (saved.putIfAbsent(part, part.storePose()) != null) return;
        for (ModelPart child : children(part).values()) saveTree(child, saved);
    }

    private static void visit(ModelPart part, String path, PoseStack parent,
                              Map<String, VfxTransform> result) {
        parent.pushPose();
        part.translateAndRotate(parent);
        Vector3f pivot = parent.last().pose().transformPosition(new Vector3f());
        Quaternionf rotation = parent.last().pose().getUnnormalizedRotation(new Quaternionf());
        VfxTransform transform = new VfxTransform(new Vec3(pivot.x, pivot.y, pivot.z), rotation);
        result.put(path, transform);
        String basename = path.substring(path.lastIndexOf('/') + 1);
        result.putIfAbsent(basename, transform);
        for (var child : children(part).entrySet())
            visit(child.getValue(), path + "/" + child.getKey(), parent, result);
        parent.popPose();
    }
}