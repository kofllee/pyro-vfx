package net.kofllee.pyrovfx.vfx.expression;

import java.util.HashMap;
import java.util.Map;

public final class VfxExpressionContext{
    private final VfxExpressionContext parent;
    private final Map<String, Double> numbers = new HashMap<>();

    private VfxExpressionContext(VfxExpressionContext parent) {
        this.parent = parent;
    }

    public static VfxExpressionContext root() {
        return new VfxExpressionContext(null);
    }
    public VfxExpressionContext child() {
        return new VfxExpressionContext(this);
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
}
