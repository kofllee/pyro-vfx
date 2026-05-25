package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.expression.VfxNumberExpression;
import net.kofllee.pyrovfx.vfx.expression.VfxVec3Expression;
import net.kofllee.pyrovfx.vfx.type.VfxSpawnShapeType;
import net.kofllee.pyrovfx.vfx.value.VfxVec3;
import net.minecraft.resources.ResourceLocation;

public record VfxSpawnShapeDefinition(VfxSpawnShapeType type, VfxNumberExpression radius, VfxNumberExpression innerRadius, VfxNumberExpression height, VfxVec3Expression halfExtents, VfxVec3Expression axis, VfxVec3Expression scale, VfxNumberExpression edgeThickness, ResourceLocation model) {
    public static VfxSpawnShapeDefinition point() {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.POINT,
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxVec3Expression.constant(new VfxVec3(0.0, 1.0, 0.0)),
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 1.0)),
                VfxNumberExpression.constant(0.0),
                null
        );
    }

    public static VfxSpawnShapeDefinition sphere(VfxNumberExpression radius, VfxNumberExpression edgeThickness) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.SPHERE,
                radius,
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxVec3Expression.constant(new VfxVec3(0.0, 1.0, 0.0)),
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 1.0)),
                edgeThickness,
                null
        );
    }

    public static VfxSpawnShapeDefinition box(VfxVec3Expression halfExtents, VfxNumberExpression edgeThickness) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.BOX,
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                halfExtents,
                VfxVec3Expression.constant(new VfxVec3(0.0, 1.0, 0.0)),
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 1.0)),
                edgeThickness,
                null
        );
    }

    public static VfxSpawnShapeDefinition line(VfxNumberExpression length, VfxVec3Expression axis) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.LINE,
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                length,
                VfxVec3Expression.constant(VfxVec3.ZERO),
                axis,
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 1.0)),
                VfxNumberExpression.constant(0.0),
                null
        );
    }

    public static VfxSpawnShapeDefinition disc(VfxNumberExpression radius, VfxVec3Expression axis, VfxNumberExpression edgeThickness) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.DISC,
                radius,
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                axis,
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 1.0)),
                edgeThickness,
                null
        );
    }

    public static VfxSpawnShapeDefinition ring(VfxNumberExpression radius, VfxNumberExpression innerRadius, VfxVec3Expression axis) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.RING,
                radius,
                innerRadius,
                VfxNumberExpression.constant(0.0),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                axis,
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 1.0)),
                VfxNumberExpression.constant(0.0),
                null
        );
    }

    public static VfxSpawnShapeDefinition cone(VfxNumberExpression radius, VfxNumberExpression height, VfxVec3Expression axis, VfxNumberExpression edgeThickness) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.CONE,
                radius,
                VfxNumberExpression.constant(0.0),
                height,
                VfxVec3Expression.constant(VfxVec3.ZERO),
                axis,
                VfxVec3Expression.constant(new VfxVec3(1.0, 1.0, 1.0)),
                edgeThickness,
                null
        );
    }

    public static VfxSpawnShapeDefinition model(ResourceLocation model, VfxVec3Expression scale, VfxNumberExpression edgeThickness) {
        return new VfxSpawnShapeDefinition(
                VfxSpawnShapeType.MODEL,
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxNumberExpression.constant(0.0),
                VfxVec3Expression.constant(VfxVec3.ZERO),
                VfxVec3Expression.constant(new VfxVec3(0.0, 1.0, 0.0)),
                scale,
                edgeThickness,
                model
        );
    }

}
