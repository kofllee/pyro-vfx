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
    public enum Action { TOGGLE, SOLO, INFO }
    public enum GlobalAction { ALL_ON, ALL_OFF }
    public static BlockPos globalButton(GlobalAction action) {
        return new BlockPos(ORIGIN_X + 3 + action.ordinal() * 5,
                FLOOR_Y + 2, ORIGIN_Z + 2);
    }
    public static String legend(String id) {
        return switch (id) {
            case "spawn_point" -> "LEFT: BURST | RIGHT: STEADY";
            case "spawn_sphere", "spawn_box" -> "LEFT: VOLUME | RIGHT: SHELL";
            case "sprite_horizontal" -> "CAMERA | CYLINDRICAL | WORLD X | WORLD Y";
            case "sprite_additive" -> "LEFT: ALPHA | RIGHT: ADDITIVE";
            case "model_custom" -> "CUSTOM | BLOCK | ENTITY (PIG)";
            case "curve_bezier" -> "BEZIER | LINEAR | STEP | CATMULL";
            case "gpu_sprite" -> "LEFT: GPU ANALYTIC | RIGHT: CPU DENSE";
            case "runtime_birth" -> "GPU: BIRTH-TIME RADIUS + SPEED";
            case "runtime_orbit" -> "GPU: PARAMETRIC SHARED-AGE OFFSET";
            case "runtime_spin" -> "GPU: ANGULAR VELOCITY / DRAG";
            case "runtime_collision" -> "CPU DENSE: ORDERED ON_COLLISION";
            case "runtime_self_emit" -> "CPU DENSE: ON_COLLISION -> EMIT";
            case "runtime_coalesced" -> "GPU: 3 BURSTS -> 1 COHORT";
            case "runtime_lit" -> "CPU DENSE + GPU LIT INSTANCING";
            case "event_order" -> "STEP 1 (0t) > STEP 2 (30t) > STEP 3 (60t)";
            case "source_entity_local" -> "AMBER: LOCAL (FOLLOWS) | CYAN: WORLD (TRAIL)";
            case "collision_events" -> "LEFT: STOP | CENTER: BOUNCE | RIGHT: EXPIRE";
            default -> "";
        };
    }

    public record SampleLabel(double offsetX, String text) {}
    public static List<SampleLabel> sampleLabels(String id) {
        return switch (id) {
            case "sprite_horizontal" -> List.of(
                    new SampleLabel(-3.3, "CAMERA"),
                    new SampleLabel(-1.1, "CYLINDRICAL"),
                    new SampleLabel(1.1, "WORLD X"),
                    new SampleLabel(3.3, "WORLD Y"));
            case "curve_bezier" -> List.of(
                    new SampleLabel(-3.0, "BEZIER"),
                    new SampleLabel(-1.0, "LINEAR"),
                    new SampleLabel(1.0, "STEP"),
                    new SampleLabel(3.0, "CATMULL"));
            case "event_order" -> List.of(
                    new SampleLabel(-2.7, "1 : START"),
                    new SampleLabel(0, "2 : +30T"),
                    new SampleLabel(2.7, "3 : +60T"));
            case "collision_events" -> List.of(
                    new SampleLabel(-3.0, "NO BOUNCE"),
                    new SampleLabel(0, "BOUNCE x0.78"),
                    new SampleLabel(3.0, "ON HIT: VANISH"));
            case "gpu_sprite" -> List.of(
                    new SampleLabel(-2.2, "GPU ANALYTIC"),
                    new SampleLabel(2.2, "CPU DENSE"));
            case "sprite_additive" -> List.of(
                    new SampleLabel(-1.6, "ALPHA"),
                    new SampleLabel(1.6, "ADDITIVE"));
            case "model_custom" -> List.of(
                    new SampleLabel(-2.4, "CUSTOM"),
                    new SampleLabel(0, "BLOCK"),
                    new SampleLabel(2.4, "ENTITY"));
            default -> List.of();
        };
    }

    /** Exact VFX expressions, with t=particle.normalized_age and r=particle.random. */
    public static String formulas(String id) {
        return switch (id) {
            case "curve_bezier" -> "t = particle.normalized_age\n"
                    + "scale = curve.museum_bezier(t) * 6\n"
                    + "scale = curve.museum_linear(t) * 4\n"
                    + "scale = curve.museum_step(t) * 4\n"
                    + "scale = curve.museum_catmull(t) * 4";
            case "motion_parametric" -> "x = math.cos(particle.age * 0.12) * 1.2\n"
                    + "y = math.sin(particle.age * 0.16) * 0.7\n"
                    + "z = math.sin(particle.age * 0.12) * 1.2";
            case "rotation_parametric" -> "rotation.z = particle.normalized_age * 720";
            case "gpu_expression" -> "r = particle.random; t = particle.normalized_age\n"
                    + "scale = (0.3 + r * 0.2) * 4\n"
                    + "red = 0.5 + r * 0.5\n"
                    + "green = 0.2 + t * 0.7\n"
                    + "alpha = 1 - t * 0.7";
            case "gpu_motion" -> "green = 0.3 + abs(particle.vel.x) * 0.15\n"
                    + "scale.x = (0.3 + abs(sin(particle.pos.x * 0.05)) * 0.08) * 4\n"
                    + "scale.y = (0.3 + abs(sin(particle.prev_pos.x * 0.05)) * 0.08) * 4";
            case "collision_events" -> "g = -10 | v0 = 0 | spawn Y = 2.35\n"
                    + "LEFT: bounciness=0, expire_on_contact=false\n"
                    + "CENTER: bounciness=0.78\n"
                    + "RIGHT: expire_on_contact=true";
            case "event_order" -> "on_creation: stage = 1, emit(1)\n"
                    + "timeline(30t): stage += 1, emit(2)\n"
                    + "timeline(60t): stage += 1, emit(3)";
            case "source_distance" -> "travel_distance_looping(distance=0.65) -> emit(5)";
            case "sprite_additive" -> "same color and alpha; blend=SRC_ALPHA,ONE vs SRC_ALPHA,ONE_MINUS_SRC_ALPHA";
            case "gpu_sprite" -> "GPU radius=0.65 | CPU radius=param.cpuRadius (0.65)\n"
                    + "same particle motion; separate CPU/GPU backends";
            default -> "";
        };
    }

    public static String hint(String id, String fallback) {
        return switch (id) {
            case "sprite_horizontal" -> "Walk around: camera faces you; X is vertical, Y is horizontal.";
            case "curve_bezier" -> "Same particle, four independent size interpolation modes.";
            case "event_order" -> "Colored pulses appear left to right at 0, 30, 60 ticks.";
            case "collision_events" -> "Three falling samples hit three pads: stop, rebound, disappear.";
            case "gpu_sprite" -> "Same effect, two simulation backends. Compare side by side.";
            case "runtime_birth" -> "Randomized sphere radius and speed evaluated at birth, then resident on GPU.";
            case "runtime_orbit" -> "Watch the cloud orbit: one shared age-based displacement drives all sprites.";
            case "runtime_spin" -> "Look close: non-square sprites rotate together in GPU resident storage.";
            case "runtime_collision" -> "Particles hit real orange blocks; every collision increments a parameter.";
            case "runtime_self_emit" -> "One falling parent hits the platform and creates a small child burst.";
            case "runtime_coalesced" -> "Three immediate bursts share a GPU buffer; the fourth arrives 15 ticks later.";
            case "runtime_lit" -> "Check shadows and day lighting: CPU Dense simulated, GPU-instanced lightmap.";
            case "source_entity_local" -> "Watch the moving husk: amber follows, cyan leaves a trail.";
            case "source_model_part" -> "Watch the husk's head: sparks follow the actual head socket.";
            case "sprite_additive" -> "Identical still clouds: compare alpha versus additive blending.";
            case "model_custom" -> "Three stationary 3D sources: custom / block / pig.";
            case "source_block_entity" -> "Small pink aura belongs to the barrel BlockEntity.";
            default -> fallback;
        };
    }

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
            return base().offset(3 + action.ordinal() * 3, 2, 10);
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
