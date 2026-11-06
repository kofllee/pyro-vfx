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
        configure(display, text, scale, 180f);
    }

    public static void configure(Display.TextDisplay display, String text, float scale, float yaw) {
        configure(display, text, scale, yaw, 240);
    }

    public static void configure(Display.TextDisplay display, String text, float scale, float yaw, int width) {
        CompoundTag tag = new CompoundTag();
        display.saveWithoutId(tag);
        tag.putString("text", JSON.toJson(Map.of("text", text)));
        tag.putString("billboard", "fixed");
        tag.put("Rotation", floats(yaw, 0f));
        tag.putInt("line_width", width);
        tag.putInt("background", 0xE0101420);
        tag.putBoolean("shadow", true);
        CompoundTag transform = new CompoundTag();
        transform.put("translation", floats(0, 0, 0));
        transform.put("left_rotation", floats(0, 0, 0, 1));
        transform.put("scale", floats(scale, scale, scale));
        transform.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", transform);
        display.load(tag);
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        return list;
    }

    public static void update(Display.TextDisplay display, String text) {
        CompoundTag tag = new CompoundTag();
        display.saveWithoutId(tag);
        tag.putString("text", JSON.toJson(Map.of("text", text)));
        display.load(tag);
    }
}
