package net.kofllee.pyrovfx.client.vfx;

import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvResolver;
import net.kofllee.pyrovfx.client.render.ClientVfxSpriteUvState;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxMotionCollisionDefinition;
import net.kofllee.pyrovfx.vfx.expression.VfxExpressionContext;
import net.kofllee.pyrovfx.vfx.type.VfxCollisionType;
import net.kofllee.pyrovfx.vfx.type.VfxMotionMode;
import net.kofllee.pyrovfx.vfx.type.VfxRotationMode;
import net.kofllee.pyrovfx.vfx.value.VfxColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

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
    private boolean dead;

    private boolean collidedThisTick;
    private Vec3 collisionPosition;

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

    public void tick(ClientLevel level, VfxExpressionContext emitterContext){
        previousPosition = position;
        previousRotation = rotation;
        previousScale = scale;
        previousColor = color;

        collidedThisTick = false;
        collisionPosition = null;

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

        tickMotion(level, particleContext);
        tickRotation(particleContext);

        if (age == 0 && emitterDefinition.rotation().mode() == VfxRotationMode.PARAMETRIC) {
            previousRotation = rotation;
        }

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

    private void tickMotion(ClientLevel level, VfxExpressionContext particleContext){
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

            Vec3 nextPosition = position.add(velocity);
            if(emitterDefinition.motion().collision().collide()){
                applyCollision(level, particleContext, nextPosition);
                return;
            }

            position = nextPosition;
        }
    }

    private void applyCollision(ClientLevel level, VfxExpressionContext particleContext, Vec3 nextPosition) {
        VfxMotionCollisionDefinition collision = emitterDefinition.motion().collision();

        Vec3 collisionSize = collision.collisionSize().evaluate(particleContext).toVec3();

        AABB nextBox = createCollisionBox(nextPosition, collision.collisionType(), collisionSize);
        if (!intersectsBlockCollision(level, nextBox)) {
            position = nextPosition;
            return;
        }

        collidedThisTick = true;
        collisionPosition = nextPosition;

        double collisionDrag = Math.max(0.0, collision.collisionDrag().evaluate(particleContext));
        double bounciness = Math.max(0.0, collision.bounciness().evaluate(particleContext));

        CollisionMoveResult result = resolveMovementByAxis(
                level,
                position,
                velocity,
                collision.collisionType(),
                collisionSize,
                bounciness
        );

        position = result.position();

        if (!result.collided()) {
            return;
        }

        velocity = result.velocity();

        double dragMultiplier = Math.exp(-collisionDrag * VfxTime.SECONDS_PER_TICK);
        velocity = velocity.scale(dragMultiplier);

        collidedThisTick = true;
        collisionPosition = result.position();

        if (collision.expireOnContact()) {
            dead = true;
        }
    }

    private CollisionMoveResult resolveMovementByAxis(ClientLevel level, Vec3 position, Vec3 velocity, VfxCollisionType collisionType, Vec3 collisionSize, double bounciness) {
        Vec3 currentPosition = position;

        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;

        boolean collided = false;

        Vec3 xPosition = currentPosition.add(vx, 0.0, 0.0);
        AABB xBox = createCollisionBox(xPosition, collisionType, collisionSize);

        if (intersectsBlockCollision(level, xBox)) {
            vx = -vx * bounciness;
            collided = true;
        } else {
            currentPosition = xPosition;
        }

        Vec3 yPosition = currentPosition.add(0.0, vy, 0.0);
        AABB yBox = createCollisionBox(yPosition, collisionType, collisionSize);


        if (intersectsBlockCollision(level, yBox)) {
            vy = -vy * bounciness;
            collided = true;
        } else {
            currentPosition = yPosition;
        }

        Vec3 zPosition = currentPosition.add(0.0, 0.0, vz);
        AABB zBox = createCollisionBox(zPosition, collisionType, collisionSize);


        if (intersectsBlockCollision(level, zBox)) {
            vz = -vz * bounciness;
            collided = true;
        } else {
            currentPosition = zPosition;
        }

        return new CollisionMoveResult(
                currentPosition,
                new Vec3(vx, vy, vz),
                collided
        );
    }


    private boolean intersectsBlockCollision(ClientLevel level, AABB box) {
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    VoxelShape shape = level.getBlockState(mutablePos)
                            .getCollisionShape(level, mutablePos);

                    if (!shape.isEmpty() && shape.bounds().move(mutablePos).intersects(box)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private AABB createCollisionBox(Vec3 center, VfxCollisionType collisionType, Vec3 collisionSize) {
        double x = Math.max(0.001, Math.abs(collisionSize.x));
        double y = Math.max(0.001, Math.abs(collisionSize.y));
        double z = Math.max(0.001, Math.abs(collisionSize.z));

        if (collisionType == VfxCollisionType.SPHERE) {
            double radius = Math.max(x, Math.max(y, z));
            return new AABB(
                    center.x - radius,
                    center.y - radius,
                    center.z - radius,
                    center.x + radius,
                    center.y + radius,
                    center.z + radius
            );
        }

        return new AABB(
                center.x - x,
                center.y - y,
                center.z - z,
                center.x + x,
                center.y + y,
                center.z + z
        );
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
        return dead || age >= lifetime;
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

    public boolean collidedThisTick() {
        return collidedThisTick;
    }

    public Vec3 collisionPosition() {
        return collisionPosition == null ? position : collisionPosition;
    }

    private record CollisionMoveResult(
            Vec3 position,
            Vec3 velocity,
            boolean collided
    ){

    }
}
