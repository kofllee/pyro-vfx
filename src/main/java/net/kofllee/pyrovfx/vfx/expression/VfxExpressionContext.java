package net.kofllee.pyrovfx.vfx.expression;

import net.kofllee.pyrovfx.vfx.curve.VfxCurveSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Scoped numeric variables. Compiled programs use primitive indexed slots and a
 * compact presence mask; ad-hoc legacy contexts retain string-key compatibility.
 */
public final class VfxExpressionContext {
    private final VfxExpressionContext parent;
    private final VfxVariableLayout layout;
    private final double[] values;
    private final long[] present;
    private Map<String, Double> fallback;

    private VfxCurveSet curves;
    private RandomSource random;

    private VfxExpressionContext(VfxExpressionContext parent, VfxVariableLayout layout) {
        this.parent = parent;
        this.layout = layout;
        this.values = layout == null ? null : new double[layout.size()];
        this.present = layout == null ? null : new long[(layout.size() + 63) >>> 6];
    }

    public static VfxExpressionContext root() {
        return new VfxExpressionContext(null, null);
    }

    public static VfxExpressionContext root(VfxVariableLayout layout) {
        return new VfxExpressionContext(null, layout);
    }

    public VfxExpressionContext child() {
        VfxExpressionContext child = new VfxExpressionContext(this, layout);
        child.curves = curves;
        child.random = random;
        return child;
    }

    public boolean usesSlots() {
        return values != null;
    }

    private boolean present(int slot) {
        return (present[slot >>> 6] & (1L << (slot & 63))) != 0;
    }

    /** Primitive expression read; no symbol hashing, boxing or intermediate maps. */
    public double getNumber(int slot, String fallbackName) {
        if (values == null) {
            return getNumber(fallbackName);
        }
        if (present(slot)) {
            return values[slot];
        }
        if (parent != null) {
            return parent.getNumber(slot, fallbackName);
        }
        return getFallback(fallbackName);
    }

    private double getFallback(String name) {
        if (fallback != null && fallback.containsKey(name)) {
            return fallback.get(name);
        }
        throw new IllegalArgumentException("Unknown expression variable: " + name);
    }

    public double getNumber(String name) {
        if (layout != null) {
            int slot = layout.slot(name);
            if (slot >= 0) {
                return getNumber(slot, name);
            }
        }
        if (fallback != null && fallback.containsKey(name)) {
            return fallback.get(name);
        }
        if (parent != null) {
            return parent.getNumber(name);
        }
        throw new IllegalArgumentException("Unknown expression variable: " + name);
    }

    public boolean hasNumber(String name) {
        if (layout != null) {
            int slot = layout.slot(name);
            if (slot >= 0 && present(slot)) {
                return true;
            }
        }
        if (fallback != null && fallback.containsKey(name)) {
            return true;
        }
        return parent != null && parent.hasNumber(name);
    }

    /** Write a compile-time known standard variable without hashing its symbol. */
    public VfxExpressionContext setSlot(int slot, String name, double value) {
        if (values == null) {
            return setNumber(name, value);
        }
        values[slot] = value;
        present[slot >>> 6] |= 1L << (slot & 63);
        return this;
    }

    public VfxExpressionContext setVec3Slots(int slot, String name, Vec3 value) {
        return setVec3Slots(slot, name, value.x, value.y, value.z);
    }

    /** Hot-path batch state update, avoids allocating Vec3 wrappers. */
    public VfxExpressionContext setVec3Slots(int slot, String name, double x, double y, double z) {
        if (values != null) {
            values[slot] = x;
            values[slot + 1] = y;
            values[slot + 2] = z;
            present[slot >>> 6] |= 1L << (slot & 63);
            present[(slot + 1) >>> 6] |= 1L << ((slot + 1) & 63);
            present[(slot + 2) >>> 6] |= 1L << ((slot + 2) & 63);
        } else {
            setNumber(name + ".x", x);
            setNumber(name + ".y", y);
            setNumber(name + ".z", z);
        }
        return this;
    }

    public VfxExpressionContext setNumber(String name, double value) {
        if (layout != null) {
            int slot = layout.slot(name);
            if (slot >= 0) {
                return setSlot(slot, name, value);
            }
        }
        if (fallback == null) {
            fallback = new HashMap<>();
        }
        fallback.put(name, value);
        return this;
    }

    public VfxExpressionContext updateNumber(String name, double value) {
        if (layout != null) {
            int slot = layout.slot(name);
            if (slot >= 0) {
                if (present(slot)) {
                    return setSlot(slot, name, value);
                }
                if (parent != null && parent.hasNumber(name)) {
                    return parent.updateNumber(name, value);
                }
                throw new IllegalArgumentException("Unknown expression variable: " + name);
            }
        }
        if (fallback != null && fallback.containsKey(name)) {
            fallback.put(name, value);
        } else if (parent != null) {
            parent.updateNumber(name, value);
        } else {
            throw new IllegalArgumentException("Unknown expression variable: " + name);
        }
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
