package net.kofllee.pyrovfx.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.Set;

/** Vanilla chest/ender-chest ModelParts; other special renderers use custom resolvers. */
final class VanillaBlockSockets {
    private VanillaBlockSockets() {}

    static Set<String> paths(BlockEntity block) {
        return block instanceof LidBlockEntity
                && Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(block) instanceof ChestRenderer<?>
                ? Set.of("lid", "lock", "bottom") : Set.of();
    }

    static VfxTransform resolve(BlockEntity block, String path, float partialTick) {
        if (!paths(block).contains(path)) return null;
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(block);
        var state = block.getBlockState();
        ChestType chestType = state.hasProperty(ChestBlock.TYPE)
                ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
        String variant = switch (chestType) {
            case LEFT -> "doubleLeft";
            case RIGHT -> "doubleRight";
            default -> "";
        };
        String fieldName = variant.isEmpty() ? path
                : variant + Character.toUpperCase(path.charAt(0)) + path.substring(1);
        ModelPart part;
        try {
            Field field = ChestRenderer.class.getDeclaredField(fieldName);
            if (!field.trySetAccessible()) return null;
            part = (ModelPart) field.get(renderer);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
        if (part == null) return null;
        float open = ((LidBlockEntity) block).getOpenNess(partialTick);
        open = 1 - (float) Math.pow(1 - open, 3);
        Direction facing = state.hasProperty(ChestBlock.FACING)
                ? state.getValue(ChestBlock.FACING) : Direction.SOUTH;
        var pos = block.getBlockPos();
        PoseStack stack = new PoseStack();
        stack.translate(pos.getX(), pos.getY(), pos.getZ());
        stack.translate(.5f, .5f, .5f);
        stack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        stack.translate(-.5f, -.5f, -.5f);
        PartPose original = part.storePose();
        try {
            if (path.equals("lid") || path.equals("lock"))
                part.xRot = -open * ((float) Math.PI / 2);
            part.translateAndRotate(stack);
            Vector3f point = stack.last().pose().transformPosition(new Vector3f());
            Quaternionf rotation = stack.last().pose().getUnnormalizedRotation(new Quaternionf());
            return new VfxTransform(new Vec3(point.x, point.y, point.z), rotation);
        } finally {
            part.loadPose(original);
        }
    }
}