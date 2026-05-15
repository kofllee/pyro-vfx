package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxLifetimeDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxLifetimeMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ClientVfxInstance {
    private final VfxDefinition definition;
    private final Vec3 position;
    private final List<ClientVfxEmitter> emitters = new ArrayList<>();
    private final RandomSource random = RandomSource.create();

    private final int delayTicks;
    private final int activeTicks;
    private final int sleepTicks;
    private final int loops;

    private final List<ClientVfxParticle> particles = new ArrayList<>();

    private int age;

    public ClientVfxInstance(VfxDefinition definition, Vec3 postion){
        this.definition = definition;
        this.position = postion;

        VfxExpressionContext effectStartContext = ClientVfxExpressionContexts.effectStart(position, random);
        VfxLifetimeDefinition lifetime = definition.lifetime();

        this.delayTicks = Math.max(0, (int) Math.round(lifetime.delayTicks().evaluate(effectStartContext)));
        this.activeTicks = Math.max(0, (int) Math.round(lifetime.activeTicks().evaluate(effectStartContext)));
        this.sleepTicks = Math.max(0, (int) Math.round(lifetime.sleepTicks().evaluate(effectStartContext)));
        this.loops = Math.max(0, (int) Math.round(lifetime.loops().evaluate(effectStartContext)));


        for(var emitterDef : definition.emitters()){
            emitters.add(new ClientVfxEmitter(emitterDef, position, effectStartContext, random));
        }
    }

    public void tick(ClientLevel level){
        VfxExpressionContext effectContext = ClientVfxExpressionContexts.effectTick(
                position,
                age,
                delayTicks,
                activeTicks
        );

        if (isEffectActive()) {
            for (var emitter : emitters) {
                particles.addAll(emitter.tick(level, position, position, effectContext, random));
            }

            for(var particleIterator = particles.iterator(); particleIterator.hasNext(); ){
                ClientVfxParticle particle = particleIterator.next();

                particle.tick(effectContext);

                if(particle.isDead()){
                    particleIterator.remove();
                }
            }
        }

        age++;
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

    public boolean isFinished(){
        if(definition.lifetime().mode() == VfxLifetimeMode.ONCE) {
            return age >= delayTicks + activeTicks
                    && emitters.stream().allMatch(ClientVfxEmitter::isFinished)
                    && particles.isEmpty();
        }

        if(definition.lifetime().mode() == VfxLifetimeMode.LOOPING && loops > 0) {
            int cycleTicks = activeTicks + sleepTicks;
            return (cycleTicks <= 0 || age >= delayTicks + cycleTicks * loops)
                    && particles.isEmpty();
        }

        return false;
    }

    public VfxDefinition definition() {
        return definition;
    }
}
