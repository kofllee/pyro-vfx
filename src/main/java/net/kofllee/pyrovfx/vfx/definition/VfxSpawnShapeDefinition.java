package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxSpawnShapeType;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxSpawnShapeDefinition(VfxSpawnShapeType type, VfxVec3 offset, double radius, VfxVec3 halfExtents, double edgeThickness) {
    public static VfxSpawnShapeDefinition point(VfxVec3 offset){
        return new VfxSpawnShapeDefinition(VfxSpawnShapeType.POINT, offset, 0, VfxVec3.ZERO, 0);
    }

    public static VfxSpawnShapeDefinition sphere(VfxVec3 offset, double radius, double edgeThickness){
        return new VfxSpawnShapeDefinition(VfxSpawnShapeType.SPHERE, offset, radius, VfxVec3.ZERO, Math.clamp(edgeThickness, 0.0, 1.0));
    }

    public static VfxSpawnShapeDefinition box(VfxVec3 offset, VfxVec3 halfExtents, double edgeThickness) {
        return new VfxSpawnShapeDefinition(VfxSpawnShapeType.BOX, offset, 0, halfExtents, Math.clamp(edgeThickness, 0.0, 1.0));
    }
}
