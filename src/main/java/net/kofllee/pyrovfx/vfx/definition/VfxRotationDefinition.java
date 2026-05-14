package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxRotationMode;

public record VfxRotationDefinition(
        VfxRotationMode mode,
        VfxDynamicRotationDefinition dynamic,
        VfxParametricRotationDefinition parametric
) {
    public static VfxRotationDefinition none() {
        return new VfxRotationDefinition(
                VfxRotationMode.NONE,
                VfxDynamicRotationDefinition.none(),
                VfxParametricRotationDefinition.none()
        );
    }

    public static VfxRotationDefinition dynamic(VfxDynamicRotationDefinition dynamic) {
        return new VfxRotationDefinition(
                VfxRotationMode.DYNAMIC,
                dynamic,
                VfxParametricRotationDefinition.none()
        );
    }

    public static VfxRotationDefinition parametric(VfxParametricRotationDefinition parametric) {
        return new VfxRotationDefinition(
                VfxRotationMode.PARAMETRIC,
                VfxDynamicRotationDefinition.none(),
                parametric
        );
    }
}
