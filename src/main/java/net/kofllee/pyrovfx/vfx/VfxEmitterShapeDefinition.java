package net.kofllee.pyrovfx.vfx;

public record VfxEmitterShapeDefinition (VfxEmitterShape shape, VfxVec3 offset, double radius, VfxVec3 halfExtents, boolean surfaceOnly) {
    public static VfxEmitterShapeDefinition point(VfxVec3 offset){
        return new VfxEmitterShapeDefinition(VfxEmitterShape.POINT, offset, 0, VfxVec3.ZERO, false);
    }

    public static VfxEmitterShapeDefinition sphere(VfxVec3 offset, double radius, boolean surfaceOnly){
        return new VfxEmitterShapeDefinition(VfxEmitterShape.SPHERE, offset, radius, VfxVec3.ZERO, surfaceOnly);
    }

    public static VfxEmitterShapeDefinition box(VfxVec3 offset, VfxVec3 halfExtents, boolean surfaceOnly){
        return new VfxEmitterShapeDefinition(VfxEmitterShape.BOX, offset, 0, halfExtents, surfaceOnly);
    }
}
