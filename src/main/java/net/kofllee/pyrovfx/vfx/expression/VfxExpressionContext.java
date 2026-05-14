package net.kofllee.pyrovfx.vfx.expression;

import java.util.HashMap;
import java.util.Map;

public final class VfxExpressionContext{
    private final Map<String, Double> numbers = new HashMap<>();

    public double getNumber(String name){
        Double value = numbers.get(name);

        if(value == null){
            throw new IllegalArgumentException("Unknown expression variable: " + name);
        }

        return value;
    }

    public boolean hasNumber(String name) {
        return numbers.containsKey(name);
    }

    public VfxExpressionContext setNumber(String name, double value) {
        numbers.put(name, value);
        return this;
    }

    public static VfxExpressionContext empty() {
        return new VfxExpressionContext();
    }
}
