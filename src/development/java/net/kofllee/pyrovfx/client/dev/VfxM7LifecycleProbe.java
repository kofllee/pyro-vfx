package net.kofllee.pyrovfx.client.dev;

import net.kofllee.pyrovfx.client.vfx.ClientVfxInstance;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.VfxTransformProviders;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/**
 * Isolated real-client attachment teardown probe. It operates only in the
 * Gradle benchmark client; no production world modifications or fixture files.
 * A removed source must stop births, drain existing particles and retire GPU
 * analytic batches. The chunk case drops a loaded client chunk explicitly.
 */
final class VfxM7LifecycleProbe {
    private final String kind;
    private final ClientLevel level;
    private final BlockPos anchor;
    private final BlockState original;
    private final Entity entity;
    private final VfxTransformProvider provider;
    private ClientVfxInstance instance;
    private int birthsBeforeRemoval;
    private boolean removed;
    private boolean verified;

    static boolean accepts(String scenario) {
        return scenario.equals("m7_lifecycle_entity")
                || scenario.equals("m7_lifecycle_block")
                || scenario.equals("m7_lifecycle_block_entity")
                || scenario.equals("m7_lifecycle_chunk");
    }

    private VfxM7LifecycleProbe(String kind, ClientLevel level, BlockPos anchor,
                                BlockState original, Entity entity, VfxTransformProvider provider) {
        this.kind = kind;
        this.level = level;
        this.anchor = anchor;
        this.original = original;
        this.entity = entity;
        this.provider = provider;
    }

    static VfxM7LifecycleProbe attach(String scenario, ClientLevel level, Vec3 origin) {
        if (!accepts(scenario)) throw new IllegalArgumentException(scenario);
        if (scenario.equals("m7_lifecycle_entity")) {
            Entity marker = EntityType.PIG.create(level);
            if (marker == null) throw new IllegalStateException("Client marker entity creation failed");
            marker.setId(800_001);
            marker.setPos(origin.x, origin.y, origin.z);
            level.addEntity(marker);
            var provider = VfxTransformProviders.entity(marker);
            if (provider.get(1.0f) == null) throw new IllegalStateException("Entity provider missing");
            return new VfxM7LifecycleProbe(scenario, level, null, null, marker, provider);
        }
        BlockPos anchor = BlockPos.containing(origin);
        if (scenario.equals("m7_lifecycle_chunk")) anchor = anchor.offset(32, 0, 0);
        if (!loaded(level, anchor))
            throw new IllegalStateException("Test chunk not loaded: " + anchor);
        BlockState original = level.getBlockState(anchor);
        var fixture = scenario.equals("m7_lifecycle_block_entity")
                ? Blocks.BARREL.defaultBlockState() : Blocks.STONE.defaultBlockState();
        level.setBlock(anchor, fixture, 3, 512);
        VfxTransformProvider provider;
        if (scenario.equals("m7_lifecycle_block_entity")) {
            var be = level.getBlockEntity(anchor);
            if (be == null) throw new IllegalStateException("Block-entity fixture not created");
            provider = VfxTransformProviders.blockEntity(be);
        } else {
            provider = VfxTransformProviders.block(level, anchor);
        }
        if (provider.get(1.0f) == null)
            throw new IllegalStateException("Fixture provider missing immediately after attachment");
        return new VfxM7LifecycleProbe(scenario, level, anchor, original, null, provider);
    }

    VfxTransformProvider provider() { return provider; }

    void bind() {
        if (ClientVfxManager.getActiveInstanceCount() != 1)
            throw new IllegalStateException("Expected exactly one attached VFX effect");
        instance = ClientVfxManager.instances().getFirst();
    }

    /** Called once after each actual Minecraft client tick and manager tick. */
    void afterTick(int index) {
        if (instance == null) throw new IllegalStateException("Lifecycle instance not bound");
        if (index == 16) {
            birthsBeforeRemoval = instance.emitters().getFirst().emittedParticles();
            if (birthsBeforeRemoval <= 0)
                throw new IllegalStateException("Attachment never produced any particles");
            removeSource();
            removed = true;
            if (provider.get(1.0f) != null)
                throw new IllegalStateException(kind + ": source still resolvable after removal");
        }
        if (removed && index >= 18 && index <= 31) {
            int births = instance.emitters().getFirst().emittedParticles();
            if (births != birthsBeforeRemoval)
                throw new IllegalStateException(kind + ": emission continued after source loss: "
                        + birthsBeforeRemoval + " -> " + births);
        }
        if (removed && index == 102) {
            var emitter = instance.emitters().getFirst();
            if (ClientVfxManager.getActiveInstanceCount() != 0
                    || ClientVfxManager.getActiveParticleCount() != 0
                    || emitter.liveParticleCount() != 0
                    || !emitter.analyticPools().isEmpty()
                    || emitter.gpuResidentBirthBytes() != 0) {
                throw new IllegalStateException(kind + ": particles or GPU resources leaked after detach");
            }
            verified = true;
        }
    }

    boolean verified() { return verified; }

    private void removeSource() {
        if (entity != null) {
            level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        } else if (kind.equals("m7_lifecycle_chunk")) {
            level.getChunkSource().drop(new ChunkPos(anchor));
            if (loaded(level, anchor))
                throw new IllegalStateException("Chunk remained loaded after client unload");
        } else {
            level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3, 512);
        }
    }

    void cleanup() {
        if (entity != null && level.getEntity(entity.getId()) == entity) {
            level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        }
        if (anchor != null && original != null && loaded(level, anchor)) {
            level.setBlock(anchor, original, 3, 512);
        }
    }

    private static boolean loaded(ClientLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false) != null;
    }
}