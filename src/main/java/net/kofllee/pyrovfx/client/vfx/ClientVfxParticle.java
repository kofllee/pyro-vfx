package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxMotionMode;
import net.minecraft.world.phys.Vec3;

public final class ClientVfxParticle {
    private final VfxEmitterDefinition emitterDefinition;
    private final Vec3 spawnPosition;
    private final int lifetime;
    private final double random;

    private Vec3 position;
    private Vec3 previousPosition;
    private Vec3 velocity;

    private int age;

    public ClientVfxParticle(
            VfxEmitterDefinition definition,
            Vec3 position,
            Vec3 velocity,
            int lifetime,
            double random
    )
    {
        this.emitterDefinition = definition;
        this.spawnPosition = position;
        this.position = position;
        this.previousPosition = spawnPosition;
        this.velocity = velocity;
        this.lifetime = Math.max(1, lifetime);
        this.random = random;
    }

    public void tick(VfxExpressionContext emitterContext){
        previousPosition = position;

        VfxExpressionContext particleContext = ClientVfxExpressionContexts.particleTick(
                emitterContext,
                spawnPosition,
                position,
                previousPosition,
                velocity,
                age,
                lifetime,
                random,
                1.0,
                1.0,
                0.0
        );

        tickMotion(particleContext);

        age++;
    }

    private void tickMotion(VfxExpressionContext particleContext){
        if(emitterDefinition.motion().mode() == VfxMotionMode.STATIC)
            return;

        if (emitterDefinition.motion().mode() == VfxMotionMode.PARAMETRIC){
            Vec3 offset = emitterDefinition.motion().parametric().offset().evaluate(particleContext).toVec3();

            position = spawnPosition.add(offset);
            return;
        }

        if (emitterDefinition.motion().mode() == VfxMotionMode.DYNAMIC){
            Vec3 acceleration = emitterDefinition.motion().dynamic().acceleration().evaluate(particleContext).toVec3();

            double drag = emitterDefinition.motion().dynamic().linearDrag().evaluate(particleContext);

            drag = Math.clamp(drag, 0.0, 1.0);

            velocity = velocity.add(acceleration).scale(1.0 - drag);
            position = position.add(velocity);
        }
    }

    public boolean isDead(){
        return age >= lifetime;
    }

    public Vec3 position(){
        return position;
    }

    public Vec3 velocity(){
        return velocity;
    }
}
