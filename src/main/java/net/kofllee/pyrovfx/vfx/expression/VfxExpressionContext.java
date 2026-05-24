package net.kofllee.pyrovfx.vfx.expression;

import net.kofllee.pyrovfx.vfx.curve.VfxCurveSet;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

public final class VfxExpressionContext{
    private final VfxExpressionContext parent;
    private final Map<String, Double> numbers = new HashMap<>();

    private VfxCurveSet curves;
    private RandomSource random;

    private VfxExpressionContext(VfxExpressionContext parent) {
        this.parent = parent;
    }

    public static VfxExpressionContext root() {
        return new VfxExpressionContext(null);
    }
    public VfxExpressionContext child() {
        VfxExpressionContext child = new VfxExpressionContext(this);
        child.curves = this.curves;
        child.random = this.random;
        return child;
    }

    public double getNumber(String name){
        Double value = numbers.get(name);

        if (value != null) {
            return value;
        }

        if (parent != null) {
            return parent.getNumber(name);
        }

        throw new IllegalArgumentException("Unknown expression variable: " + name);
    }

    public boolean hasNumber(String name) {
        if (numbers.containsKey(name)) {
            return true;
        }

        return parent != null && parent.hasNumber(name);
    }

    public VfxExpressionContext setNumber(String name, double value) {
        numbers.put(name, value);
        return this;
    }

    public VfxExpressionContext setCurves(VfxCurveSet curves) {
        this.curves = curves == null ? VfxCurveSet.EMPTY : curves;
        return this;
    }

    public VfxCurveSet curves() {
        if (curves != null) {
            return curves;
        }

        return parent == null ? VfxCurveSet.EMPTY : parent.curves();
    }

    public VfxExpressionContext setRandom(RandomSource random) {
        this.random = random;
        return this;
    }

    public RandomSource random() {
        if (random != null) {
            return random;
        }

        if (parent != null) {
            return parent.random();
        }

        throw new IllegalArgumentException("Expression random is not available in this context");
    }
}
