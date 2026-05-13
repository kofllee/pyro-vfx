package net.kofllee.pyrovfx.vfx.resource;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kofllee.pyrovfx.vfx.VfxDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class VfxJsonLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(VfxJsonLoader.class);
    private static final Gson GSON = new Gson();

    private static final String DIRECTORY = "vfx";
    private static final String EXTENSION = ".json";

    private VfxJsonLoader() {}

    public static void reload(ResourceManager resourceManager) {
        VfxRegistry.clear();

        Map<ResourceLocation, Resource> resources = resourceManager.listResources(DIRECTORY, id -> id.getPath().endsWith(EXTENSION));

        for(Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation effectId = toEffectId(fileId);

            try (Reader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);

                if(json == null) {
                    throw new IllegalArgumentException("JSON file is empty");
                }

                VfxDefinition definition = VfxDefinitionParser.parse(effectId, json);
                VfxRegistry.register(definition);

                LOGGER.info("Loaded Pyro VFX: {}", effectId);

            } catch (Exception exception) {
                LOGGER.error("Failed to load Pyro VFX {} from {}", effectId, fileId, exception);
            }
        }

        LOGGER.info("Loaded {} Pyro VFX definitions", VfxRegistry.ids().size());
    }

    private static ResourceLocation toEffectId(ResourceLocation fileId) {
        String path = fileId.getPath();

        if(path.startsWith(DIRECTORY + "/")) {
            path = path.substring((DIRECTORY + "/").length());
        }

        if(path.endsWith(EXTENSION)) {
            path = path.substring(0, path.length() - EXTENSION.length());
        }

        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), path);
    }
}
