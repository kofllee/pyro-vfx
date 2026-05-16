package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxLifetimeDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxTimelineEventDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxTriggerDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionNode;
import net.kofllee.pyrovfx.vfx.type.VfxEventType;
import net.kofllee.pyrovfx.vfx.type.VfxLifetimeMode;
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
    private final RandomSource random = RandomSource.create();

    private final int delayTicks;
    private final int activeTicks;
    private final int sleepTicks;
    private final int loops;

    private int age;
    private boolean creationTriggersFired = false;
    private boolean expirationTriggersFired = false;

    public ClientVfxInstance(VfxDefinition definition, Vec3 position){
        this.definition = definition;
        this.position = position;

        VfxExpressionContext effectStartContext = ClientVfxExpressionContexts.effectStart(position, random);
        VfxLifetimeDefinition lifetime = definition.lifetime();

        this.delayTicks = Math.max(0, (int) Math.round(lifetime.delayTicks().evaluate(effectStartContext)));
        this.activeTicks = Math.max(0, (int) Math.round(lifetime.activeTicks().evaluate(effectStartContext)));
        this.sleepTicks = Math.max(0, (int) Math.round(lifetime.sleepTicks().evaluate(effectStartContext)));
        this.loops = Math.max(0, (int) Math.round(lifetime.loops().evaluate(effectStartContext)));


        for(var emitterDef : definition.emitters()){
            ClientVfxEmitter emitter = new ClientVfxEmitter(emitterDef, position, effectStartContext, random);

            emitters.add(emitter);
            emittersById.put(emitterDef.id(), emitter);
        }
    }

    public void tick(ClientLevel level){
        VfxExpressionContext effectContext = ClientVfxExpressionContexts.effectTick(
                position,
                age,
                delayTicks,
                activeTicks
        );
        tickEffectTriggers(level, effectContext);
        
        boolean effectActive = isEffectActive();

        for (var emitter : emitters) {
            Vec3 emitterOffset = emitter.definition().offset().evaluate(effectContext).toVec3();
            Vec3 emitterPosition = position.add(emitterOffset);

            emitter.tick(level, position, emitterPosition, effectContext, isEffectActive(), definition.events(), emittersById, random);
        }

        age++;
    }

    private void tickEffectTriggers(ClientLevel level, VfxExpressionContext effectContext) {
        for (VfxTriggerDefinition trigger : definition.triggers()) {
            if (!shouldFireEffectTrigger(trigger)) {
                continue;
            }

            VfxEventRunner.run(
                    trigger.eventId(),
                    definition.events(),
                    emittersById,
                    level,
                    position,
                    position,
                    effectContext,
                    random
            );
        }

    }

    private boolean shouldFireEffectTrigger(VfxTriggerDefinition trigger) {
        if (trigger.type() == VfxTriggerType.ON_CREATION) {
            if (creationTriggersFired) {
                return false;
            }

            if (age != 0) {
                return false;
            }

            creationTriggersFired = true;
            return true;
        }

        if (trigger.type() == VfxTriggerType.TIMELINE) {
            return age == trigger.timeTicks();
        }

        if (trigger.type() == VfxTriggerType.ON_EXPIRATION) {
            if (expirationTriggersFired) {
                return false;
            }

            if (!isEffectLifetimeFinished()) {
                return false;
            }

            expirationTriggersFired = true;
            return true;
        }

        return false;
    }

    private void emitFrom(ClientLevel level, VfxExpressionContext effectContext, VfxTimelineEventDefinition event) {
        ClientVfxEmitter emitter = emittersById.get(event.emitterId());

        if(emitter == null){
            return;
        }

        Vec3 emitterOffset = emitter.definition().offset().evaluate(effectContext).toVec3();
        Vec3 emitterPosition = position.add(emitterOffset);

        VfxExpressionContext emitterContext = ClientVfxExpressionContexts.emitterTick(
                effectContext,
                emitterPosition,
                0,
                0,
                0,
                0
        );

        emitter.emitManual(level, position, emitterPosition, emitterContext, random);
    }

    private boolean isEffectActive() {
        if (age < delayTicks) {
            return false;
        }

        int localAge = age - delayTicks;

        if(definition.lifetime().mode() == VfxLifetimeMode.ONCE) {
            return localAge < activeTicks;
        }

        if(definition.lifetime().mode() == VfxLifetimeMode.LOOPING) {
            int cycleTicks = activeTicks + sleepTicks;

            if(cycleTicks <= 0){
                return false;
            }

            if(loops > 0){
                int completeCycles = localAge / cycleTicks;

                if(completeCycles >= loops){
                    return false;
                }
            }

            int cycleAge = localAge % cycleTicks;
            return cycleAge < activeTicks;
        }

        return false;
    }

    private boolean isEffectLifetimeFinished() {
        if(definition.lifetime().mode() == VfxLifetimeMode.ONCE) {
            return age >= delayTicks + activeTicks;
        }

        if(definition.lifetime().mode() == VfxLifetimeMode.LOOPING && loops > 0) {
            int cycleTicks = activeTicks + sleepTicks;
            return cycleTicks <= 0 || age >= delayTicks + cycleTicks * loops;
        }

        return false;
    }

    public boolean isFinished(){
        if (!isEffectLifetimeFinished()) {
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
}
