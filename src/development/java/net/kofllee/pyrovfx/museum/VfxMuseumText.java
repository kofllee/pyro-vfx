package net.kofllee.pyrovfx.museum;

import com.google.gson.Gson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Display;

import java.util.Map;

/**
 * Uses vanilla Text Display NBT decoding, which updates synchronized entity data.
 * TextDisplay's direct setters are private in Minecraft 1.21.1.
 */
public final class VfxMuseumText {
    private static final Gson JSON = new Gson();
    private VfxMuseumText() {}

    public static void configure(Display.TextDisplay display, String text, float scale) {
        CompoundTag tag = new CompoundTag();
        display.saveWithoutId(tag);
        tag.putString("text", JSON.toJson(Map.of("text", text)));
        tag.putString("billboard", "center");
        tag.putInt("line_width", 195);
        tag.putInt("background", 0xE0101420);
        tag.putBoolean("shadow", true);
        CompoundTag transform = new CompoundTag();
        ListTag size = new ListTag();
        for (int i=0; i<3; i++) size.add(FloatTag.valueOf(scale));
        transform.put("scale", size);
        tag.put("transformation", transform);
        display.load(tag);
    }

    public static void update(Display.TextDisplay display, String text) {
        CompoundTag tag = new CompoundTag();
        display.saveWithoutId(tag);
        tag.putString("text", JSON.toJson(Map.of("text", text)));
        display.load(tag);
    }
}
