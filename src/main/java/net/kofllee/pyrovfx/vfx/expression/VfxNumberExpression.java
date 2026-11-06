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

    /** Safe to evaluate once at each emitter birth, before any particle exists. */
    public boolean isEmitterBirthOnly() {
        return isConstant || (info != null && !info.randomCall()
                && info.variables().stream().allMatch(v ->
                    v.startsWith("param.") || v.startsWith("effect.")
                    || v.startsWith("emitter.")));
    }

    /** One immutable lifetime per particle: the already-sampled birth random
     * is allowed, but no additional RNG calls or mutable runtime variables.
     */
    public boolean isPerParticleBirthLifetime() {
        return !isConstant && info != null && !info.randomCall()
                && !info.parameterDependent()
                && info.variables().contains("particle.random")
                && info.variables().stream().allMatch(v -> v.equals("particle.random"));
    }

    /** GPU subset emitted from the same compiled expression tree, or null. */
    public String glsl() {
        return isConstant ? VfxGpuExpressionNode.literal(constant)
                : VfxGpuExpressionNode.glsl(node);
    }

    /** Conservative subset admitting only immutable per-birth random and age. */
    public boolean isGpuBirthOrAgeOnly() {
        return glsl() != null && (isConstant || (info != null
                && !info.randomCall() && !info.parameterDependent()
                && info.variables().stream().allMatch(v ->
                    v.equals("particle.random") || v.equals("particle.age")
                    || v.equals("particle.normalized_age") || v.equals("particle.lifetime")
                    || v.equals("particle.speed")
                    || v.startsWith("particle.pos.") || v.startsWith("particle.prev_pos.")
                    || v.startsWith("particle.vel.") || v.startsWith("spawn.pos."))));
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
