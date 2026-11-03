package net.kofllee.pyrovfx.vfx.transform;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Samples a source pose; returns null when the source is unavailable.
 * The runtime samples at 1.0 on game ticks and at partialTick during rendering.
 * Providers must be sampled on their owning game thread (client or server).
 */
@FunctionalInterface
public interface VfxTransformProvider {
    VfxTransform get(float partialTick);

    /**
     * Select a named client-resolved attachment on a tracked Entity or BlockEntity.
     * For vanilla living entities, paths such as "head" and "body/right_arm"
     * refer to ModelParts. Namespaced sockets may be supplied by other mods.
     * The source identity is retained for server networking.
     */
    default VfxTransformProvider socket(String name) {
        if (this instanceof VfxTrackedTransformProvider tracked)
            return tracked.withSocket(name);
        throw new IllegalStateException("Named VFX sockets need Vfx.at(entity) or Vfx.at(blockEntity)");
    }

    /**
     * Offsets the effect in the source's local axes.
     * Returns a new provider; this provider is unchanged.
     */
    default VfxTransformProvider offset(double x, double y, double z) {
        return offset(new Vec3(x, y, z));
    }

    default VfxTransformProvider offset(Vec3 localOffset) {
        return transformed(VfxTransform.at(localOffset));
    }

    /** Rotates the effect relative to the source, in XYZ Euler degrees. */
    default VfxTransformProvider rotate(double x, double y, double z) {
        return rotate(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(x),
                (float) Math.toRadians(y),
                (float) Math.toRadians(z)));
    }

    /** Rotates the effect relative to the source with a quaternion. */
    default VfxTransformProvider rotate(Quaternionf localRotation) {
        return transformed(new VfxTransform(Vec3.ZERO, localRotation));
    }

    /** Apply a complete local-space pose while retaining built-in source identity. */
    default VfxTransformProvider compose(VfxTransform local) {
        return transformed(local);
    }

    private VfxTransformProvider transformed(VfxTransform local) {
        if (this instanceof VfxTrackedTransformProvider tracked)
            return tracked.withLocal(local);
        return partialTick -> {
            VfxTransform parent = get(partialTick);
            return parent == null ? null : parent.compose(local);
        };
    }

    static VfxTransformProvider fixed(Vec3 position) {
        VfxTransform snapshot = VfxTransform.at(position);
        return partialTick -> snapshot;
    }
}