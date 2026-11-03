package net.kofllee.pyrovfx.api;

import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.network.ServerVfxNetwork;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.kofllee.pyrovfx.vfx.transform.VfxTrackedTransformProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Tests the API used by other mods, rather than directly invoking the networking layer. */
@GameTestHolder(PyroVfx.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VfxUnifiedApiGameTests {
    private static final ResourceLocation EFFECT =
            ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order");

    private VfxUnifiedApiGameTests() {}

    @GameTest(template = "empty")
    public static void nestedLocalTransformsStayNetworkableAndRebind(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos.offset(2, 0, 0), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.offset(3, 0, 0), Blocks.CHEST.defaultBlockState());
        var chest = level.getBlockEntity(pos.offset(3, 0, 0));
        var pig = EntityType.PIG.create(level);
        if (pig == null || chest == null) throw new AssertionError("Missing chained source fixture");
        pig.setPos(Vec3.atCenterOf(pos));
        pig.setYRot(90);
        if (!level.addFreshEntity(pig)) throw new AssertionError("Cannot add fixture");

        var rocketNozzle = Vfx.at(pig)
                .offset(0, 0, -0.5)
                .rotate(0, 90, 0)
                .offset(0.25, 0, 0);
        if (!(rocketNozzle instanceof VfxTrackedTransformProvider tracked)
                || tracked.kind() != VfxTrackedTransformProvider.Kind.ENTITY)
            throw new AssertionError("Nested operations lost entity identity");
        var headSocket = Vfx.at(pig).socket("head")
                .offset(0, 0.25, 0).rotate(0, 90, 0);
        if (!(headSocket instanceof VfxTrackedTransformProvider headTracked)
                || !"head".equals(headTracked.socketName())
                || headTracked.kind() != VfxTrackedTransformProvider.Kind.ENTITY)
            throw new AssertionError("Named socket/offset chain lost tracked entity identity");
        VfxTransform rootPose = new VfxTransform(pig.position(), new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-pig.getYRot()), (float) Math.toRadians(pig.getXRot()), 0));
        VfxTransform expected = rootPose
                .compose(VfxTransform.at(new Vec3(0, 0, -0.5)))
                .compose(new VfxTransform(Vec3.ZERO, new Quaternionf().rotationY((float) Math.PI / 2)))
                .compose(VfxTransform.at(new Vec3(0.25, 0, 0)));
        if (tracked.get(1) == null || tracked.get(1).position().distanceTo(expected.position()) > 1.0e-5
                || Math.abs(tracked.get(1).rotation().dot(expected.rotation())) < 0.99999f)
            throw new AssertionError("Nested local transformations changed their order");

        int before = ServerVfxNetwork.liveCount(level);
        VfxHandle effect = Vfx.play(EFFECT, rocketNozzle);
        if (!effect.isActive() || ServerVfxNetwork.liveCount(level) != before + 1)
            throw new AssertionError("Chained Entity provider did not create a network effect");
        var block = Vfx.at(level, pos.offset(2, 0, 0)).offset(0, 1, 0).rotate(0, 0, 90);
        var blockEntity = Vfx.at(chest).offset(0.1, 0.2, -0.3).rotate(10, 20, 30);
        if (!(block instanceof VfxTrackedTransformProvider)
                || !(blockEntity instanceof VfxTrackedTransformProvider)
                || !effect.attach(headSocket)
                || !effect.attach(Vfx.at(chest).socket("pyrotechnica:lid"))
                || !effect.attach(block) || !effect.attach(blockEntity)
                || !effect.attach(rocketNozzle))
            throw new AssertionError("Rebinding a network instance lost its source");
        // Unlike a built-in descriptor, an arbitrary server callback is automatically streamed.
        if (!effect.attach(partialTick -> VfxTransform.at(Vec3.atCenterOf(pos))))
            throw new AssertionError("Custom server callback did not switch to automatic streaming");
        if (!effect.detach() || !effect.isActive() || !effect.stop() || effect.stop()
                || ServerVfxNetwork.liveCount(level) != before)
            throw new AssertionError("Chained network instance did not detach/stop");
        pig.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void customJavaProvidersStreamAndExpireWithoutManualPoseUpdates(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        AtomicInteger samples = new AtomicInteger();
        var callback = (net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider) partialTick -> {
            int sample = samples.incrementAndGet();
            return sample > 4 ? null : VfxTransform.at(position.add(sample * 0.1, 0, 0));
        };
        VfxHandle handle = Vfx.play(EFFECT, level, callback,
                VfxOptions.DEFAULT.withDurationTicks(40));
        if (!handle.isActive() || samples.get() != 1)
            throw new AssertionError("Server custom callback was not sampled at start");
        helper.runAfterDelay(8, () -> {
            if (handle.isActive() || samples.get() != 5)
                throw new AssertionError("Server callback did not update/expire automatically");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void fixedAndBlockProviderChainsHaveExplicitWorldIdentity(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        int before = ServerVfxNetwork.liveCount(level);
        var fixed = Vfx.at(level, Vec3.atCenterOf(pos)).offset(1, 2, 3).rotate(0, 45, 0);
        var block = Vfx.at(level, pos).offset(0, 0.5, 0).rotate(90, 0, 0);
        VfxHandle a = Vfx.play(EFFECT, fixed);
        VfxHandle b = Vfx.play(EFFECT, block);
        if (!a.isActive() || !b.isActive() || ServerVfxNetwork.liveCount(level) != before + 2)
            throw new AssertionError("Fixed or Block transform provider did not route to the server");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            if (!a.isActive() || b.isActive())
                throw new AssertionError("Removed block did not stop its chained source");
            if (!a.stop() || a.isActive() || b.isActive())
                throw new AssertionError("Fixed source cleanup failed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void serverEntityAndBlockEntityAutomaticallyBroadcast(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var pig = EntityType.PIG.create(level);
        if (pig == null) throw new AssertionError("Missing entity fixture");
        pig.setPos(Vec3.atCenterOf(pos));
        if (!level.addFreshEntity(pig)) throw new AssertionError("Cannot spawn fixture");
        level.setBlockAndUpdate(pos.offset(1, 0, 0), Blocks.CHEST.defaultBlockState());
        var chest = level.getBlockEntity(pos.offset(1, 0, 0));
        if (chest == null) throw new AssertionError("Missing block entity fixture");
        int before = ServerVfxNetwork.liveCount(level);

        VfxHandle smoke = Vfx.play(EFFECT, pig);
        VfxHandle sparks = Vfx.play(EFFECT, chest, Map.of("stage", 0.0));
        if (!smoke.isActive() || !sparks.isActive() || ServerVfxNetwork.liveCount(level) != before + 2)
            throw new AssertionError("Simple Entity/BlockEntity plays were not routed to server");
        if (!smoke.setParameter("stage", 1.0) || !smoke.trigger("increment")
                || !smoke.attach(chest) || !smoke.attach(pig) || !smoke.detach()
                || !smoke.attach(partialTick -> VfxTransform.at(Vec3.atCenterOf(pos)))
                || !sparks.attach(pos.offset(1, 0, 0)))
            throw new AssertionError("Unified server handle failed to rebind or auto-stream a provider");
        if (!smoke.stop() || smoke.stop() || smoke.isActive()
                || !sparks.stop() || sparks.stop()
                || ServerVfxNetwork.liveCount(level) != before)
            throw new AssertionError("Unified server handles did not release their leases");
        pig.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void levelPositionAndBlockOverloadsUseServerLease(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        VfxOptions shortLease = VfxOptions.DEFAULT.withDurationTicks(3)
                .withRadius(24).withParameters(Map.of("stage", 0.0));
        if (VfxOptions.DEFAULT.durationTicks() != 1200
                || VfxOptions.DEFAULT.radius() != 96
                || !VfxOptions.DEFAULT.parameters().isEmpty())
            throw new AssertionError("Default options were mutated");
        int before = ServerVfxNetwork.liveCount(level);
        VfxHandle point = Vfx.play(EFFECT, level, Vec3.atCenterOf(pos), shortLease);
        VfxHandle block = Vfx.play(EFFECT, level, pos, shortLease);
        VfxHandle pose = Vfx.play(EFFECT, level, VfxTransform.at(Vec3.atCenterOf(pos)), shortLease);
        VfxHandle streamed = Vfx.playStreamed(EFFECT, level, VfxTransform.at(Vec3.atCenterOf(pos)), shortLease);
        if (ServerVfxNetwork.liveCount(level) != before + 4
                || !point.isActive() || !block.isActive() || !pose.isActive() || !streamed.isActive()
                || !streamed.updatePose(VfxTransform.at(Vec3.atCenterOf(pos).add(1, 0, 0)))
                || point.updatePose(VfxTransform.at(Vec3.ZERO)))
            throw new AssertionError("World-aware position/streamed routing failed");
        helper.runAfterDelay(5, () -> {
            if (point.isActive() || block.isActive() || pose.isActive() || streamed.isActive()
                    || ServerVfxNetwork.liveCount(level) != before)
                throw new AssertionError("Default routing bypassed server duration/cleanup");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void unavailableSourcesDoNotCreateServerEffects(GameTestHelper helper) {
        var level = helper.getLevel();
        int before = ServerVfxNetwork.liveCount(level);
        if (Vfx.play(EFFECT, (net.minecraft.world.entity.Entity) null).isActive()
                || Vfx.play(EFFECT, level, (Vec3) null).isActive()
                || Vfx.play(EFFECT, level, Vec3.ZERO,
                        VfxOptions.DEFAULT.withRadius(Double.NaN)).isActive()
                || Vfx.play(EFFECT, level, new BlockPos(0, -100, 0)).isActive()
                || ServerVfxNetwork.liveCount(level) != before)
            throw new AssertionError("Invalid world-aware calls created an effect");
        helper.succeed();
    }
}