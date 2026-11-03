package net.kofllee.pyrovfx.client.vfx;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.kofllee.pyrovfx.vfx.resource.VfxRegistry;
import net.minecraft.resources.ResourceLocation;
import net.kofllee.pyrovfx.client.render.VfxGpuTelemetry;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class ClientVfxManager {
    private static final List<ClientVfxInstance> INSTANCES = new ArrayList<>();
    private static final VfxWorldIdentity WORLD_IDENTITY = new VfxWorldIdentity();
    private static final VfxGlobalBudget GLOBAL_BUDGET = new VfxGlobalBudget();
    private static final VfxAdaptiveSpawnController ADAPTIVE_SPAWNS = new VfxAdaptiveSpawnController();
    private static final VfxAdaptiveSpawnController ADAPTIVE_GPU = new VfxAdaptiveSpawnController();
    private static final int[] ACTIVE_BY_CLASS = new int[VfxParticleCostClass.values().length];
    private static long lastGpuSample;
    private static int memorySampleTicks;
    private static double memorySpawnScale = 1.0;

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientVfxManager(){}

    public static void play(VfxDefinition definition, Vec3 position) {
        play(definition, position, VfxPlayOptions.empty());
    }

    public static void play(VfxDefinition definition, Vec3 position, VfxPlayOptions options) {
        tryPlay(definition, position, options);
    }

    public static boolean tryPlay(VfxDefinition definition, Vec3 position, VfxPlayOptions options) {
        return tryPlay(definition, VfxTransformProvider.fixed(position), options);
    }

    /** M7.1 transform entrypoint; effect handles and cross-mod API are added in M7.2. */
    public static boolean tryPlay(VfxDefinition definition, VfxTransformProvider provider, VfxPlayOptions options) {
        trackWorld();
        if (!GLOBAL_BUDGET.admitInstance(INSTANCES.size())) return false;
        if (provider == null) return false;
        try {
            INSTANCES.add(new ClientVfxInstance(definition, provider, options));
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }

    public static boolean tryPlay(ResourceLocation effectId, VfxTransformProvider provider) {
        return tryPlay(effectId, provider, VfxPlayOptions.empty());
    }

    /** Client-only convenience for lambda/method-reference attachment without registration. */
    public static boolean tryPlay(ResourceLocation effectId, VfxTransformProvider provider,
                                  VfxPlayOptions options) {
        VfxDefinition definition = VfxRegistry.get(effectId);
        return definition != null && tryPlay(definition, provider, options);
    }

    public static void playSeeded(VfxDefinition definition, Vec3 position, VfxPlayOptions options, long seed) {
        playSeeded(definition, VfxTransformProvider.fixed(position), options, seed);
    }

    public static void playSeeded(VfxDefinition definition, VfxTransformProvider provider,
                                  VfxPlayOptions options, long seed) {
        trackWorld();
        if (!GLOBAL_BUDGET.admitInstance(INSTANCES.size())) return;
        if (provider == null) return;
        try {
            INSTANCES.add(new ClientVfxInstance(definition, provider, options, seed));
        } catch (IllegalStateException ignored) {
            // Invalid source at creation; a VFX effect must not hold a missing attachment.
        }
    }

    public static void tick(){
        ClientLevel level = trackWorld();

        if(level == null) {
            for (ClientVfxInstance instance : INSTANCES) instance.close();
            INSTANCES.clear();
            GLOBAL_BUDGET.reset();
            ADAPTIVE_SPAWNS.reset();
            ADAPTIVE_GPU.reset();
            lastGpuSample = 0;
            memorySampleTicks = 0;
            memorySpawnScale = 1.0;
            VfxCollisionQueryCache.clear();
            return;
        }

        VfxCollisionQueryCache.beginTick(level);
        boolean heavy = !net.neoforged.fml.loading.FMLEnvironment.production
                && Boolean.getBoolean("pyrovfx.bench.allowHeavy");
        // Preserve baseline results for automated benchmarks unless explicitly
        // enabled by a dedicated testing flag.
        boolean automaticBenchmark = !System.getProperty("pyrovfx.bench.auto", "").isBlank();
        boolean adaptive = !heavy && VfxBudgetConfig.adaptiveSpawns()
                && (!automaticBenchmark || Boolean.getBoolean("pyrovfx.bench.adaptive"));
        // The GPU signal is asynchronous. Consume only a newly completed GL
        // timer sample; never wait for GPU fences on the client tick thread.
        int activeBeforeTick = getActiveParticleCount();
        boolean gpuAdaptive = adaptive && VfxBudgetConfig.gpuTimingEnabled()
                && VfxGpuTelemetry.supported();
        if (gpuAdaptive && VfxGpuTelemetry.sampleCount() > 0
                && VfxGpuTelemetry.sampleCount() != lastGpuSample) {
            lastGpuSample = VfxGpuTelemetry.sampleCount();
            ADAPTIVE_GPU.observe(VfxGpuTelemetry.latestSampleNs(), activeBeforeTick,
                    true, VfxBudgetConfig.gpuRenderTargetMs(),
                    VfxBudgetConfig.minimumAdaptiveSpawnPercent());
        } else if (!gpuAdaptive || activeBeforeTick == 0) {
            ADAPTIVE_GPU.reset();
        }
        int configuredSpawns = VfxBudgetConfig.globalSpawns();
        // Heap pressure is sampled at a bounded cadence. Only substantially
        // allocated VFX state can enable this extra admission safeguard.
        boolean sampleMemory = adaptive && (memorySampleTicks++ % 20 == 0);
        if (!adaptive || activeBeforeTick == 0) {
            memorySampleTicks = 0;
            memorySpawnScale = 1.0;
        }
        long allocatedStateBytes = 0;
        java.util.Arrays.fill(ACTIVE_BY_CLASS, 0);
        for (var instance : INSTANCES) {
            for (var emitter : instance.emitters()) {
                if (sampleMemory) allocatedStateBytes += emitter.allocatedParticleStateBytes();
                for (var dimension : VfxParticleCostClass.values()) {
                    if ((emitter.particleCostMask() & dimension.bit()) != 0) {
                        ACTIVE_BY_CLASS[dimension.ordinal()] += emitter.liveParticleCount();
                    }
                }
            }
        }
        if (sampleMemory && activeBeforeTick > 0) {
            Runtime vm = Runtime.getRuntime();
            memorySpawnScale = VfxGpuMemoryPolicy.heapScale(allocatedStateBytes,
                    vm.totalMemory() - vm.freeMemory(), vm.maxMemory());
        }
        GLOBAL_BUDGET.beginTick(getActiveParticleCount(), heavy,
                VfxBudgetConfig.globalParticles(), configuredSpawns, ACTIVE_BY_CLASS);
        double scale = adaptive ? Math.min(
                Math.min(ADAPTIVE_SPAWNS.spawnScale(), ADAPTIVE_GPU.spawnScale()),
                Math.min(VfxGpuMemoryPolicy.spawnScale(VfxGpuTelemetry.freeVramMiB(),
                        VfxGpuTelemetry.totalVramMiB()), memorySpawnScale)) : 1.0;
        GLOBAL_BUDGET.setAdaptiveSpawnScale(scale);
        long startedNs = System.nanoTime();
        Iterator<ClientVfxInstance> iterator = INSTANCES.iterator();
        while(iterator.hasNext()){
            ClientVfxInstance instance = iterator.next();

            try {
                long effectStarted = System.nanoTime();
                instance.tick(level, GLOBAL_BUDGET);
                instance.recordCpuTime(System.nanoTime() - effectStarted);
            } catch (Exception e) {
                LOGGER.error("Failed to tick Pyro VFX instance: {}", instance.definition().id(), e);
                instance.close();
                iterator.remove();
                continue;
            }

            if(instance.isFinished()){
                instance.close();
                iterator.remove();
            }
        }
        double targetMs = VfxBudgetConfig.cpuTickTargetMs();
        if (!net.neoforged.fml.loading.FMLEnvironment.production && automaticBenchmark
                && Boolean.getBoolean("pyrovfx.bench.adaptive")) {
            double override = Double.parseDouble(System.getProperty("pyrovfx.bench.adaptiveTargetMs", "0"));
            if (override >= 0.25 && override <= 20) targetMs = override;
        }
        ADAPTIVE_SPAWNS.observe(System.nanoTime() - startedNs, getActiveParticleCount(),
                adaptive, targetMs,
                VfxBudgetConfig.minimumAdaptiveSpawnPercent());
    }

    private static ClientLevel trackWorld() {
        ClientLevel level = Minecraft.getInstance().level;
        if (WORLD_IDENTITY.update(level)) {
            for (ClientVfxInstance instance : INSTANCES) instance.close();
            INSTANCES.clear();
            GLOBAL_BUDGET.reset();
            ADAPTIVE_SPAWNS.reset();
            ADAPTIVE_GPU.reset();
            VfxGpuTelemetry.requestReset();
            lastGpuSample = 0;
            memorySampleTicks = 0;
            memorySpawnScale = 1.0;
        }
        return level;
    }

    public static void clear(){
        for (ClientVfxInstance instance : INSTANCES) instance.close();
        INSTANCES.clear();
        GLOBAL_BUDGET.reset();
        ADAPTIVE_SPAWNS.reset();
        ADAPTIVE_GPU.reset();
        VfxGpuTelemetry.requestReset();
        lastGpuSample = 0;
        memorySampleTicks = 0;
        memorySpawnScale = 1.0;
        VfxCollisionQueryCache.clear();
    }

    public static VfxGlobalBudget globalBudget() { return GLOBAL_BUDGET; }
    public static VfxAdaptiveSpawnController adaptiveSpawns() { return ADAPTIVE_SPAWNS; }
    public static VfxAdaptiveSpawnController adaptiveGpu() { return ADAPTIVE_GPU; }
    public static double adaptiveMemoryScale() { return memorySpawnScale; }

    public static int getActiveInstanceCount(){
        return INSTANCES.size();
    }

    public static List<ClientVfxInstance> instances() {
        return Collections.unmodifiableList(INSTANCES);
    }

    public static int getActiveParticleCount(){
        int count = 0;

        for(ClientVfxInstance instance : INSTANCES){
            count += instance.particleCount();
        }

        return count;
    }
}
