package net.kofllee.pyrovfx.vfx.expression;

public class VfxNumberExpression implements VfxExpression<Double> {
    private final Double constant;
    private final VfxExpressionNode node;
    private final VfxEvaluationMode evaluationMode;


    private VfxNumberExpression(Double constant, VfxExpressionNode node, VfxEvaluationMode evaluationMode) {
        this.constant = constant;
        this.node = node;
        this.evaluationMode = evaluationMode;
    }

    public static VfxNumberExpression constant(double value, VfxEvaluationMode evaluationMode) {
        return new VfxNumberExpression(value, null, evaluationMode);
    }

    public static VfxNumberExpression expression(String source, VfxEvaluationMode evaluationMode) {
        return new VfxNumberExpression(null, VfxExpressionCompiler.compileNumber(source), evaluationMode);
    }


    @Override
    public Double evaluate(VfxExpressionContext context) {
        if(constant != null) {
            return constant;
        }

        return node.evaluate(context);
    }

    @Override
    public VfxEvaluationMode evaluationMode() {
        return evaluationMode;
    }

    @Override
    public boolean isConstant() {
        return constant != null;
    }
}
