package net.kofllee.pyrovfx.vfx;

public record VfxEmitterShapeDefinition (VfxEmitterShape shape, VfxVec3 offset, double radius, VfxVec3 halfExtents, double edgeThickness) {
    public static VfxEmitterShapeDefinition point(VfxVec3 offset){
        return new VfxEmitterShapeDefinition(VfxEmitterShape.POINT, offset, 0, VfxVec3.ZERO, 1.0);
    }

    public static VfxEmitterShapeDefinition sphere(VfxVec3 offset, double radius, double edgeThickness){
        return new VfxEmitterShapeDefinition(VfxEmitterShape.SPHERE, offset, radius, VfxVec3.ZERO, Math.clamp(edgeThickness, 0.0, 1.0));
    }

    public static VfxEmitterShapeDefinition box(VfxVec3 offset, VfxVec3 halfExtents, double edgeThickness) {
        return new VfxEmitterShapeDefinition(VfxEmitterShape.BOX, offset, 0, halfExtents, Math.clamp(edgeThickness, 0.0, 1.0));
    }
}
