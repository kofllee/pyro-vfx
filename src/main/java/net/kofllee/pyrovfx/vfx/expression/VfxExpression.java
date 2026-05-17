package net.kofllee.pyrovfx.vfx.expression;

public interface VfxExpression<T> {
    T evaluate(VfxExpressionContext context);

    boolean isConstant();
}
