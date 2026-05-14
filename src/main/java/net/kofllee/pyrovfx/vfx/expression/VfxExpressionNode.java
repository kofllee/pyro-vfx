package net.kofllee.pyrovfx.vfx.expression;

@FunctionalInterface
public interface VfxExpressionNode {
    double evaluate(VfxExpressionContext context);
}
