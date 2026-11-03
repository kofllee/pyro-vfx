package net.kofllee.pyrovfx.vfx.transform;

/**
 * Samples a source pose; returns null when the source is unavailable.
 * The runtime samples at 1.0 on game ticks and at partialTick during rendering.
 * Providers must be called only on the owning client thread.
 */
@FunctionalInterface
public interface VfxTransformProvider {
    VfxTransform get(float partialTick);

    static VfxTransformProvider fixed(net.minecraft.world.phys.Vec3 position) {
        VfxTransform snapshot = VfxTransform.at(position);
        return partialTick -> snapshot;
    }
}