package net.kofllee.pyrovfx.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kofllee.pyrovfx.client.vfx.ClientVfxParticle;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.kofllee.pyrovfx.vfx.transform.VfxEntityPoseInitializer;

import java.util.Map;
import java.util.WeakHashMap;

public final class EntityModelVfxParticleRenderer {
    private EntityModelVfxParticleRenderer() {}

    // Animation state belongs to the PARTICLE, not the entity type. Reusing one
    // dummy mob across particles overwrites tickCount/pose from the previous
    // particle during the same frame and can make animated parts jump.
    // Weak keys release render entities after the owning particle expires.
    private static final Map<ClientVfxParticle, Entity> CACHE = new WeakHashMap<>();
    private static ClientLevel cachedLevel;

    /** Used by the real-client benchmark to catch shared animation state. */
    public static boolean hasIndependentRenderEntities(ClientVfxParticle first, ClientVfxParticle second) {
        Entity a = CACHE.get(first);
        Entity b = CACHE.get(second);
        return a != null && b != null && a != b;
    }

    /** Benchmark assertion for the cause of repeated unticked-head motion. */
    public static boolean hasStableInitialHeadPose(ClientVfxParticle particle) {
        Entity entity = CACHE.get(particle);
        if (!(entity instanceof LivingEntity living)) return false;
        return Math.abs(living.yHeadRotO - living.yHeadRot) < 1.0e-4f
                && Math.abs(living.yBodyRotO - living.yBodyRot) < 1.0e-4f
                && Math.abs(living.xRotO - living.getXRot()) < 1.0e-4f;
    }

    public static void render(
            ClientVfxParticle particle,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            Camera camera,
            float partialTick
    ) {
        var modelDefinition = particle.emitterDefinition().render().model();

        Minecraft minecraft = Minecraft.getInstance();

        if (cachedLevel != minecraft.level) {
            CACHE.clear();
            cachedLevel = minecraft.level;
        }

        Entity entity = CACHE.computeIfAbsent(particle, key -> {
            EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(modelDefinition.model());
            Entity created = entityType.create(cachedLevel);
            if (created != null) {
                created.setPos(0, 0, 0);
                VfxEntityPoseInitializer.synchronizeUntickedPose(created);
            }
            return created;
        });
        if(entity == null) {
            return;
        }

        entity.tickCount = particle.age();
        // This dummy is not moved or ticked in the world. Motion comes from
        // the particle transform, while its independent age/pose remains
        // continuous across successive renders of THIS particle.

        int light = particle.emitterDefinition().render().environmentLighting()
                ? VfxRenderLight.getParticleLight(particle, partialTick)
                : LightTexture.FULL_BRIGHT;

        poseStack.pushPose();

        VfxRenderTransforms.applyParticleTransform(
                poseStack,
                particle,
                camera,
                partialTick
        );

        minecraft.getEntityRenderDispatcher().render(
                entity,
                0.0,
                0.0,
                0.0,
                0.0F,
                partialTick,
                poseStack,
                bufferSource,
                light
        );

        poseStack.popPose();
    }
}
