package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxEvaluationMode;
import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnShapeType;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public record VfxSpawnShapeDefinition(VfxSpawnShapeType type, VfxVec3Expression offset, VfxNumberExpression radius, VfxVec3Expression halfExtents, VfxNumberExpression edgeThickness) {
    public static VfxSpawnShapeDefinition point(VfxVec3Expression offset) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.POINT,
                offset,
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_TICK),
                VfxVec3Expression.constant(VfxVec3.ZERO, VfxEvaluationMode.EMITTER_TICK),
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_TICK)
        );
    }

    public static VfxSpawnShapeDefinition sphere(
            VfxVec3Expression offset,
            VfxNumberExpression radius,
            VfxNumberExpression edgeThickness
    ) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.SPHERE,
                offset,
                radius,
                VfxVec3Expression.constant(VfxVec3.ZERO, VfxEvaluationMode.EMITTER_TICK),
                edgeThickness
        );
    }

    public static VfxSpawnShapeDefinition box(
            VfxVec3Expression offset,
            VfxVec3Expression halfExtents,
            VfxNumberExpression edgeThickness
    ) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.BOX,
                offset,
                VfxNumberExpression.constant(0.0, VfxEvaluationMode.EMITTER_TICK),
                halfExtents,
                edgeThickness
        );
    }
}
