package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxLifetimeDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxParameterDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxTriggerDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxTriggerType;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class ClientVfxInstance {
    private final VfxDefinition definition;
    private Vec3 position;
    private VfxTransformProvider provider;
    private VfxTransform previousTransform;
    private VfxTransform currentTransform;
    private boolean sourceLost;
    private final VfxTravelTriggers travelTriggers;
    private final List<VfxTravelTriggers.Hit> travelHits = new ArrayList<>();
    private final List<ClientVfxEmitter> emitters = new ArrayList<>();
    private final Map<String, ClientVfxEmitter> emittersById = new HashMap<>();
    private final Map<String, Double> parameters;
    private final int[] parameterSlots;
    private final double[] parameterValues;
    private final Map<String, Integer> parameterIndices;
    private final RandomSource random;
    private final VfxRuntimeBudget budget = new VfxRuntimeBudget();
    private final VfxEventRuntime eventRuntime = new VfxEventRuntime() {
        @Override
        public void setParameter(String id, double value) {
            ClientVfxInstance.this.setParameter(id, value);
        }

        @Override
        public boolean enterEvent() {
            return budget.enterEvent();
        }

        @Override
        public boolean hasEventBudget() {
            return budget.hasEventBudget();
        }

        @Override
        public int reserveSpawns(int requested) {
            return budget.reserveSpawns(requested);
        }

        @Override
        public int reserveSpawns(int requested, VfxParticleCostClass kind) {
            return budget.reserveSpawns(requested, kind);
        }

        @Override
        public int reserveSpawns(int requested, int costMask) {
            return budget.reserveSpawns(requested, costMask);
        }
    };

    private final int delayTicks;
    private final int activeTicks;
    private final int sleepTicks;
    private final int loops;
    private final double effectRandom;

    private int age;
    private double lastCpuTickMs;
    private double cpuTickEmaMs;
    private boolean creationTriggersFired = false;
    private boolean expirationTriggersFired = false;

    private VfxLifetimeState lifetimeState() {
        return VfxLifetimeRuntime.effect(
                definition.lifetime().mode(),
                age,
                delayTicks,
                activeTicks,
                sleepTicks,
                loops
        );
    }

    public ClientVfxInstance(VfxDefinition definition, Vec3 position, VfxPlayOptions options){
        this(definition, VfxTransformProvider.fixed(position), options, RandomSource.create());
    }

    public ClientVfxInstance(VfxDefinition definition, Vec3 position, VfxPlayOptions options, long seed) {
        this(definition, VfxTransformProvider.fixed(position), options, RandomSource.create(seed));
    }

    public ClientVfxInstance(VfxDefinition definition, VfxTransformProvider provider, VfxPlayOptions options) {
        this(definition, provider, options, RandomSource.create());
    }

    public ClientVfxInstance(VfxDefinition definition, VfxTransformProvider provider,
                             VfxPlayOptions options, long seed) {
        this(definition, provider, options, RandomSource.create(seed));
    }

    private ClientVfxInstance(VfxDefinition definition, VfxTransformProvider provider, VfxPlayOptions options, RandomSource random) {
        this.definition = definition;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.currentTransform = provider.get(1.0f);
        if (currentTransform == null)
            throw new IllegalStateException("Transform provider unavailable at effect creation");
        this.previousTransform = currentTransform;
        this.position = currentTransform.position();
        Vec3 position = this.position;
        this.travelTriggers = new VfxTravelTriggers(definition.triggers());
        this.random = random;

        this.effectRandom = random.nextDouble();

        VfxExpressionContext parameterStartContext = ClientVfxExpressionContexts.effectStart(
                position,
                effectRandom,
                Map.of(),
                definition.curves(),
                random,
                definition.program().variables()
        );

        this.parameters = resolveParameters(definition, parameterStartContext, options);

        var bindings = definition.program().parameterBindings();
        parameterSlots = new int[bindings.size()];
        parameterValues = new double[bindings.size()];
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < bindings.size(); i++) {
            var binding = bindings.get(i);
            parameterSlots[i] = binding.slot();
            parameterValues[i] = parameters.getOrDefault(binding.id(), 0.0);
            indices.put(binding.id(), i);
        }
        parameterIndices = Map.copyOf(indices);

        VfxExpressionContext effectStartContext = ClientVfxExpressionContexts.effectStart(position, effectRandom,
                parameters, definition.curves(), random, definition.program().variables());

        VfxLifetimeDefinition lifetime = definition.lifetime();

        this.delayTicks = Math.max(0, (int) Math.round(lifetime.delayTicks().evaluateDouble(effectStartContext)));
        this.activeTicks = Math.max(0, (int) Math.round(lifetime.activeTicks().evaluateDouble(effectStartContext)));
        this.sleepTicks = Math.max(0, (int) Math.round(lifetime.sleepTicks().evaluateDouble(effectStartContext)));
        this.loops = Math.max(0, (int) Math.round(lifetime.loops().evaluateDouble(effectStartContext)));

        for(var emitterDef : definition.program().emitters()){
            Vec3 emitterOffset = emitterDef.offset().evaluate(effectStartContext).toVec3();
            Vec3 emitterAngles = emitterDef.emitterRotation().evaluate(effectStartContext).toVec3();
            VfxTransform emitterPose = currentTransform.child(emitterOffset, emitterAngles);
            Vec3 emitterStartPosition = emitterPose.position();

            ClientVfxEmitter emitter = new ClientVfxEmitter(
                    emitterDef,
                    emitterStartPosition,
                    effectStartContext,
                    random
            );

            emitters.add(emitter);
            emitter.setSource(emitterPose, emitterPose, currentTransform);
            emittersById.put(emitterDef.id(), emitter);
        }
    }

    public ClientVfxInstance(VfxDefinition definition, Vec3 position) {
        this(definition, position, VfxPlayOptions.empty());
    }

    private static Map<String, Double> resolveParameters(
            VfxDefinition definition,
            VfxExpressionContext effectStartContext,
            VfxPlayOptions options
    ) {
        Map<String, Double> result = new HashMap<>();

        for (VfxParameterDefinition parameter : definition.parameters().values()) {
            result.put(parameter.id(), parameter.value().evaluateDouble(effectStartContext));
        }

        for (Map.Entry<String, Double> entry : options.parameters().entrySet()) {
            if (!definition.parameters().containsKey(entry.getKey())) {
                continue;
            }

            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public void tick(ClientLevel level){
        tick(level, null);
    }

    public void tick(ClientLevel level, VfxGlobalBudget global){
        previousTransform = currentTransform;
        if (!sourceLost) {
            VfxTransform resolved = provider.get(1.0f);
            if (resolved == null) {
                sourceLost = true;
                provider = null; // release callback captures as soon as the source disappears
            } else {
                currentTransform = resolved;
                position = resolved.position();
            }
        }
        int liveParticles = 0;
        for (var emitter : emitters) {
            liveParticles += emitter.liveParticleCount();
        }
        boolean heavy = !net.neoforged.fml.loading.FMLEnvironment.production
                && Boolean.getBoolean("pyrovfx.bench.allowHeavy");
        budget.beginTick(liveParticles, heavy, global);
        if (global != null) {
            budget.qualityScale(VfxCostAdmissionPolicy.scale(
                    global.adaptiveSpawnScale(), cpuTickEmaMs,
                    VfxBudgetConfig.cpuTickTargetMs(),
                    ClientVfxManager.getActiveInstanceCount(), definition.metadata().priority()));
        } else {
            budget.qualityScale(1.0);
        }
        VfxLifetimeState lifetimeState = lifetimeState();

        VfxExpressionContext effectContext = ClientVfxExpressionContexts.effectTick(
                position,
                lifetimeState,
                effectRandom,
                parameterSlots,
                parameterValues,
                definition.curves(),
                random,
                definition.program().variables()
        );

        // Once an attachment disappears, freeze the final emitter pose.
        // Effect-age-dependent offsets must not keep dragging detached live
        // particles around as they finish their remaining lifetime.
        if (!sourceLost) {
            for (var emitter : emitters) {
                Vec3 offset = emitter.definition().offset().evaluate(effectContext).toVec3();
                Vec3 angles = emitter.definition().emitterRotation().evaluate(effectContext).toVec3();
                VfxTransform emitterPose = currentTransform.child(offset, angles);
                emitter.setSource(emitter.sourceTransform(), emitterPose, currentTransform);
            }
        }

        if (!sourceLost) {
            tickEffectTriggers(level, effectContext, lifetimeState);
            travelHits.clear();
            travelTriggers.advance(previousTransform.position(), currentTransform.position(),
                    (trigger, at) -> travelHits.add(new VfxTravelTriggers.Hit(trigger, at)));
            for (var hit : travelHits) {
                runEffectTrigger(level, effectContext, hit.trigger(), hit.at());
            }
            travelHits.clear();
            tickEffectExpirationTriggers(level, effectContext, lifetimeState);
        }

        for (var emitter : emitters) {
            Vec3 emitterPosition = emitter.sourceTransform().position();

            emitter.tick(level, position, emitterPosition, effectContext, !sourceLost && lifetimeState.active(), !sourceLost,
                    definition.program().events(), emittersById, eventRuntime, random);
        }

        age++;
    }

    private void tickEffectTriggers(ClientLevel level, VfxExpressionContext effectContext, VfxLifetimeState lifetimeState) {
        if (age == 0 && !creationTriggersFired) {
            creationTriggersFired = true;

            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.ON_CREATION) {
                    runEffectTrigger(level, effectContext, trigger, position);
                }
            }
        }

        if (lifetimeState.active()) {
            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.TIMELINE
                        && age == trigger.timeTicks()) {
                    runEffectTrigger(level, effectContext, trigger, position);
                }
            }
        }
    }

    private void tickEffectExpirationTriggers(ClientLevel level, VfxExpressionContext effectContext, VfxLifetimeState lifetimeState) {
        if (expirationTriggersFired) {
            return;
        }

        if (!lifetimeState.finished()) {
            return;
        }

        expirationTriggersFired = true;

        for (VfxTriggerDefinition trigger : definition.triggers()) {
            if (trigger.type() == VfxTriggerType.ON_EXPIRATION) {
                runEffectTrigger(level, effectContext, trigger, position);
            }
        }
    }

    private void runEffectTrigger(
            ClientLevel level,
            VfxExpressionContext effectContext,
            VfxTriggerDefinition trigger,
            Vec3 eventPosition
    ) {
        VfxEventRunner.run(
                trigger.eventId(),
                definition.program().events(),
                emittersById,
                level,
                position,
                eventPosition,
                effectContext,
                eventRuntime,
                random
        );
    }

    public boolean isFinished() {
        if (sourceLost) return hasNoParticles();
        if (!lifetimeState().finished()) {
            return false;
        }

        return emitters.stream().allMatch(ClientVfxEmitter::isFinished)
                && hasNoParticles();
    }

    public VfxDefinition definition() {
        return definition;
    }

    /** Cost sampled in the manager; never attributes other mod or GPU work. */
    void recordCpuTime(long elapsedNs) {
        lastCpuTickMs = Math.max(0.0, elapsedNs / 1_000_000.0);
        cpuTickEmaMs += 0.1 * (lastCpuTickMs - cpuTickEmaMs);
    }
    public double cpuTickEmaMs() { return cpuTickEmaMs; }
    public double lastCpuTickMs() { return lastCpuTickMs; }

    private boolean hasNoParticles() {
        for (ClientVfxEmitter emitter : emitters) {
            if (emitter.liveParticleCount() != 0) {
                return false;
            }
        }

        return true;
    }

    public List<ClientVfxParticle> particles() {
        List<ClientVfxParticle> result = new ArrayList<>();

        for (ClientVfxEmitter emitter : emitters) {
            result.addAll(emitter.particles());
        }

        return Collections.unmodifiableList(result);
    }

    public int particleCount() {
        int count = 0;
        for (ClientVfxEmitter emitter : emitters) {
            count += emitter.liveParticleCount();
        }
        return count;
    }

    public List<ClientVfxEmitter> emitters() {
        return Collections.unmodifiableList(emitters);
    }

    public void close() {
        provider = null;
        for (ClientVfxEmitter emitter : emitters) emitter.close();
    }

    /** Render-only interpolation; no mutations or gameplay events in this method. */
    public VfxTransform renderTransform(float partialTick) {
        if (!sourceLost && provider != null) {
            VfxTransform sampled = provider.get(partialTick);
            if (sampled != null) return sampled;
        }
        return VfxTransform.interpolate(previousTransform, currentTransform, partialTick);
    }

    public void setParameter(String id, double value) {
        if (!definition.parameters().containsKey(id)) {
            return;
        }

        parameters.put(id, value);
        Integer index = parameterIndices.get(id);
        if (index != null) {
            parameterValues[index] = value;
        }
    }

    public Map<String, Double> parameters() {
        return Collections.unmodifiableMap(parameters);
    }
}
