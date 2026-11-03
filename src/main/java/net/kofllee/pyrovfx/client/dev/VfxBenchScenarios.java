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
        add(scenarios, "analytic_dynamic_10k", "bench/analytic_dynamic", 10_000);
        add(scenarios, "dynamic_1k", "bench/dynamic", 1_000);
        add(scenarios, "collision_1k", "bench/collision", 1_000);
        add(scenarios, "collision_10k", "bench/collision", 10_000);
        add(scenarios, "collision_no_events", "bench/collision_no_events", 1_000);
        add(scenarios, "collision_no_events_10k", "bench/collision_no_events", 10_000);
        add(scenarios, "self_emit_collision", "bench/self_emit_collision", 1);
        add(scenarios, "model_rotation", "bench/static", 1);
        add(scenarios, "model_inheritance", "bench/static", 1);
        add(scenarios, "event_order", "bench/event_order", 1);
        scenarios.put("m7_api_handles", new Scenario("m7_api_handles",
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order"),
                0, 0x4D72_2026L, 5, 30));
        scenarios.put("m7_network_sync", new Scenario("m7_network_sync",
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order"),
                0, 0x4D73_2026L, 5, 70));
        scenarios.put("m7_network_attachments", new Scenario("m7_network_attachments",
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order"),
                0, 0x4D74_2026L, 5, 75));
        scenarios.put("m7_network_auto", new Scenario("m7_network_auto",
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/event_order"),
                0, 0x4D75_2026L, 5, 95));
        scenarios.put("m7_network_sockets", new Scenario("m7_network_sockets",
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "museum/source_entity_world"),
                0, 0x4D76_2026L, 5, 145));
        scenarios.put("m7_network_late", new Scenario("m7_network_late",
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "museum/source_custom"),
                0, 0x4D77_2026L, 5, 125));
        add(scenarios, "model_1k", "bench/model", 1_000);
        add(scenarios, "model_translucent_1k", "bench/model_translucent", 1_000);
        add(scenarios, "model_entity_250", "bench/model_entity", 250);
        add(scenarios, "model_entity_1k", "bench/model_entity", 1_000);
        for (String lifecycle : new String[]{"entity", "block", "block_entity", "chunk"}) {
            String name = "m7_lifecycle_" + lifecycle;
            String effect = lifecycle.equals("entity")
                    ? "museum/source_entity_world" : "museum/source_block";
            scenarios.put(name, new Scenario(name,
                    ResourceLocation.fromNamespaceAndPath("pyro_vfx", effect),
                    24, 0x4D37_2026L, 5, 110));
        }
        scenarios.put("m7_lifecycle_world_exit", new Scenario("m7_lifecycle_world_exit",
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "museum/source_custom"),
                24, 0x4D37_2026L, 5, 110));
        add(scenarios, "sprite_expressions_1k", "bench/sprite_expressions", 1_000);
        add(scenarios, "sprite_expressions_10k", "bench/sprite_expressions", 10_000);
        add(scenarios, "sprite_expressions_100k", "bench/sprite_expressions", 100_000);
        add(scenarios, "sprite_gpu_curves_10k", "bench/sprite_gpu_curves", 10_000);
        add(scenarios, "sprite_gpu_curves_100k", "bench/sprite_gpu_curves", 100_000);
        add(scenarios, "sprite_gpu_motion_expr_10k", "bench/sprite_gpu_motion_expr", 10_000);
        add(scenarios, "sprite_gpu_motion_expr_100k", "bench/sprite_gpu_motion_expr", 100_000);
        add(scenarios, "gpu_expression_reload_active", "bench/sprite_gpu_motion_expr", 1_000);
        add(scenarios, "model_10k", "bench/model", 10_000);
        add(scenarios, "model_100k", "bench/model", 100_000);
        add(scenarios, "model_rotating_10k", "bench/model_rotating", 10_000);
        add(scenarios, "model_rotating_unlit_10k", "bench/model_rotating_unlit", 10_000);
        add(scenarios, "model_rotating_unlit_100k", "bench/model_rotating_unlit", 100_000);
        add(scenarios, "model_rotating_1k", "bench/model_rotating", 1_000);
        add(scenarios, "model_block_10k", "bench/model_block", 10_000);
        add(scenarios, "model_item_1k", "bench/model_item", 1_000);
        add(scenarios, "model_item_10k", "bench/model_item", 10_000);
        add(scenarios, "model_reload_active", "bench/model", 1);
        add(scenarios, "sprite_uv_instanced", "bench/sprite_uv_instanced", 1_000);
        add(scenarios, "sprite_rotating_1k", "bench/sprite_rotating", 1_000);
        add(scenarios, "sprite_rotating_10k", "bench/sprite_rotating", 10_000);
        add(scenarios, "sprite_alpha_smoke_1k", "bench/sprite_alpha_smoke", 1_000);
        add(scenarios, "sprite_alpha_smoke_10k", "bench/sprite_alpha_smoke", 10_000);
        add(scenarios, "sprite_alpha_smoke_100k", "bench/sprite_alpha_smoke", 100_000);
        add(scenarios, "analytic_uv_1k", "bench/analytic_uv", 1_000);
        add(scenarios, "analytic_uv_10k", "bench/analytic_uv", 10_000);
        add(scenarios, "analytic_age_1k", "bench/analytic_age", 1_000);
        add(scenarios, "analytic_age_10k", "bench/analytic_age", 10_000);
        add(scenarios, "analytic_steady", "bench/analytic_steady", 0);
        add(scenarios, "adaptive_steady", "bench/adaptive_steady", 0);
        int soakTicks = Math.clamp(Integer.getInteger("pyrovfx.bench.soakTicks", 600), 300, 36_000);
        scenarios.put("soak_mixed", new Scenario("soak_mixed",
                ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/soak_mixed"),
                0, 0x50B0_2026L, 30, soakTicks));
        add(scenarios, "sprite_lit_fallback", "bench/sprite_lit_fallback", 1_000);
        add(scenarios, "sprite_world_z", "bench/sprite_world_z", 1_000);
        add(scenarios, "sprite_horizontal", "bench/sprite_horizontal", 1_000);
        add(scenarios, "sprite_frustum", "bench/sprite_frustum", 1_000);
        add(scenarios, "sprite_lod", "bench/sprite_lod", 1_000);
        // 64 independent emitters distributed around the camera, not a single
        // dense cloud. Tests per-cohort visibility and upload avoidance.
        add(scenarios, "distributed_analytic_64", "bench/analytic_uv", 0);
        add(scenarios, "budget_spike", "bench/budget_spike", 0);
        // Three independent 20k requests: per-effect caps are not sufficient.
        // The client-wide 30k/tick budget must reject 30k across instances.
        add(scenarios, "global_budget_spike", "bench/static", 0);
        add(scenarios, "reload_active", "bench/static", 1);
        add(scenarios, "curves", "examples/curves", 0);
        add(scenarios, "events", "examples/event_sequence", 0);
        add(scenarios, "uv_animation", "examples/animated_sprite", 0);
        for (var exhibit : net.kofllee.pyrovfx.museum.VfxMuseumCatalog.exhibits()) {
            scenarios.put("museum_" + exhibit.id(), new Scenario("museum_" + exhibit.id(),
                    exhibit.effect(), exhibit.count(), 0x50B0_2026L ^ exhibit.id().hashCode(),
                    10, 60));
        }
        var pig = net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("model_entity");
        scenarios.put("museum_model_entity_close_pan", new Scenario("museum_model_entity_close_pan",
                pig.effect(), pig.count(), 0x50B0_2026L ^ pig.id().hashCode(), 5, 90));
        var selfEmit = net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("self_emit");
        scenarios.put("museum_self_emit_near", new Scenario("museum_self_emit_near",
                selfEmit.effect(), selfEmit.count(), 0x50B0_2026L ^ selfEmit.id().hashCode(),
                10, 60));
        var camera = net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("model_custom");
        for (var name : new String[]{"museum_camera_front", "museum_camera_turn_90"}) {
            scenarios.put(name, new Scenario(name, camera.effect(), camera.count(),
                    0x50B0_2026L ^ camera.id().hashCode(), 10, 60));
        }
        var smoke = net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("smoke_alpha");
        for (var name : new String[]{"museum_smoke_camera_front", "museum_smoke_camera_turn_90"}) {
            scenarios.put(name, new Scenario(name, smoke.effect(), smoke.count(),
                    0x50B0_2026L ^ smoke.id().hashCode(), 10, 60));
        }
        var glass = net.kofllee.pyrovfx.museum.VfxMuseumCatalog.find("model_translucent");
        for (String alpha : new String[]{"zero", "full"}) {
            String name = "museum_translucency_" + alpha;
            scenarios.put(name, new Scenario(name,
                    ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/" + name),
                    glass.count(), 0x50B0_2026L ^ glass.id().hashCode(), 10, 60));
        }
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
