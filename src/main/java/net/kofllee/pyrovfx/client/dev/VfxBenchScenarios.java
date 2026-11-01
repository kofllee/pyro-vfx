package net.kofllee.pyrovfx.client.dev;

import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VfxBenchScenarios {
    public record Scenario(String name, ResourceLocation effect, int particles, long seed,
                           int warmupTicks, int sampleTicks) {
        public boolean requiresOptIn() {
            return particles > 10_000;
        }
    }

    private static final Map<String, Scenario> SCENARIOS = createScenarios();

    private VfxBenchScenarios() {}

    private static Map<String, Scenario> createScenarios() {
        Map<String, Scenario> scenarios = new LinkedHashMap<>();
        add(scenarios, "sprite_1k", "bench/static", 1_000);
        add(scenarios, "sprite_10k", "bench/static", 10_000);
        add(scenarios, "sprite_100k", "bench/static", 100_000);
        add(scenarios, "sprite_1m", "bench/static", 1_000_000);
        add(scenarios, "dynamic_1k", "bench/dynamic", 1_000);
        add(scenarios, "collision_1k", "bench/collision", 1_000);
        add(scenarios, "self_emit_collision", "bench/self_emit_collision", 1);
        add(scenarios, "model_rotation", "bench/static", 1);
        add(scenarios, "model_inheritance", "bench/static", 1);
        add(scenarios, "event_order", "bench/event_order", 1);
        add(scenarios, "model_1k", "bench/model", 1_000);
        add(scenarios, "curves", "examples/curves", 0);
        add(scenarios, "events", "examples/event_sequence", 0);
        add(scenarios, "uv_animation", "examples/animated_sprite", 0);
        return Map.copyOf(scenarios);
    }

    private static void add(Map<String, Scenario> scenarios, String name, String id, int particles) {
        scenarios.put(name, new Scenario(name,
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", id),
                particles, 0x50B0_2026L, 30, 100));
    }

    public static Scenario get(String name) {
        return SCENARIOS.get(name);
    }

    public static Map<String, Scenario> all() {
        return SCENARIOS;
    }
}
