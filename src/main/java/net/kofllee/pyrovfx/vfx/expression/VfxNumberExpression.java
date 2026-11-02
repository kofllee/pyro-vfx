package net.kofllee.pyrovfx.vfx.expression;

public class VfxNumberExpression implements VfxExpression<Double> {
    private final double constant;
    private final boolean isConstant;
    private final VfxExpressionNode node;
    private final VfxProgram.ExpressionInfo info;

    private VfxNumberExpression(Double constant, VfxExpressionNode node,
                                VfxProgram.ExpressionInfo info) {
        this.constant = constant == null ? 0.0 : constant;
        this.isConstant = constant != null;
        this.node = node;
        this.info = info;
    }

    public static VfxNumberExpression constant(double value) {
        return new VfxNumberExpression(value, null, null);
    }

    public static VfxNumberExpression expression(String source) {
        var compiled = VfxExpressionCompiler.compileNumberWithInfo(source);
        if (compiled.node() instanceof VfxExpressionCompiler.ConstantNode constant) {
            return constant(constant.value());
        }
        return new VfxNumberExpression(null, compiled.node(), compiled.info());
    }

    /**
     * Every particle in a single analytic burst has the same age and lifetime.
     * Immutable curves with shared-age inputs are safe: they are sampled
     * once per emitter draw and uploaded as a uniform. They do not imply
     * general curve evaluation inside GLSL. Mutable params, RNG, position
     * and other per-particle data are never admitted here.
     */
    public boolean isSharedParticleAgeOnly() {
        return isConstant || (info != null && !info.randomCall()
                && !info.parameterDependent()
                && info.variables().stream().allMatch(v ->
                        v.equals("particle.age") || v.equals("particle.normalized_age")
                                || v.equals("particle.lifetime")));
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
