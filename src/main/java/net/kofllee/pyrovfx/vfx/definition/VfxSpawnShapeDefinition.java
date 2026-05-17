package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnShapeType;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxSpawnShapeDefinition(VfxSpawnShapeType type, VfxNumberExpression radius, VfxVec3Expression halfExtents, VfxNumberExpression edgeThickness) {
    public static VfxSpawnShapeDefinition point() {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.POINT,
                VfxNumberExpression.constant(0.0),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxNumberExpression.constant(0.0)
        );
    }

    public static VfxSpawnShapeDefinition sphere(
            VfxNumberExpression radius,
            VfxNumberExpression edgeThickness
    ) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.SPHERE,
                radius,
                VfxVec3Expression.constant(VfxVec3.ZERO),
                edgeThickness
        );
    }

    public static VfxSpawnShapeDefinition box(
            VfxVec3Expression halfExtents,
            VfxNumberExpression edgeThickness
    ) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.BOX,
                VfxNumberExpression.constant(0.0),
                halfExtents,
                edgeThickness
        );
    }
}
