package net.kofllee.pyrovfx.client.dev;

import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.VfxTransformProviders;
import net.kofllee.pyrovfx.museum.VfxMuseumCatalog;
import net.kofllee.pyrovfx.museum.VfxMuseumWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;


/**
 * Developer-only gallery: each scene plays the actual renderer configuration,
 * with no switches or alternate rendering modes.
 */
@EventBusSubscriber(modid = PyroVfx.MOD_ID, value = Dist.CLIENT)
public final class VfxRenderGallery {
    private static final ResourceLocation MATRIX =
            ResourceLocation.fromNamespaceAndPath("pyro_vfx","bench/render_matrix");
    private static final ResourceLocation OVERLAPPING_ALPHA =
            ResourceLocation.fromNamespaceAndPath("pyro_vfx","bench/render_alpha");
    private static final ResourceLocation OVERLAPPING_MODELS =
            ResourceLocation.fromNamespaceAndPath("pyro_vfx","bench/render_models");
    private static final ResourceLocation OVERLAPPING_SPARKS =
            ResourceLocation.fromNamespaceAndPath("pyro_vfx","bench/render_sparks");

    private static ClientLevel currentWorld;
    private static int currentLane = -1;
    private static long matrixHandle, nearHandle, farHandle;
    private static long alphaHandle, modelHandle, sparksHandle;

    private VfxRenderGallery() {}

    private static void stop(long handle) {
        if (handle != 0) ClientVfxManager.stopHandle(handle);
    }

    private static void reset() {
        stop(matrixHandle);
        stop(nearHandle);
        stop(farHandle);
        stop(alphaHandle);
        stop(modelHandle);
        stop(sparksHandle);
        matrixHandle=nearHandle=farHandle=alphaHandle=modelHandle=sparksHandle=0;
    }

    private static long ensure(long handle, ResourceLocation effect, Vec3 origin) {
        if (handle != 0 && ClientVfxManager.handleActive(handle)) return handle;
        return ClientVfxManager.playManaged(effect,
                VfxTransformProviders.fixed(origin),Map.of());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pyrovfx.museum.visualOnly")) return;
        Minecraft mc=Minecraft.getInstance();
        ClientLevel level=mc.level;
        if (level!=currentWorld) {
            reset();
            currentWorld=level;
            currentLane=-1;
        }
        if (level==null || mc.player==null) return;

        int x=mc.player.getBlockX(), z=mc.player.getBlockZ();
        boolean atClouds=x>=VfxMuseumWorld.CLOUD_LAB_X
                && x<=VfxMuseumWorld.CLOUD_LAB_X+24
                && z>=VfxMuseumWorld.TRANSPARENCY_Z
                && z<=VfxMuseumWorld.TRANSPARENCY_Z+24
                && mc.player.getY()>=VfxMuseumWorld.CLOUD_LAB_Y
                && mc.player.getY()<=VfxMuseumWorld.CLOUD_LAB_Y+32;
        int nextLane=-1;
        if (atClouds) nextLane=-2;
        else {
            int lane=Math.floorDiv(x-VfxMuseumWorld.TRANSPARENCY_X,
                    VfxMuseumWorld.TRANSPARENCY_SPACING);
            int offset=x-VfxMuseumWorld.TRANSPARENCY_X-lane*VfxMuseumWorld.TRANSPARENCY_SPACING;
            if (lane>=0 && lane<VfxMuseumWorld.EXPERIMENT_LANES
                    && offset>=0 && offset<=14 && z>=VfxMuseumWorld.TRANSPARENCY_Z
                    && z<=VfxMuseumWorld.TRANSPARENCY_Z+24
                    && level.getBlockState(new BlockPos(
                    VfxMuseumWorld.TRANSPARENCY_X+lane*VfxMuseumWorld.TRANSPARENCY_SPACING,
                    VfxMuseumCatalog.FLOOR_Y,VfxMuseumWorld.TRANSPARENCY_Z))
                    .is(Blocks.YELLOW_CONCRETE))
                nextLane=lane;
        }

        if (nextLane!=currentLane) {
            reset();
            currentLane=nextLane;
        }
        if (currentLane==-1) return;
        if (currentLane==-2) {
            nearHandle=ensure(nearHandle,MATRIX,new Vec3(
                    VfxMuseumWorld.CLOUD_LAB_X+7.0,186.3,
                    VfxMuseumWorld.TRANSPARENCY_Z+16.5));
            farHandle=ensure(farHandle,MATRIX,new Vec3(
                    VfxMuseumWorld.CLOUD_LAB_X+17.0,198.5,
                    VfxMuseumWorld.TRANSPARENCY_Z+16.5));
            return;
        }

        Vec3 center=VfxMuseumWorld.transparencyOrigin(currentLane);
        matrixHandle=ensure(matrixHandle,MATRIX,center);
        if (currentLane==7) {
            // Only the intersection zone also contains overlapping smoke,
            // sparks and translucent meshes, all rendered in the NEW mode.
            alphaHandle=ensure(alphaHandle,OVERLAPPING_ALPHA,center);
            modelHandle=ensure(modelHandle,OVERLAPPING_MODELS,center);
            sparksHandle=ensure(sparksHandle,OVERLAPPING_SPARKS,center);
        }
    }
}
