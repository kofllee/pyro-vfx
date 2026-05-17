package net.kofllee.pyrovfx.client.vfx;

import java.util.Map;

public final class VfxPlayOptions {
    private static final VfxPlayOptions EMPTY = new VfxPlayOptions(Map.of());

    private final Map<String, Double> parameters;

    private VfxPlayOptions(Map<String, Double> parameters) {
        this.parameters = Map.copyOf(parameters);
    }

    public static VfxPlayOptions empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Double> parameters() {
        return parameters;
    }

    public static final class Builder {
        private final Map<String, Double> parameters = new java.util.HashMap<>();

        private Builder() {}

        public Builder param(String name, double value) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("VFX parameter name must not be empty");
            }

            parameters.put(name, value);
            return this;
        }

        public VfxPlayOptions build() {
            if (parameters.isEmpty()) {
                return VfxPlayOptions.empty();
            }

            return new VfxPlayOptions(parameters);
        }
    }
}
