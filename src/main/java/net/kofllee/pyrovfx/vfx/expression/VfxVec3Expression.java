package net.kofllee.pyrovfx.vfx.expression;

import net.kofllee.pyrovfx.vfx.value.VfxVec3;

public class VfxVec3Expression implements VfxExpression<VfxVec3> {
    private final VfxNumberExpression x;
    private final VfxNumberExpression y;
    private final VfxNumberExpression z;

    public VfxVec3Expression(
            VfxNumberExpression x,
            VfxNumberExpression y,
            VfxNumberExpression z
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static VfxVec3Expression constant(VfxVec3 value) {
        return new VfxVec3Expression(
                VfxNumberExpression.constant(value.x()),
                VfxNumberExpression.constant(value.y()),
                VfxNumberExpression.constant(value.z())
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
    public boolean isConstant() {
        return x.isConstant() && y.isConstant() && z.isConstant();
    }
}
