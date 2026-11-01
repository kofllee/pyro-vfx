package net.kofllee.pyrovfx.vfx.resource;


import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VfxRegistry {

    // Publish a complete immutable snapshot, never an in-place partially reloaded map.
    private static volatile Map<ResourceLocation, VfxDefinition> DEFINITIONS = Map.of();

    private VfxRegistry() {}

    public static synchronized void register(VfxDefinition definition) {
        Map<ResourceLocation, VfxDefinition> next = new LinkedHashMap<>(DEFINITIONS);
        next.put(definition.id(), definition);
        replaceAll(next);
    }

    public static synchronized void replaceAll(Map<ResourceLocation, VfxDefinition> definitions) {
        DEFINITIONS = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static VfxDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    public static Collection<ResourceLocation> ids() {
        return DEFINITIONS.keySet();
    }

    public static synchronized void clear() {
        DEFINITIONS = Map.of();
    }
}
