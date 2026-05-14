package net.kofllee.pyrovfx.vfx.definition;

import net.kofllee.pyrovfx.vfx.type.VfxMotionMode;

public record VfxMotionDefinition (
        VfxMotionMode mode,
        VfxDynamicMotionDefinition dynamic,
        VfxParametricMotionDefinition parametric,
        VfxMotionCollisionDefinition collision
) {

    public static VfxMotionDefinition statik(VfxMotionCollisionDefinition collision) {
        return new VfxMotionDefinition(
                VfxMotionMode.STATIC,
                VfxDynamicMotionDefinition.none(),
                VfxParametricMotionDefinition.none(),
                collision
        );
    }

    public static VfxMotionDefinition dynamic(
            VfxDynamicMotionDefinition dynamic,
            VfxMotionCollisionDefinition collision
    ) {
        return new VfxMotionDefinition(
                VfxMotionMode.DYNAMIC,
                dynamic,
                VfxParametricMotionDefinition.none(),
                collision
        );
    }

    public static VfxMotionDefinition parametric(
            VfxParametricMotionDefinition parametric,
            VfxMotionCollisionDefinition collision
    ) {
        return new VfxMotionDefinition(
                VfxMotionMode.PARAMETRIC,
                VfxDynamicMotionDefinition.none(),
                parametric,
                collision
        );
    }

    public static VfxMotionDefinition none() {
        return statik(VfxMotionCollisionDefinition.none());
    }

}
