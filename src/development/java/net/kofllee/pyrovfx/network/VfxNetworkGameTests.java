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
        var local = new VfxTransform(new Vec3(0.4, 0.2, -0.5),
                new Quaternionf().rotationXYZ(-0.3f, 0.7f, 0.15f));
        var values = new VfxPackets.Anchor[] {
                VfxPackets.Anchor.fixed(pose),
                VfxPackets.Anchor.stream(pose),
                VfxPackets.Anchor.entity(19, UUID.randomUUID(), pose, local),
                VfxPackets.Anchor.entity(22, UUID.randomUUID(), pose, local, "head"),
                VfxPackets.Anchor.block(new BlockPos(2, 4, 6), false, pose, local),
                VfxPackets.Anchor.block(new BlockPos(2, 4, 6), true, pose, local),
                VfxPackets.Anchor.block(new BlockPos(2, 4, 6), true, pose, local,
                        "pyrotechnica:muzzle")
        };
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (VfxPackets.Anchor value : values) VfxPackets.writeAnchor(buffer, value);
            for (VfxPackets.Anchor value : values) {
                VfxPackets.Anchor decoded = VfxPackets.readAnchor(buffer);
                if (value.kind() != decoded.kind() || value.entityId() != decoded.entityId()
                        || !value.entityUuid().equals(decoded.entityUuid())
                        || !value.block().equals(decoded.block())
                        || !value.socket().equals(decoded.socket())
                        || value.pose().position().distanceTo(decoded.pose().position()) > 1e-8
                        || Math.abs(value.pose().rotation().dot(decoded.pose().rotation())) < 0.99999f
                        || value.local().position().distanceTo(decoded.local().position()) > 1e-8
                        || Math.abs(value.local().rotation().dot(decoded.local().rotation())) < 0.99999f)
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
            // Infinite server leases may run much longer than the ten-minute
            // *finite* lease limit. Late clients must retain their real phase.
            new VfxPackets.Start(UUID.randomUUID(),
                    ResourceLocation.withDefaultNamespace("overworld"),
                    ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order"),
                    VfxPackets.Anchor.fixed(pose), Map.of(), 20 * 60 * 60);
            buffer.clear();
            buffer.writeByte(VfxPackets.Anchor.ENTITY);
            buffer.writeVarInt(7);
            buffer.writeUUID(UUID.randomUUID());
            VfxPackets.writeTransform(buffer, pose);
            buffer.writeDouble(Double.NaN);
            buffer.writeDouble(0);
            buffer.writeDouble(0);
            buffer.writeFloat(0);
            buffer.writeFloat(0);
            buffer.writeFloat(0);
            buffer.writeFloat(1);
            buffer.writeUtf("", 128);
            try {
                VfxPackets.readAnchor(buffer);
                throw new AssertionError("Non-finite network local offset was accepted");
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
            try {
                VfxPackets.Anchor.block(BlockPos.ZERO, false, pose, local, "head");
                throw new AssertionError("Plain block unexpectedly accepted an animated socket");
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
            try {
                new VfxPackets.Start(UUID.randomUUID(),
                        ResourceLocation.withDefaultNamespace("overworld"),
                        ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order"),
                        VfxPackets.Anchor.fixed(pose), Map.of(), -1);
                throw new AssertionError("Negative remote timeline age accepted");
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
            var mutable = new java.util.HashMap<>(Map.of("power", 3.0));
            var snapshot = new VfxPackets.Start(UUID.randomUUID(),
                    ResourceLocation.withDefaultNamespace("overworld"),
                    ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order"),
                    VfxPackets.Anchor.fixed(pose), mutable);
            mutable.put("power", 999.0);
            if (!snapshot.parameters().equals(Map.of("power", 3.0)))
                throw new AssertionError("Queued network start observed a later mutable parameter update");
            try {
                snapshot.parameters().put("injected", 5.0);
                throw new AssertionError("Start packet parameter snapshot is mutable");
            } catch (UnsupportedOperationException expected) {
                // Expected.
            }
            try {
                new VfxPackets.Start(UUID.randomUUID(),
                        ResourceLocation.withDefaultNamespace("overworld"),
                        ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order"),
                        VfxPackets.Anchor.fixed(pose), Map.of("bad", Double.NaN));
                throw new AssertionError("Non-finite parameters bypassed Start validation");
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
            buffer.clear();
            buffer.writeVarInt(1);
            buffer.writeUtf("", 64);
            buffer.writeDouble(2.0);
            try {
                VfxPackets.readParameters(buffer);
                throw new AssertionError("Blank network parameter name was accepted");
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
            // Other GameTests share this ServerLevel and may change its total
            // effect count before the delayed callback. Inspect this UUID.
            if (handle.isActive())
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

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void thousandStationaryEffectsUseBatchedAudienceChecks(GameTestHelper helper) {
        var level = helper.getLevel();
        var effect = ResourceLocation.fromNamespaceAndPath("pyro_vfx", "museum/source_custom");
        Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
        var handles = new java.util.ArrayList<VfxRemoteHandle>();
        try {
            for (int i = 0; i < 1_000; i++) {
                var handle = ServerVfxNetwork.play(level, effect, position,
                        Map.of(), 32, 60);
                if (!handle.isActive())
                    throw new AssertionError("Cannot initialize 1,000 active server VFX leases: " + i);
                handles.add(handle);
            }
        } catch (Throwable error) {
            handles.forEach(VfxRemoteHandle::stop);
            throw error;
        }
        helper.runAfterDelay(12, () -> {
            try {
                var counters = ServerVfxNetwork.telemetry(level);
                if (counters == null || counters.effects() < 1_000
                        || counters.audienceScans() > 400)
                    throw new AssertionError("Static audience refreshes were not throttled: " + counters);
                org.slf4j.LoggerFactory.getLogger(VfxNetworkGameTests.class).info(
                        "Pyro network 1000-effect stress: {} ms server VFX tick, {} audience scans, {} candidate player checks, {} actual players",
                        String.format(java.util.Locale.ROOT, "%.3f", counters.networkTickNanos() / 1e6),
                        counters.audienceScans(), counters.playerChecks(), counters.players());
                helper.succeed();
            } finally {
                handles.forEach(VfxRemoteHandle::stop);
            }
        });
    }
}
