package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxLifetimeDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxParameterDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxTriggerDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxTriggerType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class ClientVfxInstance {
    private final VfxDefinition definition;
    private final Vec3 position;
    private final List<ClientVfxEmitter> emitters = new ArrayList<>();
    private final Map<String, ClientVfxEmitter> emittersById = new HashMap<>();
    private final Map<String, Double> parameters;
    private final RandomSource random = RandomSource.create();

    private final int delayTicks;
    private final int activeTicks;
    private final int sleepTicks;
    private final int loops;
    private final double effectRandom;

    private int age;
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
        this.definition = definition;
        this.position = position;

        this.effectRandom = random.nextDouble();

        VfxExpressionContext parameterStartContext = ClientVfxExpressionContexts.effectStart(
                position,
                effectRandom,
                Map.of(),
                definition.curves(),
                random
        );

        this.parameters = resolveParameters(definition, parameterStartContext, options);

        VfxExpressionContext effectStartContext = ClientVfxExpressionContexts.effectStart(position, effectRandom, parameters, definition.curves(), random);

        VfxLifetimeDefinition lifetime = definition.lifetime();

        this.delayTicks = Math.max(0, (int) Math.round(lifetime.delayTicks().evaluate(effectStartContext)));
        this.activeTicks = Math.max(0, (int) Math.round(lifetime.activeTicks().evaluate(effectStartContext)));
        this.sleepTicks = Math.max(0, (int) Math.round(lifetime.sleepTicks().evaluate(effectStartContext)));
        this.loops = Math.max(0, (int) Math.round(lifetime.loops().evaluate(effectStartContext)));

        for(var emitterDef : definition.emitters()){
            Vec3 emitterOffset = emitterDef.offset().evaluate(effectStartContext).toVec3();
            Vec3 emitterStartPosition = position.add(emitterOffset);

            ClientVfxEmitter emitter = new ClientVfxEmitter(
                    emitterDef,
                    emitterStartPosition,
                    effectStartContext,
                    random
            );

            emitters.add(emitter);
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
            result.put(parameter.id(), parameter.value().evaluate(effectStartContext));
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
        VfxLifetimeState lifetimeState = lifetimeState();

        VfxExpressionContext effectContext = ClientVfxExpressionContexts.effectTick(
                position,
                lifetimeState,
                effectRandom,
                parameters,
                definition.curves(),
                random
        );

        tickEffectTriggers(level, effectContext, lifetimeState);
        tickEffectExpirationTriggers(level, effectContext, lifetimeState);

        for (var emitter : emitters) {
            Vec3 emitterOffset = emitter.definition().offset().evaluate(effectContext).toVec3();
            Vec3 emitterPosition = position.add(emitterOffset);

            emitter.tick(level, position, emitterPosition, effectContext, lifetimeState.active(), definition.events(), emittersById, this::setParameter, random);
        }

        age++;
    }

    private void tickEffectTriggers(ClientLevel level, VfxExpressionContext effectContext, VfxLifetimeState lifetimeState) {
        if (age == 0 && !creationTriggersFired) {
            creationTriggersFired = true;

            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.ON_CREATION) {
                    runEffectTrigger(level, effectContext, trigger);
                }
            }
        }

        if (lifetimeState.active()) {
            for (VfxTriggerDefinition trigger : definition.triggers()) {
                if (trigger.type() == VfxTriggerType.TIMELINE
                        && age == trigger.timeTicks()) {
                    runEffectTrigger(level, effectContext, trigger);
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
                runEffectTrigger(level, effectContext, trigger);
            }
        }
    }

    private void runEffectTrigger(
            ClientLevel level,
            VfxExpressionContext effectContext,
            VfxTriggerDefinition trigger
    ) {
        VfxEventRunner.run(
                trigger.eventId(),
                definition.events(),
                emittersById,
                level,
                position,
                position,
                effectContext,
                this::setParameter,
                random
        );
    }

    public boolean isFinished() {
        if (!lifetimeState().finished()) {
            return false;
        }

        return emitters.stream().allMatch(ClientVfxEmitter::isFinished)
                && hasNoParticles();
    }

    public VfxDefinition definition() {
        return definition;
    }

    private boolean hasNoParticles() {
        for (ClientVfxEmitter emitter : emitters) {
            if (!emitter.particles().isEmpty()) {
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

    public void setParameter(String id, double value) {
        if (!definition.parameters().containsKey(id)) {
            return;
        }

        parameters.put(id, value);
    }

    public Map<String, Double> parameters() {
        return Collections.unmodifiableMap(parameters);
    }
}
