package net.kofllee.pyrovfx.museum;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** One catalog for map builder, client controls, generated JSON and regression tests. */
public final class VfxMuseumCatalog {
    public static final int ORIGIN_X = 512, FLOOR_Y = 140, ORIGIN_Z = 512;
    public static final int COLUMNS = 6, CELL_X = 12, CELL_Z = 13;
    public static final BlockPos MARKER = new BlockPos(ORIGIN_X, FLOOR_Y + 1, ORIGIN_Z);
    public enum Action { PLAY, STOP, COMPARE, INFO }

    public record Exhibit(String id, String category, String title, String source, int count,
                          double visualScale, String description, int index) {
        public ResourceLocation effect() {
            return ResourceLocation.fromNamespaceAndPath("pyro_vfx", "museum/" + id);
        }
        public BlockPos base() {
            return new BlockPos(ORIGIN_X + (index % COLUMNS) * CELL_X,
                    FLOOR_Y, ORIGIN_Z + (index / COLUMNS) * CELL_Z);
        }
        public BlockPos effectOrigin() { return base().offset(6, 3, 5); }
        public BlockPos button(Action action) {
            return base().offset(2 + action.ordinal() * 2, 2, 10);
        }
    }

    private static final List<Exhibit> EXHIBITS = load();
    private VfxMuseumCatalog() {}
    public static List<Exhibit> exhibits() { return EXHIBITS; }
    public static Exhibit find(String id) {
        return EXHIBITS.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }
    public static int rows() {
        return (EXHIBITS.size() + COLUMNS - 1) / COLUMNS;
    }
    public static boolean inside(int x, int y, int z) {
        return x >= ORIGIN_X - 4 && x < ORIGIN_X + COLUMNS * CELL_X + 4
                && y >= FLOOR_Y - 5 && y <= FLOOR_Y + 16
                && z >= ORIGIN_Z - 4 && z < ORIGIN_Z + rows() * CELL_Z + 4;
    }
    private static List<Exhibit> load() {
        List<Exhibit> exhibits = new ArrayList<>();
        try (var stream = VfxMuseumCatalog.class.getClassLoader()
                .getResourceAsStream("pyro_vfx_museum_exhibits.tsv")) {
            if (stream == null) throw new IllegalStateException("Missing museum manifest");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] data = line.split("\\|", -1);
                    if (data.length != 7) throw new IllegalStateException("Invalid museum manifest line: " + line);
                    if (exhibits.stream().anyMatch(e -> e.id().equals(data[0])))
                        throw new IllegalStateException("Duplicate museum exhibit: " + data[0]);
                    exhibits.add(new Exhibit(data[0], data[1], data[2], data[3],
                            Integer.parseInt(data[4]), Double.parseDouble(data[5]),
                            data[6], exhibits.size()));
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load museum catalog", e);
        }
        return List.copyOf(exhibits);
    }
}
