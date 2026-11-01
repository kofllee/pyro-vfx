package net.kofllee.pyrovfx.vfx.expression;

public class VfxNumberExpression implements VfxExpression<Double> {
    private final double constant;
    private final boolean isConstant;
    private final VfxExpressionNode node;

    private VfxNumberExpression(Double constant, VfxExpressionNode node) {
        this.constant = constant == null ? 0.0 : constant;
        this.isConstant = constant != null;
        this.node = node;
    }

    public static VfxNumberExpression constant(double value) {
        return new VfxNumberExpression(value, null);
    }

    public static VfxNumberExpression expression(String source) {
        VfxExpressionNode compiled = VfxExpressionCompiler.compileNumber(source);
        if (compiled instanceof VfxExpressionCompiler.ConstantNode constant) {
            return constant(constant.value());
        }
        return new VfxNumberExpression(null, compiled);
    }


    @Override
    public Double evaluate(VfxExpressionContext context) {
        return evaluateDouble(context);
    }

    public double evaluateDouble(VfxExpressionContext context) {
        return isConstant ? constant : node.evaluate(context);
    }

    @Override
    public boolean isConstant() {
        return isConstant;
    }
}
