package net.kofllee.pyrovfx.vfx.value;

import net.minecraft.world.phys.Vec3;

public record VfxVec3 (double x, double y, double z) {
    public static final VfxVec3 ZERO = new VfxVec3(0.0, 0.0, 0.0);

    public Vec3 toVec3() {
        return new Vec3(x, y, z);
    }
}
