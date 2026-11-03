package net.kofllee.pyrovfx.api;

import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.network.ServerVfxNetwork;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** Tests the API used by other mods, rather than directly invoking the networking layer. */
@GameTestHolder(PyroVfx.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VfxUnifiedApiGameTests {
    private static final ResourceLocation EFFECT =
            ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order");

    private VfxUnifiedApiGameTests() {}

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
                || smoke.attach(partialTick -> VfxTransform.at(Vec3.ZERO))
                || !sparks.attach(pos.offset(1, 0, 0)))
            throw new AssertionError("Unified server handle failed or accepted client-only provider");
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