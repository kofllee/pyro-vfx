package net.kofllee.pyrovfx.vfx.expression;

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

    public VfxContextBuilder vec3(String name, Vec3 value){
        context.setNumber(name + ".x", value.x);
        context.setNumber(name + ".y", value.y);
        context.setNumber(name + ".z", value.z);
        return this;
    }

    public VfxExpressionContext build() {
        return context;
    }
}
