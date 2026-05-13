package net.kofllee.pyrovfx.vfx.resource;


import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VfxRegistry {

    private static final Map<ResourceLocation, VfxDefinition> DEFINITIONS = new LinkedHashMap<>();

    private VfxRegistry() {}

    public static void register(VfxDefinition definition) {
        DEFINITIONS.put(definition.id(), definition);
    }

    public static VfxDefinition get(ResourceLocation id) {
        return DEFINITIONS.get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    public static Collection<ResourceLocation> ids() {
        return Collections.unmodifiableSet(DEFINITIONS.keySet());
    }

    public static void clear() {
        DEFINITIONS.clear();
    }
}
