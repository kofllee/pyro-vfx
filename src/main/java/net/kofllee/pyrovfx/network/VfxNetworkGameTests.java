package net.kofllee.pyrovfx.network;

import io.netty.buffer.Unpooled;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.UUID;

@GameTestHolder(PyroVfx.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VfxNetworkGameTests {
    private VfxNetworkGameTests() {}

    @GameTest(template = "empty")
    public static void networkAnchorsRoundTripAndValidateBounds(GameTestHelper helper) {
        var pose = new VfxTransform(new Vec3(12.125, 64, -5.75),
                new Quaternionf().rotationXYZ(0.1f, 0.3f, -0.2f));
        var values = new VfxPackets.Anchor[] {
                VfxPackets.Anchor.fixed(pose),
                VfxPackets.Anchor.stream(pose),
                VfxPackets.Anchor.entity(19, UUID.randomUUID(), pose),
                VfxPackets.Anchor.block(new BlockPos(2, 4, 6), false, pose),
                VfxPackets.Anchor.block(new BlockPos(2, 4, 6), true, pose)
        };
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (VfxPackets.Anchor value : values) VfxPackets.writeAnchor(buffer, value);
            for (VfxPackets.Anchor value : values) {
                VfxPackets.Anchor decoded = VfxPackets.readAnchor(buffer);
                if (value.kind() != decoded.kind() || value.entityId() != decoded.entityId()
                        || !value.entityUuid().equals(decoded.entityUuid())
                        || !value.block().equals(decoded.block())
                        || value.pose().position().distanceTo(decoded.pose().position()) > 1e-8
                        || Math.abs(value.pose().rotation().dot(decoded.pose().rotation())) < 0.99999f)
                    throw new AssertionError("Network anchor lost identity/pose during serialization");
            }
            VfxPackets.writeParameters(buffer, Map.of("power", 1.5, "count", 16.0));
            if (!VfxPackets.readParameters(buffer).equals(Map.of("power", 1.5, "count", 16.0)))
                throw new AssertionError("Network parameters did not round-trip");
            buffer.clear();
            buffer.writeByte(99);
            try {
                VfxPackets.readAnchor(buffer);
                throw new AssertionError("Unknown source type must reject");
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
            buffer.clear();
            buffer.writeVarInt(33);
            try {
                VfxPackets.readParameters(buffer);
                throw new AssertionError("Unbounded parameter map must reject");
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteEffectControlsStayServerSideAndStopIdempotently(GameTestHelper helper) {
        var level = helper.getLevel();
        var effect = ResourceLocation.fromNamespaceAndPath("pyro_vfx", "museum/source_custom");
        var start = VfxTransform.at(Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)));
        int before = ServerVfxNetwork.liveCount(level);
        var inactive = ServerVfxNetwork.play(level, effect, start, Map.of("invalid", Double.NaN), 24, 30);
        if (inactive.isActive()) throw new AssertionError("Non-finite network parameter accepted");
        var handle = ServerVfxNetwork.playStreamed(level, effect, start, Map.of("count", 4.0), 24, 30);
        if (!handle.isActive() || ServerVfxNetwork.liveCount(level) != before + 1
                || !handle.setParameter("count", 6)
                || handle.setParameter("count", Double.POSITIVE_INFINITY)
                || !handle.trigger("spark") || handle.trigger("")
                || !handle.updatePose(VfxTransform.at(start.position().add(2, 0, 0)))
                || !handle.attach(start) || handle.updatePose(start)
                || !handle.attachStreamed(start) || !handle.updatePose(start)
                || !handle.detach() || handle.updatePose(start)) {
            throw new AssertionError("Remote source and command lifecycle failed");
        }
        if (!handle.stop() || handle.stop() || handle.isActive()
                || ServerVfxNetwork.liveCount(level) != before)
            throw new AssertionError("Remote stop must release ledger and be idempotent");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void remoteEffectExpiresWithoutClientOrParticleSimulation(GameTestHelper helper) {
        var level = helper.getLevel();
        int before = ServerVfxNetwork.liveCount(level);
        var handle = ServerVfxNetwork.play(level,
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "museum/source_block"),
                Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)), Map.of("count", 2.0), 24, 3);
        if (!handle.isActive() || ServerVfxNetwork.liveCount(level) != before + 1)
            throw new AssertionError("Server-side VFX lifetime start failed");
        helper.runAfterDelay(5, () -> {
            if (handle.isActive() || ServerVfxNetwork.liveCount(level) != before)
                throw new AssertionError("Server VFX lease must expire without connected clients");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void blockAttachmentStopsWhenSourceIsDestroyed(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        var handle = ServerVfxNetwork.play(level,
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "museum/source_block"),
                pos, Map.of("count", 3.0), 48, 100);
        if (!handle.isActive()) throw new AssertionError("Block attachment could not start");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            if (handle.isActive()) throw new AssertionError("Destroyed block retained remote effect");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void entityAttachmentStopsWhenSourceIsRemoved(GameTestHelper helper) {
        var level = helper.getLevel();
        var pig = EntityType.PIG.create(level);
        if (pig == null) throw new AssertionError("Failed to create entity fixture");
        pig.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        if (!level.addFreshEntity(pig)) throw new AssertionError("Failed to add entity fixture");
        var handle = ServerVfxNetwork.play(level,
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "museum/source_entity_world"),
                pig, Map.of("count", 3.0), 48, 100);
        if (!handle.isActive()) throw new AssertionError("Entity attachment could not start");
        pig.discard();
        helper.runAfterDelay(3, () -> {
            if (handle.isActive()) throw new AssertionError("Removed entity retained remote effect");
            helper.succeed();
        });
    }
}
