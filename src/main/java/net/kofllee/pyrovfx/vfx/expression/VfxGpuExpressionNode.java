package net.kofllee.pyrovfx.vfx.expression;

import java.util.List;
import java.util.Locale;
import net.kofllee.pyrovfx.vfx.curve.VfxCurveDefinition;

/**
 * Optional GLSL representation of nodes produced by the existing CPU parser.
 * Unsupported operations retain the exact CPU evaluator and return null
 * instead of introducing a second expression grammar.
 */
public final class VfxGpuExpressionNode {
    private VfxGpuExpressionNode() {}

    private record Emitted(String glsl, VfxExpressionNode cpu) implements VfxExpressionNode {
        @Override public double evaluate(VfxExpressionContext context) { return cpu.evaluate(context); }
    }

    public static String glsl(VfxExpressionNode node) {
        if (node instanceof Emitted emitted) return emitted.glsl();
        if (node instanceof VfxExpressionCompiler.ConstantNode constant)
            return literal(constant.value());
        return null;
    }

    public static VfxExpressionNode emit(String glsl, VfxExpressionNode cpu) {
        // Bound shader source expansion for deeply nested expressions/curves.
        return glsl == null || glsl.length() > 4096 ? cpu : new Emitted(glsl, cpu);
    }

    public static String literal(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE) return null;
        String text = Double.toString(value);
        return value < 0 ? "(" + text + ")" : text;
    }

    public static VfxExpressionNode number(double value) {
        return emit(literal(value), context -> value);
    }

    public static String variable(String name) {
        return switch (name) {
            case "particle.age" -> "vfxAge";
            case "particle.normalized_age" -> "min(1.0, vfxAge / vfxLifetime)";
            case "particle.lifetime" -> "vfxLifetime";
            case "particle.random" -> "vfxRandom";
            case "particle.pos.x" -> "vfxPos.x";
            case "particle.pos.y" -> "vfxPos.y";
            case "particle.pos.z" -> "vfxPos.z";
            case "particle.prev_pos.x" -> "vfxPrevPos.x";
            case "particle.prev_pos.y" -> "vfxPrevPos.y";
            case "particle.prev_pos.z" -> "vfxPrevPos.z";
            case "particle.vel.x" -> "vfxVelocity.x";
            case "particle.vel.y" -> "vfxVelocity.y";
            case "particle.vel.z" -> "vfxVelocity.z";
            case "particle.speed" -> "length(vfxVelocity)";
            case "spawn.pos.x" -> "birth.x";
            case "spawn.pos.y" -> "birth.y";
            case "spawn.pos.z" -> "birth.z";
            default -> null;
        };
    }

    public static VfxExpressionNode binary(String operator, VfxExpressionNode left,
                                            VfxExpressionNode right, VfxExpressionNode cpu) {
        String l = glsl(left), r = glsl(right);
        return emit(l != null && r != null && switch(operator) {
                    case "+", "-", "*", "/" -> true;
                    default -> false;
                } ? "(" + l + " " + operator + " " + r + ")" : null, cpu);
    }

    public static VfxExpressionNode unaryMinus(VfxExpressionNode node, VfxExpressionNode cpu) {
        String v = glsl(node);
        return emit(v == null ? null : "(-" + v + ")", cpu);
    }

    public static VfxExpressionNode function(String name, List<VfxExpressionNode> args,
                                             VfxExpressionNode cpu) {
        String shaderFn = switch (name.toLowerCase(Locale.ROOT)) {
            case "math.sin" -> "sin";
            case "math.cos" -> "cos";
            case "math.abs" -> "abs";
            case "math.floor" -> "floor";
            case "math.ceil" -> "ceil";
            case "math.sqrt" -> "sqrt";
            case "math.min" -> "min";
            case "math.max" -> "max";
            // CPU Math.clamp throws for reversed bounds. GLSL clamp has
            // undefined behavior there, so retain the original evaluator.
            default -> null;
        };
        if (shaderFn == null) return cpu;
        StringBuilder result = new StringBuilder(shaderFn).append("(");
        for (int i = 0; i < args.size(); i++) {
            String s = glsl(args.get(i));
            if (s == null) return cpu;
            if (i != 0) result.append(",");
            result.append(s);
        }
        return emit(result.append(")").toString(), cpu);
    }

    /** Inline small immutable curves directly into the existing GLSL expression. */
    public static VfxExpressionNode curve(VfxCurveDefinition curve, VfxExpressionNode input,
                                          VfxExpressionNode cpu) {
        String x = glsl(input);
        if (x == null || curve.points().size() > 8) return cpu;
        var points = curve.points();
        String last = literal(points.getLast().y());
        if (last == null) return cpu;
        for (int i = points.size() - 2; i >= 0; i--) {
            var left = points.get(i);
            var right = points.get(i + 1);
            String xLeft = literal(left.x()), xRight = literal(right.x());
            String yLeft = literal(left.y()), yRight = literal(right.y());
            if (xLeft == null || xRight == null || yLeft == null || yRight == null)
                return cpu;
            String t = right.x() == left.x() ? "0.0"
                    : "((" + x + "-" + xLeft + ")/(" + xRight + "-" + xLeft + "))";
            String interpolation = switch (curve.type()) {
                case STEP -> yLeft;
                case LINEAR -> "(" + yLeft + "+(" + yRight + "-" + yLeft + ")*" + t + ")";
                case BEZIER -> "(" + yLeft + "+(" + yRight + "-" + yLeft
                        + ")*(" + t + "*" + t + "*(3.0-2.0*" + t + ")))";
                case CATMULL -> {
                    String p0 = literal(points.get(Math.max(0, i - 1)).y());
                    String p3 = literal(points.get(Math.min(points.size() - 1, i + 2)).y());
                    if (p0 == null || p3 == null) yield null;
                    yield "(0.5*(2.0*" + yLeft + "+(-" + p0 + "+" + yRight + ")*" + t
                            + "+(2.0*" + p0 + "-5.0*" + yLeft + "+4.0*" + yRight
                            + "-" + p3 + ")*(" + t + "*" + t + ")"
                            + "+(-" + p0 + "+3.0*" + yLeft + "-3.0*" + yRight
                            + "+" + p3 + ")*(" + t + "*" + t + "*" + t + ")))";
                }
            };
            if (interpolation == null) return cpu;
            last = "(" + x + " <= " + xRight + " ? " + interpolation + " : " + last + ")";
        }
        String first = literal(points.getFirst().y()), firstX = literal(points.getFirst().x());
        return first == null || firstX == null ? cpu
                : emit("(" + x + " <= " + firstX + " ? " + first + " : " + last + ")", cpu);
    }
}
