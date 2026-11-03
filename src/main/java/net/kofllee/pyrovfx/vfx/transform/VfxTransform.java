package net.kofllee.pyrovfx.vfx.transform;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immutable pose snapshot. The quaternion is copied on both ingress and egress. */
public final class VfxTransform {
    private final Vec3 position;
    private final Quaternionf rotation;

    public VfxTransform(Vec3 position, Quaternionf rotation) {
        if (position == null || rotation == null
                || !Double.isFinite(position.x) || !Double.isFinite(position.y)
                || !Double.isFinite(position.z)
                || !Float.isFinite(rotation.x) || !Float.isFinite(rotation.y)
                || !Float.isFinite(rotation.z) || !Float.isFinite(rotation.w)) {
            throw new IllegalArgumentException("VFX transform must be finite");
        }
        this.position = position;
        this.rotation = new Quaternionf(rotation).normalize();
    }

    public static VfxTransform at(Vec3 position) {
        return new VfxTransform(position, new Quaternionf());
    }

    public Vec3 position() { return position; }
    public Quaternionf rotation() { return new Quaternionf(rotation); }
    public boolean hasRotation() {
        return Math.abs(rotation.x) > 1.0e-7f || Math.abs(rotation.y) > 1.0e-7f
                || Math.abs(rotation.z) > 1.0e-7f
                || Math.abs(rotation.w - 1.0f) > 1.0e-7f;
    }

    public Vec3 rotate(Vec3 vector) {
        Vector3f v = rotation.transform(new Vector3f((float) vector.x,
                (float) vector.y, (float) vector.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 transform(Vec3 local) { return position.add(rotate(local)); }

    /** Emitter-local offset and Euler degrees, composed after the parent pose. */
    public VfxTransform child(Vec3 offset, Vec3 eulerDegrees) {
        Quaternionf local = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(eulerDegrees.x),
                (float) Math.toRadians(eulerDegrees.y),
                (float) Math.toRadians(eulerDegrees.z));
        return new VfxTransform(transform(offset), new Quaternionf(rotation).mul(local));
    }

    public Vec3 rotateAngles(Vec3 localEulerDegrees) {
        Quaternionf composed = new Quaternionf(rotation).mul(
                new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(localEulerDegrees.x),
                        (float) Math.toRadians(localEulerDegrees.y),
                        (float) Math.toRadians(localEulerDegrees.z)));
        Vector3f angles = composed.getEulerAnglesXYZ(new Vector3f());
        return new Vec3(Math.toDegrees(angles.x), Math.toDegrees(angles.y),
                Math.toDegrees(angles.z));
    }

    /** Express this world pose in a parent's local coordinate system. */
    public VfxTransform relativeTo(VfxTransform parent) {
        return new VfxTransform(parent.inverseTransform(position),
                parent.rotation().conjugate().mul(rotation));
    }

    /** Compose a parent world pose with a child pose in local coordinates. */
    public VfxTransform compose(VfxTransform local) {
        return new VfxTransform(transform(local.position),
                new Quaternionf(rotation).mul(local.rotation));
    }

    public Vec3 inverseTransform(Vec3 world) { return inverseRotate(world.subtract(position)); }

    public Vec3 inverseRotate(Vec3 vector) {
        Vector3f v = new Quaternionf(rotation).conjugate().transform(
                new Vector3f((float) vector.x, (float) vector.y, (float) vector.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public static VfxTransform interpolate(VfxTransform previous, VfxTransform current, float partialTick) {
        float t = Math.max(0.0f, Math.min(1.0f, partialTick));
        return new VfxTransform(previous.position.lerp(current.position, t),
                new Quaternionf(previous.rotation).slerp(current.rotation, t));
    }
}