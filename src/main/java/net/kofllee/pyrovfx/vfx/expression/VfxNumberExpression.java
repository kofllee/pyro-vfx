package net.kofllee.pyrovfx.vfx.expression;

public class VfxNumberExpression implements VfxExpression<Double> {
    private final Double constant;
    private final VfxExpressionNode node;

    private VfxNumberExpression(Double constant, VfxExpressionNode node) {
        this.constant = constant;
        this.node = node;
    }

    public static VfxNumberExpression constant(double value) {
        return new VfxNumberExpression(value, null);
    }

    public static VfxNumberExpression expression(String source) {
        return new VfxNumberExpression(null, VfxExpressionCompiler.compileNumber(source));
    }


    @Override
    public Double evaluate(VfxExpressionContext context) {
        if(constant != null) {
            return constant;
        }

        return node.evaluate(context);
    }

    @Override
    public boolean isConstant() {
        return constant != null;
    }
}
