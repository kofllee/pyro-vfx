package net.kofllee.pyrovfx.vfx.expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable per-definition variable-slot layout. Indices stay fixed after JSON compilation. */
public final class VfxVariableLayout {
    private static final String[] BUILTINS = {
            "effect.age", "effect.local_age", "effect.active_age", "effect.normalized_age",
            "effect.random", "effect.pos.x", "effect.pos.y", "effect.pos.z",
            "emitter.age", "emitter.local_age", "emitter.active_age", "emitter.normalized_age",
            "emitter.random", "emitter.spawned_particles", "emitter.pos.x", "emitter.pos.y", "emitter.pos.z",
            "particle.random", "spawn.pos.x", "spawn.pos.y", "spawn.pos.z",
            "particle.age", "particle.lifetime", "particle.normalized_age",
            "particle.scale.x", "particle.scale.y", "particle.scale.z",
            "particle.rotation.x", "particle.rotation.y", "particle.rotation.z",
            "particle.angular_velocity.x", "particle.angular_velocity.y", "particle.angular_velocity.z",
            "particle.pos.x", "particle.pos.y", "particle.pos.z",
            "particle.prev_pos.x", "particle.prev_pos.y", "particle.prev_pos.z",
            "particle.vel.x", "particle.vel.y", "particle.vel.z", "particle.speed"
    };
    public static final int EFFECT_AGE = 0, EFFECT_LOCAL_AGE = 1, EFFECT_ACTIVE_AGE = 2,
            EFFECT_NORMALIZED_AGE = 3, EFFECT_RANDOM = 4, EFFECT_POS = 5;
    public static final int EMITTER_AGE = 8, EMITTER_LOCAL_AGE = 9, EMITTER_ACTIVE_AGE = 10,
            EMITTER_NORMALIZED_AGE = 11, EMITTER_RANDOM = 12,
            EMITTER_SPAWNED = 13, EMITTER_POS = 14;
    public static final int PARTICLE_RANDOM = 17, SPAWN_POS = 18, PARTICLE_AGE = 21,
            PARTICLE_LIFETIME = 22, PARTICLE_NORMALIZED_AGE = 23, PARTICLE_SCALE = 24,
            PARTICLE_ROTATION = 27, PARTICLE_ANGULAR_VELOCITY = 30,
            PARTICLE_POS = 33, PARTICLE_PREV_POS = 36,
            PARTICLE_VEL = 39, PARTICLE_SPEED = 42;

    private final List<String> names;
    private final Map<String, Integer> indices;

    private VfxVariableLayout(List<String> names, Map<String, Integer> indices) {
        this.names = List.copyOf(names);
        this.indices = Map.copyOf(indices);
    }

    public int size() { return names.size(); }
    public int slot(String name) { return indices.getOrDefault(name, -1); }
    public String name(int slot) { return names.get(slot); }
    public List<String> names() { return names; }

    public static final class Builder {
        private final List<String> names = new ArrayList<>();
        private final Map<String, Integer> indices = new LinkedHashMap<>();

        public Builder() {
            for (String name : BUILTINS) {
                intern(name);
            }
        }

        public int intern(String name) {
            Integer existing = indices.get(name);
            if (existing != null) {
                return existing;
            }
            int next = names.size();
            names.add(name);
            indices.put(name, next);
            return next;
        }

        public VfxVariableLayout build() {
            return new VfxVariableLayout(names, indices);
        }
    }
}
