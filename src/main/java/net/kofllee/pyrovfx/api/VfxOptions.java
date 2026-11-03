package net.kofllee.pyrovfx.api;

import java.util.Map;
import java.util.Objects;

/**
 * Optional playback settings. A normal Vfx.play call does not need this class.
 * The radius and lease affect server broadcasts only; local playback uses parameters.
 */
public record VfxOptions(Map<String, Double> parameters, double radius, int durationTicks) {
    /** 96-block audience and a finite one-minute server lease. */
    public static final VfxOptions DEFAULT = new VfxOptions(Map.of(), 96.0, 20 * 60);

    public VfxOptions {
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }

    public VfxOptions withParameters(Map<String, Double> values) {
        return new VfxOptions(values, radius, durationTicks);
    }

    public VfxOptions withRadius(double blocks) {
        return new VfxOptions(parameters, blocks, durationTicks);
    }

    /** Use -1 only for a persistent effect which the owner will explicitly stop. */
    public VfxOptions withDurationTicks(int ticks) {
        return new VfxOptions(parameters, radius, ticks);
    }
}