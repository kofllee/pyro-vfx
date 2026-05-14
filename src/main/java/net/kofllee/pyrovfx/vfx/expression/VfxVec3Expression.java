package net.kofllee.pyrovfx.vfx.expression;

import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public class VfxVec3Expression implements VfxExpression<VfxVec3> {
    private final VfxNumberExpression x;
    private final VfxNumberExpression y;
    private final VfxNumberExpression z;
    private final VfxEvaluationMode evaluationMode;


    public VfxVec3Expression(
            VfxNumberExpression x,
            VfxNumberExpression y,
            VfxNumberExpression z,
            VfxEvaluationMode evaluationMode
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.evaluationMode = evaluationMode;
    }

    public static VfxVec3Expression constant(VfxVec3 value, VfxEvaluationMode evaluationMode) {
        return new VfxVec3Expression(
                VfxNumberExpression.constant(value.x(), evaluationMode),
                VfxNumberExpression.constant(value.y(), evaluationMode),
                VfxNumberExpression.constant(value.z(), evaluationMode),
                evaluationMode
        );
    }

    @Override
    public VfxVec3 evaluate(VfxExpressionContext context) {
        return new VfxVec3(
                x.evaluate(context),
                y.evaluate(context),
                z.evaluate(context)
        );
    }

    @Override
    public VfxEvaluationMode evaluationMode() {
        return evaluationMode;
    }

    @Override
    public boolean isConstant() {
        return x.isConstant() && y.isConstant() && z.isConstant();
    }
}
