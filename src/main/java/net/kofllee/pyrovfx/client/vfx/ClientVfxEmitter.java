package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.vfx.sampling.VfxRotationSampler;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxSpawnPositionSampler;
import net.kofllee.pyrovfx.client.vfx.sampling.VfxMotionSampler;
import net.kofllee.pyrovfx.vfx.definition.*;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxEmitterLifetimeMode;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnAmountMode;
import net.kofllee.pyrovfx.vfx.type.VfxRenderType;
import net.kofllee.pyrovfx.vfx.type.VfxSpace;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.kofllee.pyrovfx.vfx.type.VfxTriggerType;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ClientVfxEmitter {
    private final VfxEmitterDefinition definition;
    private VfxTransform sourceTransform;
    private VfxTransform parentSourceTransform;
    private VfxTransform previousSourceTransform;
    private VfxTransform previousParentSourceTransform;
    private boolean sourceInitialized;
    private final VfxTravelTriggers travelTriggers;
    private final List<VfxTravelTriggers.Hit> travelHits = new ArrayList<>();
    private Vec3 previousEmitterPosition;
    private int age;
    private int lastInstantAge = -1;
    private int emittedParticles;
    private float spawnAccumulator;

    private final int delayTicks;
    private final int activeTicks;
    private final int sleepTicks;
    private final int loops;
    private final int instantAmount;
    private final int maxParticles;

    private boolean creationTriggersFired = false;
    private boolean expirationTriggersFired = false;

    private final double emitterRandom;

    /** Jump the emitter's clock without producing particles from past ticks. */
    void seekElapsed(int elapsed) {
        if (elapsed <= 0) return;
        age = elapsed;
        creationTriggersFired = true;
        expirationTriggersFired = VfxLifetimeRuntime.emitter(
                definition.emitterLifetime().mode(), age, delayTicks, activeTicks, sleepTicks, loops).finished();
        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.ONCE
                && elapsed > delayTicks)
            lastInstantAge = 0;
        // For a looping instant emitter, the next cycle still spawns normally.
    }


    private final List<ClientVfxParticle> particles = new ArrayList<>();
    private final List<ClientVfxParticle> pendingParticles = new ArrayList<>();
    private final VfxDenseParticlePool densePool;
    private final VfxAnalyticParticlePool analyticPool;
    private final List<VfxAnalyticParticlePool> analyticBatches = new ArrayList<>();
    private boolean analyticPrimarySpawned;
    private final VfxAnalyticPlan analyticPlan;
    private final VfxEventCommandQueue collisionEvents;
    private boolean updatingParticles;
    private String lastRenderer = "not rendered yet";
    private double simulationEmaMs;

    public ClientVfxEmitter(
            VfxEmitterDefinition definition,
            Vec3 emitterPosition,
            VfxExpressionContext effectStartContext,
            RandomSource random
    ) {

        this.definition = definition;
        this.sourceTransform = VfxTransform.at(emitterPosition);
        this.parentSourceTransform = sourceTransform;
        this.previousSourceTransform = sourceTransform;
        this.previousParentSourceTransform = parentSourceTransform;
        this.travelTriggers = new VfxTravelTriggers(definition.triggers());
        this.previousEmitterPosition = emitterPosition;
        this.analyticPlan = VfxAnalyticPlan.analyse(definition,
                net.neoforged.fml.loading.FMLEnvironment.dist.isClient()
                        && net.kofllee.pyrovfx.client.render.VfxGpuBirthBuffers.gpuPossible());
        this.analyticPool = analyticPlan.useGpu()
                && definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.ONCE
                && definition.spawnAmount().mode() == VfxSpawnAmountMode.INSTANT
                ? new VfxAnalyticParticlePool(definition, analyticPlan) : null;
        this.densePool = !analyticPlan.useGpu() && VfxDenseParticlePool.supports(definition)
                ? new VfxDenseParticlePool(definition) : null;
        this.collisionEvents = definition.particleTriggers().isEmpty() ? null : new VfxEventCommandQueue();

        this.emitterRandom = random.nextDouble();

        VfxExpressionContext emitterStartContext = ClientVfxExpressionContexts.emitterStart(
                effectStartContext,
                emitterPosition,
                emitterRandom
        );

        VfxEmitterLifetimeDefinition lifetime = definition.emitterLifetime();
        VfxSpawnAmountDefinition spawnAmount = definition.spawnAmount();

        this.delayTicks = Math.max(0, (int) Math.round(lifetime.delayTicks().evaluateDouble(emitterStartContext)));
        this.activeTicks = Math.max(0, (int) Math.round(lifetime.activeTicks().evaluateDouble(emitterStartContext)));
        this.sleepTicks = Math.max(0, (int) Math.round(lifetime.sleepTicks().evaluateDouble(emitterStartContext)));
        this.loops = Math.max(0, (int) Math.round(lifetime.loops().evaluateDouble(emitterStartContext)));

        this.instantAmount = Math.max(0, (int) Math.round(spawnAmount.amount().evaluateDouble(emitterStartContext)));
        this.maxParticles = Math.max(0, (int) Math.round(spawnAmount.maxParticles().evaluateDouble(emitterStartContext)));
    }

    public void tick(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext effectContext,
            boolean canSpawn,
            boolean sourceAvailable,
            Map<String, VfxEventDefinition> events,
            Map<String, ClientVfxEmitter> emittersById,
            VfxEventRuntime eventRuntime,
            RandomSource random
    ) {
        long startedNs = System.nanoTime();
        VfxLifetimeState lifetimeState = VfxLifetimeRuntime.emitter(
                definition.emitterLifetime().mode(),
                age,
                delayTicks,
                activeTicks,
                sleepTicks,
                loops
        );

        VfxExpressionContext emitterContext = ClientVfxExpressionContexts.emitterTick(
                effectContext,
                emitterPosition,
                lifetimeState,
                emittedParticles,
                emitterRandom
        );

        if (canSpawn) {
            travelTriggers.advance(previousEmitterPosition, emitterPosition,
                    (trigger, at) -> travelHits.add(new VfxTravelTriggers.Hit(trigger, at)));
            for (var hit : travelHits) {
                VfxEventRunner.run(hit.trigger().eventId(), events, emittersById,
                        level, effectPosition, hit.at(), emitterContext, eventRuntime, random);
            }
            travelHits.clear();
        }
        previousEmitterPosition = emitterPosition;

        if (sourceAvailable) {
            tickTriggers(
                    level,
                    effectPosition,
                    emitterPosition,
                    emitterContext,
                    random,
                    events,
                    emittersById,
                    lifetimeState,
                    eventRuntime
            );
        }

        if (canSpawn && lifetimeState.active()) {
            tickSpawnAmount(level, effectPosition, emitterPosition, emitterContext, lifetimeState, random, eventRuntime);
        }

        // Collision events may emit into this emitter. Process only the particles
        // present at the start of the phase; newly emitted particles start next tick.
        if (densePool != null) {
            densePool.tick(level, emitterContext,
                    sourceAvailable && collisionEvents != null
                    ? (x, y, z) -> {
                        Vec3 contact = new Vec3(x, y, z);
                        for (VfxTriggerDefinition trigger : definition.particleTriggers()) {
                            if (trigger.type() == VfxTriggerType.ON_COLLISION)
                                collisionEvents.enqueue(trigger.eventId(), contact);
                        }
                        // Preserve Legacy's immediate per-particle event ordering:
                        // set_param affects the next particle, and self-emitted
                        // births append after the original Dense tick cohort.
                        collisionEvents.drain(events, emittersById, level,
                                effectPosition, emitterContext, eventRuntime, random);
                    } : null);
        }
        if (analyticPlan.useGpu()) {
            for (var iterator = analyticBatches.iterator(); iterator.hasNext();) {
                VfxAnalyticParticlePool batch = iterator.next();
                batch.tick();
                if (batch.liveCount() == 0) {
                    batch.close();
                    iterator.remove();
                }
            }
        }
        int particleCount = particles.size();
        boolean hasExpiredParticles = false;
        updatingParticles = true;
        try {
            for (int i = 0; i < particleCount; i++) {
                ClientVfxParticle particle = particles.get(i);

                particle.tick(level, emitterContext);

                // Detached effects retain their already-live particles but
                // cannot re-enter the emitter graph through collision events.
                if (sourceAvailable) {
                    tickParticleTriggers(
                            level,
                            effectPosition,
                            particle,
                            emitterContext,
                            random,
                            events,
                            emittersById,
                            eventRuntime
                    );
                }
                hasExpiredParticles |= particle.isDead();
            }
        } finally {
            updatingParticles = false;
        }
        if (hasExpiredParticles) {
            particles.removeIf(ClientVfxParticle::isDead);
        }
        if (!pendingParticles.isEmpty()) {
            particles.addAll(pendingParticles);
            pendingParticles.clear();
        }

        if (canSpawn) {
            age++;
        }
        double elapsed = (System.nanoTime() - startedNs) / 1_000_000.0;
        simulationEmaMs = simulationEmaMs == 0 ? elapsed : simulationEmaMs * 0.9 + elapsed * 0.1;
    }

    /** Move existing local particles before this tick's new births and events. */
    public void setSource(VfxTransform previous, VfxTransform current, VfxTransform parent) {
        this.previousSourceTransform = previous;
        this.previousParentSourceTransform = sourceInitialized ? parentSourceTransform : parent;
        this.sourceInitialized = true;
        this.sourceTransform = current;
        this.parentSourceTransform = parent;
        if (definition.space() != VfxSpace.LOCAL) return;
        if (densePool != null) densePool.followSource(previous, current);
        if (previous.position().equals(current.position())
                && previous.rotation().equals(current.rotation())) return;
        for (ClientVfxParticle particle : particles) particle.followSource(previous, current);
        for (ClientVfxParticle particle : pendingParticles) particle.followSource(previous, current);
    }

    public VfxTransform sourceTransform() { return sourceTransform; }
    public Vec3 rotateParent(Vec3 offset) { return parentSourceTransform.rotate(offset); }

    /**
     * Sample the moving parent continuously and interpolate the emitter's own
     * offset/orientation independently. GPU birth positions are emitter-local.
     */
    public VfxTransform renderSourceTransform(VfxTransform renderedParent, float partialTick) {
        VfxTransform previousLocal = previousSourceTransform.relativeTo(previousParentSourceTransform);
        VfxTransform currentLocal = sourceTransform.relativeTo(parentSourceTransform);
        return renderedParent.compose(VfxTransform.interpolate(previousLocal, currentLocal, partialTick));
    }

    /** Called once per visible frame, not once per particle. */
    public void setRenderTransform(VfxTransform parent, float partialTick) {
        VfxTransform pose = renderSourceTransform(parent, partialTick);
        for (VfxAnalyticParticlePool batch : analyticBatches) batch.setSourceTransform(pose);
    }

    private void tickTriggers(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            RandomSource random,
            Map<String, VfxEventDefinition> events,
            Map<String, ClientVfxEmitter> emittersById,
            VfxLifetimeState lifetimeState,
            VfxEventRuntime eventRuntime
    ) {
        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.MANUAL) {
            return;
        }

        if (age == 0 && !creationTriggersFired) {
            creationTriggersFired = true;

            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.ON_CREATION) {
                    VfxEventRunner.run(trigger.eventId(), events, emittersById, level, effectPosition, emitterPosition, emitterContext, eventRuntime, random);
                }
            }
        }

        if (lifetimeState.active()) {
            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.TIMELINE
                        && age == trigger.timeTicks()) {
                    VfxEventRunner.run(trigger.eventId(), events, emittersById, level, effectPosition, emitterPosition, emitterContext, eventRuntime, random);
                }
            }
        }

        if (!expirationTriggersFired && lifetimeState.finished()) {
            expirationTriggersFired = true;

            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.ON_EXPIRATION) {
                    VfxEventRunner.run(trigger.eventId(), events, emittersById, level, effectPosition, emitterPosition, emitterContext, eventRuntime, random);
                }
            }
        }
    }

    private void tickParticleTriggers(
            ClientLevel level,
            Vec3 effectPosition,
            ClientVfxParticle particle,
            VfxExpressionContext emitterContext,
            RandomSource random,
            Map<String, VfxEventDefinition> events,
            Map<String, ClientVfxEmitter> emittersById,
            VfxEventRuntime eventRuntime
    ) {
        if (!particle.collidedThisTick() || collisionEvents == null) {
            return;
        }

        for (VfxTriggerDefinition trigger : definition.particleTriggers()) {
            if (trigger.type() != VfxTriggerType.ON_COLLISION) {
                continue;
            }
            collisionEvents.enqueue(trigger.eventId(), particle.collisionPosition());
        }
        // Drain before advancing to the next particle, preserving existing
        // same-tick set_param and random draw ordering. Spawn requests targeting
        // this emitter are staged in pendingParticles until its update finishes.
        collisionEvents.drain(events, emittersById, level, effectPosition,
                emitterContext, eventRuntime, random);
    }

    private void tickSpawnAmount(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            VfxLifetimeState lifetimeState,
            RandomSource random,
            VfxEventRuntime runtime
    ) {
        VfxSpawnAmountDefinition spawnAmount = definition.spawnAmount();

        if (spawnAmount.mode() == VfxSpawnAmountMode.INSTANT) {
            tickInstantSpawnAmount(level, effectPosition, emitterPosition, emitterContext, lifetimeState, random, runtime);
        }

        if (spawnAmount.mode() == VfxSpawnAmountMode.STEADY) {
            tickSteadySpawnAmount(level, effectPosition, emitterPosition, emitterContext, random, runtime);
        }
    }

    private void tickSteadySpawnAmount(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            RandomSource random,
            VfxEventRuntime runtime
    ) {
        double rate = definition.spawnAmount().rate().evaluateDouble(emitterContext);
        // A saturated emitter discards scheduled births instead of retaining
        // unbounded spawn debt that would later emerge as a delayed burst.
        if (!Double.isFinite(rate) || rate < 0.0) rate = 0.0;
        spawnAccumulator += (float) (rate / 20.0);

        int scheduled = (int) spawnAccumulator;
        if (!Float.isFinite(spawnAccumulator) || spawnAccumulator >= Integer.MAX_VALUE)
            spawnAccumulator = 0;
        else
            spawnAccumulator -= scheduled;

        int remaining = Math.max(0, maxParticles - liveParticleCount());
        int admitted = Math.min(scheduled, remaining);
        if (admitted > 0)
            spawnParticles(level, effectPosition, emitterPosition, emitterContext, random, admitted, runtime);
    }


    private void tickInstantSpawnAmount(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            VfxLifetimeState lifetimeState,
            RandomSource random,
            VfxEventRuntime runtime
    ) {
        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.LOOPING) {
            if (lifetimeState.localAge() != 0) {
                return;
            }

            if (lastInstantAge == age) {
                return;
            }

            lastInstantAge = age;

            spawnParticles(level, effectPosition, emitterPosition, emitterContext, random, instantAmount, runtime);
            return;
        }

        if (lastInstantAge >= 0) {
            return;
        }

        lastInstantAge = lifetimeState.localAge();

        spawnParticles(level, effectPosition, emitterPosition, emitterContext, random, instantAmount, runtime);
    }

    private void spawnParticles(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            RandomSource random,
            int count,
            VfxEventRuntime runtime
    ) {
        count = runtime.reserveSpawns(count, particleCostMask());
        if (analyticPlan.useGpu()) {
            if (count == 0) return;
            // A mutable lifetime is shared by particles born in this tick,
            // but later batches may see changed effect parameters.
            VfxAnalyticPlan birthPlan = analyticPlan.forBirth(definition, emitterContext);
            VfxAnalyticParticlePool batch = analyticBatches.isEmpty()
                    ? null : analyticBatches.getLast();
            if (batch == null || !batch.canAppendBirth(birthPlan, sourceTransform, emitterContext)) {
                // Preserve an empty primary cohort for delayed instant births,
                // but never reuse it once emitted. A new cohort owns its own age.
                batch = !analyticPrimarySpawned && analyticPool != null
                        && birthPlan == analyticPlan ? analyticPool
                        : new VfxAnalyticParticlePool(definition, birthPlan);
                analyticBatches.add(batch);
            }
            analyticPrimarySpawned = true;
            batch.spawn(count, emitterPosition, emitterContext, random, sourceTransform);
            emittedParticles += count;
            return;
        }
        for (int i = 0; i < count; i++) {
            double particleRandom = random.nextDouble();

            VfxExpressionContext preSpawnContext = ClientVfxExpressionContexts.particleSpawn(
                    emitterContext,
                    emitterPosition,
                    particleRandom
            );

            Vec3 particlePosition = VfxSpawnPositionSampler.sample(
                    emitterPosition,
                    definition.spawnShape(),
                    preSpawnContext,
                    random
            );
            Vec3 sampledPosition = particlePosition;
            particlePosition = emitterPosition.add(sourceTransform.rotate(
                    particlePosition.subtract(emitterPosition)));

            VfxExpressionContext particleSpawnContext = ClientVfxExpressionContexts.particleSpawn(
                    emitterContext,
                    particlePosition,
                    particleRandom
            );


            Vec3 velocity = VfxMotionSampler.sampleInitialVelocity(
                    definition.motion(),
                    emitterPosition,
                    sampledPosition,
                    particleSpawnContext,
                    random
            );
            velocity = sourceTransform.rotate(velocity);

            Vec3 rotation = VfxRotationSampler.sampleInitialRotation(
                    definition.rotation(),
                    particleSpawnContext
            );
            if (definition.render().type() == VfxRenderType.MODEL && sourceTransform.hasRotation()) {
                rotation = sourceTransform.rotateAngles(rotation);
            }

            Vec3 angularVelocity = VfxRotationSampler.sampleInitialAngularVelocity(
                    definition.rotation(),
                    particleSpawnContext
            );

            if (definition.render().type() != VfxRenderType.SPRITE
                    && definition.render().type() != VfxRenderType.MODEL) {
                emittedParticles++;
                continue;
            }

            int lifeTime = Math.max(1, (int) Math.round(definition.particleLifetime().maxAgeTicks().evaluateDouble(particleSpawnContext)));

            VfxExpressionContext initialRenderContext = ClientVfxExpressionContexts.particleTick(
                    emitterContext,
                    particlePosition,
                    particlePosition,
                    particlePosition,
                    velocity,
                    rotation,
                    angularVelocity,
                    0,
                    lifeTime,
                    particleRandom,
                    new Vec3(1.0, 1.0, 1.0)
            );

            Vec3 initialScale = definition.render().appearance().scale()
                    .evaluate(initialRenderContext)
                    .toVec3();

            VfxColor initialColor = definition.render().appearance().color()
                    .evaluate(initialRenderContext);

            if (densePool != null) {
                densePool.add(particlePosition, velocity, rotation, angularVelocity, lifeTime, particleRandom,
                        initialScale, initialColor, particleSpawnContext);
            } else {
                ClientVfxParticle particle = new ClientVfxParticle(
                        definition, particlePosition, velocity, rotation, angularVelocity,
                        lifeTime, particleRandom, initialScale, initialColor, particleSpawnContext
                );
                if (definition.space() == VfxSpace.LOCAL) particle.setLocalSource(sourceTransform);
                if (updatingParticles) {
                    pendingParticles.add(particle);
                } else {
                    particles.add(particle);
                }
            }

            emittedParticles++;
        }
    }

    public void emitManual(
            ClientLevel level,
            Vec3 effectPosition,
            Vec3 emitterPosition,
            VfxExpressionContext emitterContext,
            RandomSource random,
            VfxEventRuntime runtime
    ) {
        VfxSpawnAmountDefinition spawnAmount = definition.spawnAmount();

        int amount = Math.max(0, (int) Math.round(spawnAmount.amount().evaluateDouble(emitterContext)));

        if (amount == 0) {
            return;
        }

        spawnParticles(level, effectPosition, emitterPosition, emitterContext, random, amount, runtime);
    }

    public boolean isFinished() {
        if (definition.emitterLifetime().mode() == VfxEmitterLifetimeMode.MANUAL) {
            return liveParticleCount() == 0;
        }

        return lifetimeState().finished() && liveParticleCount() == 0;
    }

    public VfxEmitterDefinition definition() {
        return definition;
    }

    public List<ClientVfxParticle> particles() {
        if (densePool == null) {
            return Collections.unmodifiableList(particles);
        }
        // Public inspection compatibility; renderer and counters never use this slow path.
        return new java.util.AbstractList<>() {
            @Override public ClientVfxParticle get(int index) { return densePool.snapshot(index); }
            @Override public int size() { return densePool.size(); }
        };
    }

    public List<ClientVfxParticle> legacyParticles() {
        return Collections.unmodifiableList(particles);
    }

    public VfxDenseParticlePool densePool() {
        return densePool;
    }

    public VfxAnalyticParticlePool analyticPool() { return analyticPool; }
    public List<VfxAnalyticParticlePool> analyticPools() { return analyticBatches; }

    public VfxParticleCostClass particleCostClass() {
        if (analyticPlan.useGpu()) return VfxParticleCostClass.GPU_ANALYTIC;
        if (definition.motion().collision().collide()) return VfxParticleCostClass.CPU_COLLISION;
        if (definition.render().type() == VfxRenderType.MODEL) return VfxParticleCostClass.CPU_MODEL;
        return VfxParticleCostClass.CPU_BASIC;
    }

    /** The model and collision dimensions stack with the CPU simulation cost. */
    public int particleCostMask() {
        return costMaskFor(definition, analyticPlan.useGpu());
    }

    public static int costMaskFor(VfxEmitterDefinition definition, boolean gpuAnalytic) {
        int mask = gpuAnalytic ? VfxParticleCostClass.GPU_ANALYTIC.bit()
                : VfxParticleCostClass.CPU_BASIC.bit();
        if (!gpuAnalytic && definition.motion().collision().collide()) {
            mask |= VfxParticleCostClass.CPU_COLLISION.bit();
        }
        if (definition.render().type() == VfxRenderType.MODEL) {
            mask |= VfxParticleCostClass.CPU_MODEL.bit();
        }
        return mask;
    }

    public String selectedBackend() {
        return analyticPlan.useGpu() ? "gpu_analytic"
                : densePool != null ? "cpu_dense"
                : "cpu_legacy";
    }
    public String backendReason() {
        if (analyticPlan.useGpu()) return (definition.render().type() == VfxRenderType.MODEL
                ? "GPU-resident model births; " : "GPU-resident sprite births; ") + analyticPlan.reason();
        StringBuilder reason = new StringBuilder();
        reason.append(densePool == null ? "Legacy required: " : "SoA enabled: ");
        reason.append("motion=").append(definition.motion().mode().name().toLowerCase(java.util.Locale.ROOT));
        reason.append(", rotation=").append(definition.rotation().mode().name().toLowerCase(java.util.Locale.ROOT));
        if (definition.render().environmentLighting()) reason.append(", lighting=render-time");
        if (definition.motion().collision().collide()) reason.append(", collision=world queries");
        if (!definition.particleTriggers().isEmpty()) reason.append(", ordered particle events");
        if (definition.render().type() == VfxRenderType.MODEL) {
            reason.append(", model=").append(definition.render().model().source());
            reason.append("/").append(definition.render().model().renderLayer());
        } else if (definition.render().sprite() != null) {
            reason.append(", sprite=").append(definition.render().sprite().blendMode());
        }
        reason.append("; GPU analytic unavailable: ").append(analyticPlan.reason());
        return reason.toString();
    }
    public String selectedRenderer() { return lastRenderer; }
    public void recordRenderer(String renderer) { lastRenderer = renderer; }
    public double simulationEmaMs() { return simulationEmaMs; }
    public long allocatedParticleStateBytes() {
        long result = densePool == null ? 0 : densePool.allocatedStateBytes();
        for (var batch : analyticBatches) result += batch.allocatedStateBytes();
        return result;
    }
    public long gpuResidentBirthBytes() {
        long result = 0;
        for (var batch : analyticBatches) result += batch.residentBufferBytes();
        return result;
    }
    public void close() {
        for (VfxAnalyticParticlePool batch : analyticBatches) batch.close();
        analyticBatches.clear();
    }

    public int age() {
        return age;
    }

    public int activeTicks() {
        return activeTicks;
    }

    public int delayTicks() {
        return delayTicks;
    }

    public int emittedParticles() {
        return emittedParticles;
    }

    public int liveParticleCount() {
        int gpuCount = 0;
        for (VfxAnalyticParticlePool batch : analyticBatches) gpuCount += batch.liveCount();
        return particles.size() + pendingParticles.size()
                + (densePool == null ? 0 : densePool.size()) + gpuCount;
    }

    /** Sample an actual particle rather than the emitter origin. Emitters may
     * spawn at an offset, or travel into water while their source stays dry.
     * The sample is constant-cost for Dense and GPU-resident backends.
     */
    public Vec3 representativeParticlePosition(float partialTick) {
        for (var cohort : analyticBatches) {
            if (cohort.liveCount() == 0) continue;
            Vec3 position = cohort.representativeWorldPosition(partialTick);
            if (position != null) return position;
        }
        if (densePool != null && densePool.size() > 0)
            return densePool.interpolatedPosition(0, partialTick);
        if (!particles.isEmpty())
            return particles.getFirst().interpolatedPosition(partialTick);
        if (!pendingParticles.isEmpty())
            return pendingParticles.getFirst().interpolatedPosition(partialTick);
        return null;
    }

    /** Approximate billboard extent for foreground visibility. Used only
     * when determining whether a single large quad straddles transparent
     * blocks, not as a replacement for per-fragment transparency sorting.
     */
    public Vec3 representativeParticleScale(float partialTick) {
        for (var cohort : analyticBatches)
            if (cohort.liveCount() > 0) return cohort.scale();
        if (densePool != null && densePool.size() > 0)
            return densePool.interpolatedScale(0, partialTick);
        if (!particles.isEmpty())
            return particles.getFirst().interpolatedScale(partialTick);
        if (!pendingParticles.isEmpty())
            return pendingParticles.getFirst().interpolatedScale(partialTick);
        return Vec3.ZERO;
    }

    public VfxLifetimeState lifetimeState() {
        return VfxLifetimeRuntime.emitter(
                definition.emitterLifetime().mode(),
                age,
                delayTicks,
                activeTicks,
                sleepTicks,
                loops
        );
    }

    public double emitterRandom() {
        return emitterRandom;
    }
}
