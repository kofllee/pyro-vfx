package net.kofllee.pyrovfx.vfx.expression;

import net.kofllee.pyrovfx.vfx.curve.VfxCurveSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VfxContextBuilder {
    private final VfxExpressionContext context;

    private VfxContextBuilder(VfxExpressionContext context){
        this.context = context;
    }

    public static VfxContextBuilder create(){
        return new VfxContextBuilder(VfxExpressionContext.root());
    }

    public static VfxContextBuilder childOf(VfxExpressionContext parent){
        return new VfxContextBuilder(parent.child());
    }

    public VfxContextBuilder number(String name, double value){
        context.setNumber(name, value);
        return this;
    }

    public VfxContextBuilder numbers(String prefix, java.util.Map<String, Double> values) {
        for (java.util.Map.Entry<String, Double> entry : values.entrySet()) {
            number(prefix + "." + entry.getKey(), entry.getValue());
        }

        return this;
    }

    public VfxContextBuilder vec3(String name, Vec3 value){
        context.setNumber(name + ".x", value.x);
        context.setNumber(name + ".y", value.y);
        context.setNumber(name + ".z", value.z);
        return this;
    }

    public VfxContextBuilder curves(VfxCurveSet curves) {
        context.setCurves(curves);
        return this;
    }

    public VfxContextBuilder random(RandomSource random) {
        context.setRandom(random);
        return this;
    }

    public VfxExpressionContext build() {
        return context;
    }
}
