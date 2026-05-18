package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvResolver;
import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvState;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxMotionMode;
import net.kofllee.pyrovfx.vfx.type.VfxRotationMode;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.world.phys.Vec3;

import static org.joml.Math.lerp;

public final class ClientVfxParticle {
    private final VfxEmitterDefinition emitterDefinition;
    private final Vec3 spawnPosition;
    private final int lifetime;
    private final double random;

    private Vec3 position;
    private Vec3 previousPosition;
    private Vec3 velocity;

    private Vec3 rotation;
    private Vec3 previousRotation;
    private Vec3 angularVelocity;

    private Vec3 scale;
    private Vec3 previousScale;
    private VfxColor color;
    private VfxColor previousColor;

    private int age;

    private ClientVfxSpriteUvState spriteUv;

    public ClientVfxParticle(
            VfxEmitterDefinition definition,
            Vec3 position,
            Vec3 velocity,
            Vec3 rotation,
            Vec3 angularVelocity,
            int lifetime,
            double random,
            Vec3 initialScale,
            VfxColor initialColor,
            VfxExpressionContext particleSpawnContext
    )
    {
        this.emitterDefinition = definition;
        this.spawnPosition = position;
        this.position = position;
        this.previousPosition = spawnPosition;
        this.velocity = velocity;
        this.rotation = rotation;
        this.previousRotation = rotation;
        this.angularVelocity = angularVelocity;
        this.lifetime = Math.max(1, lifetime);
        this.random = random;
        this.scale = initialScale;
        this.previousScale = initialScale;
        this.color = initialColor;
        this.previousColor = initialColor;

        this.spriteUv = definition.render().sprite() == null
                ? ClientVfxSpriteUvState.full()
                : ClientVfxSpriteUvResolver.resolve(
                definition.render().sprite().uv(),
                particleSpawnContext,
                random
        );
    }

    public void tick(VfxExpressionContext emitterContext){
        previousPosition = position;
        previousRotation = rotation;
        previousScale = scale;
        previousColor = color;

        VfxExpressionContext particleContext = ClientVfxExpressionContexts.particleTick(
                emitterContext,
                spawnPosition,
                position,
                previousPosition,
                velocity,
                rotation,
                angularVelocity,
                age,
                lifetime,
                random,
                scale
        );

        tickMotion(particleContext);
        tickRotation(particleContext);

        VfxExpressionContext renderContext = ClientVfxExpressionContexts.particleTick(
                emitterContext,
                spawnPosition,
                position,
                previousPosition,
                velocity,
                rotation,
                angularVelocity,
                age,
                lifetime,
                random,
                scale
        );

        scale = emitterDefinition.render().appearance().scale().evaluate(renderContext).toVec3();
        color = emitterDefinition.render().appearance().color().evaluate(renderContext);

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
            Vec3 accelerationPerSecondSquared = emitterDefinition.motion().dynamic().acceleration().evaluate(particleContext).toVec3();

            Vec3 acceleration = accelerationPerSecondSquared.scale(VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK);

            double drag = emitterDefinition.motion().dynamic().linearDrag().evaluate(particleContext);

            double dragMultiplier = Math.exp(-drag * VfxTime.SECONDS_PER_TICK);

            velocity = velocity.add(acceleration).scale(dragMultiplier);
            position = position.add(velocity);
        }
    }

    private void tickRotation(VfxExpressionContext particleContext){
        if(emitterDefinition.rotation().mode() == VfxRotationMode.NONE)
            return;

        if (emitterDefinition.rotation().mode() == VfxRotationMode.PARAMETRIC){
            rotation = emitterDefinition.rotation().parametric().rotation().evaluate(particleContext).toVec3();
            return;
        }

        if(emitterDefinition.rotation().mode() == VfxRotationMode.DYNAMIC){
            Vec3 angularAccelerationPerSecondSquared = emitterDefinition.rotation().dynamic().angularAcceleration().evaluate(particleContext).toVec3();

            Vec3 angularAcceleration = angularAccelerationPerSecondSquared.scale(VfxTime.SECONDS_PER_TICK * VfxTime.SECONDS_PER_TICK);

            double angularDrag = emitterDefinition.rotation().dynamic().angularDrag().evaluate(particleContext);

            double dragMultiplier = Math.exp(-angularDrag * VfxTime.SECONDS_PER_TICK);

            angularVelocity = angularVelocity.add(angularAcceleration).scale(dragMultiplier);
            rotation = rotation.add(angularVelocity);
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

    public VfxEmitterDefinition emitterDefinition(){
        return emitterDefinition;
    }

    public double ageNormalized(){
        return lifetime <= 0 ? 1.0 : Math.min(1.0, age / (double) lifetime);
    }

    public Vec3 scale(){
        return scale;
    }

    public VfxColor color(){
        return color;
    }

    public Vec3 previousPosition(){
        return previousPosition;
    }

    public Vec3 interpolatedPosition(float partialTick) {
        return previousPosition.lerp(position, partialTick);
    }

    public Vec3 rotation(){
        return rotation;
    }

    public Vec3 previousRotation(){
        return previousRotation;
    }

    public Vec3 interpolatedRotation(float partialTick) {
        return previousRotation.lerp(rotation, partialTick);
    }

    public Vec3 interpolatedScale(float partialTick) {
        return previousScale.lerp(scale, partialTick);
    }

    public VfxColor interpolatedColor(float partialTick) {
        return new VfxColor(
                lerp(previousColor.r(), color.r(), partialTick),
                lerp(previousColor.g(), color.g(), partialTick),
                lerp(previousColor.b(), color.b(), partialTick),
                lerp(previousColor.a(), color.a(), partialTick)
        );
    }

    public int age() {
        return age;
    }

    public double random() {
        return random;
    }

    public ClientVfxSpriteUvState spriteUv() {
        return spriteUv;
    }
}
