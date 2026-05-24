package net.kofllee.pyrovfx.vfx.curve;

import java.util.Map;

public final class VfxCurveSet {
    public static final VfxCurveSet EMPTY = new VfxCurveSet(Map.of());

    private final Map<String, VfxCurveDefinition> curves;

    public VfxCurveSet(final Map<String, VfxCurveDefinition> curves) {
        this.curves = curves;
    }

    public double sample(String id, double x){
        VfxCurveDefinition curve = curves.get(id);

        if (curve == null) {
            throw new IllegalArgumentException("Unknown curve: " + id);
        }

        return curve.sample(x);
    }

    public boolean contains(String id) {
        return curves.containsKey(id);
    }

    public Map<String, VfxCurveDefinition> curves() {
        return curves;
    }
}
