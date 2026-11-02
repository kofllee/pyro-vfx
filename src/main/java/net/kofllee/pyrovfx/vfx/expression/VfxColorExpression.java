package net.kofllee.pyrovfx.vfx.expression;

import net.kofllee.pyrovfx.vfx.value.VfxColor;

public class VfxColorExpression implements VfxExpression<VfxColor>{
    private final VfxNumberExpression r;
    private final VfxNumberExpression g;
    private final VfxNumberExpression b;
    private final VfxNumberExpression a;
    private final VfxColor constantValue;

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
        this.constantValue = r.isConstant() && g.isConstant() && b.isConstant() && a.isConstant()
                ? new VfxColor(r.evaluateDouble(null), g.evaluateDouble(null),
                b.evaluateDouble(null), a.evaluateDouble(null)) : null;
    }

    @Override
    public VfxColor evaluate(VfxExpressionContext context) {
        if (constantValue != null) {
            return constantValue;
        }
        return new VfxColor(
                r.evaluateDouble(context),
                g.evaluateDouble(context),
                b.evaluateDouble(context),
                a.evaluateDouble(context)
        );
    }

    @Override
    public boolean isConstant() {
        return r.isConstant() && g.isConstant() && b.isConstant() && a.isConstant();
    }

    public boolean isSharedParticleAgeOnly() {
        return r.isSharedParticleAgeOnly() && g.isSharedParticleAgeOnly()
                && b.isSharedParticleAgeOnly() && a.isSharedParticleAgeOnly();
    }
}
