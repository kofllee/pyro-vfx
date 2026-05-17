package net.kofllee.pyrovfx.vfx.expression;

import net.kofllee.pyrovfx.vfx.value.VfxColor;

public class VfxColorExpression implements VfxExpression<VfxColor>{
    private final VfxNumberExpression r;
    private final VfxNumberExpression g;
    private final VfxNumberExpression b;
    private final VfxNumberExpression a;

    public VfxColorExpression(
            VfxNumberExpression r,
            VfxNumberExpression g,
            VfxNumberExpression b,
            VfxNumberExpression a
    ) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    @Override
    public VfxColor evaluate(VfxExpressionContext context) {
        return new VfxColor(
                r.evaluate(context),
                g.evaluate(context),
                b.evaluate(context),
                a.evaluate(context)
        );
    }

    @Override
    public boolean isConstant() {
        return r.isConstant() && g.isConstant() && b.isConstant() && a.isConstant();
    }
}
