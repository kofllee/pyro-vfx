package net.kofllee.pyrovfx.vfx.math;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/** Inverse view orientation for world positions already relative to camera. */
public final class VfxCameraRelativeView {
    private VfxCameraRelativeView() {}

    public static Quaternionf rotation(Quaternionfc cameraRotation) {
        return new Quaternionf(cameraRotation).conjugate();
    }
}
