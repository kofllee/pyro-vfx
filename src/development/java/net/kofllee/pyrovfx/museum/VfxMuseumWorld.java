package net.kofllee.pyrovfx.museum;

import net.kofllee.pyrovfx.PyroVfx;
import net.minecraft.ChatFormatting;
import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import static net.minecraft.commands.Commands.literal;

/**
 * Creates a reproducible physical testing gallery in the player's world.
 * The fixed anchor means clients do not need custom packets to discover the
 * exhibition: ordinary buttons are synchronized by Minecraft itself.
 *
 * This is intentionally an explicit operator command; no world changes occur
 * on startup or when merely joining a world.
 */
@EventBusSubscriber(modid = PyroVfx.MOD_ID)
public final class VfxMuseumWorld {
    public static final int TRANSPARENCY_Z =
            VfxMuseumCatalog.ORIGIN_Z + VfxMuseumCatalog.rows() * VfxMuseumCatalog.CELL_Z + 16;
    public static final int TRANSPARENCY_X = VfxMuseumCatalog.ORIGIN_X + 7;
    public static final int TRANSPARENCY_SPACING = 21;
    public static final int EXPERIMENT_LANES = 8;
    // Deliberately at the actual vanilla cloud altitude (Y=192..196).
    // The ordinary gallery at Y~140 cannot validate cloud layer occlusion.
    public static final int CLOUD_LAB_X = TRANSPARENCY_X + EXPERIMENT_LANES * TRANSPARENCY_SPACING + 12;
    public static final int CLOUD_LAB_Y = 183;
    private static final String[] SAMPLE_LABELS = {
            "ALPHA WORLD-LIT", "ALPHA ANALYTIC", "ADDITIVE ANALYTIC", "ADDITIVE LIT",
            "CAMERA HORIZONTAL", "WORLD-Z BILLBOARD", "SPINNING SPRITES", "SOLID BLOCK MODEL",
            "MODEL CUTOUT / ITEM", "MODEL ADDITIVE", "MODEL TRANSLUCENT", "MODEL ENTITY"
    };
    private static final double[] SAMPLE_COLUMNS = {-4.6,-1.55,1.55,4.6};
    private static final double[] SAMPLE_ROWS = {-0.65,1.95,4.55};
    public static void visitCloudLab(net.minecraft.server.level.ServerPlayer player) {
        player.connection.teleport(CLOUD_LAB_X + 12.5, CLOUD_LAB_Y + 2,
                TRANSPARENCY_Z + 3.5, 0f, -23f);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    }

    public static Vec3 transparencyOrigin(int lane) {
        return new Vec3(TRANSPARENCY_X + lane * TRANSPARENCY_SPACING + 6.5,
                VfxMuseumCatalog.FLOOR_Y + 3.5, TRANSPARENCY_Z + 16.5);
    }

    public static void teleportTransparency(net.minecraft.server.level.ServerPlayer player, int lane) {
        var pos = transparencyOrigin(lane);
        // Send position AND yaw/pitch together. Calling setYRot after a
        // position-only teleport changes only the server's copy: the actual
        // client retains its previous save-camera rotation.
        player.connection.teleport(pos.x, VfxMuseumCatalog.FLOOR_Y + 2.0,
                TRANSPARENCY_Z + 3.5, 0f, -5f);
    }

    public static void visitExperiment(net.minecraft.server.level.ServerPlayer player, int lane) {
        teleportTransparency(player, lane);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    }

    private VfxMuseumWorld() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("pyrovfxmuseum")
                .then(literal("build").requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            build(level);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("Pyro VFX Museum built at "
                                            + VfxMuseumCatalog.ORIGIN_X + " "
                                            + VfxMuseumCatalog.FLOOR_Y + " "
                                            + VfxMuseumCatalog.ORIGIN_Z
                                            + ". Use /pyrovfxmuseum visit"), true);
                            return VfxMuseumCatalog.exhibits().size();
                        }))
                .then(literal("visit").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    // Keep each world/dimension independent; this is not a cross-dimensional portal.
                    player.teleportTo(VfxMuseumCatalog.ORIGIN_X + 6.5,
                            VfxMuseumCatalog.FLOOR_Y + 2,
                            VfxMuseumCatalog.ORIGIN_Z + 4.5);
                    return 1;
                }))
                .then(literal("runtime").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    var stand = VfxMuseumCatalog.find("runtime_collision");
                    if (stand == null) return 0;
                    player.teleportTo(stand.base().getX() + 6.5,
                            stand.base().getY() + 2.0, stand.base().getZ() + 2.5);
                    player.setYRot(0);
                    player.setXRot(-6);
                    return 1;
                }))
                .then(literal("transparency")
                        .executes(ctx -> {
                            teleportTransparency(ctx.getSource().getPlayerOrException(), 1);
                            return 1;
                        })
                        .then(literal("clear").executes(ctx -> {
                            teleportTransparency(ctx.getSource().getPlayerOrException(), 0);
                            return 1;
                        }))
                        .then(literal("glass").executes(ctx -> {
                            teleportTransparency(ctx.getSource().getPlayerOrException(), 1);
                            return 1;
                        }))
                        .then(literal("water").executes(ctx -> {
                            teleportTransparency(ctx.getSource().getPlayerOrException(), 2);
                            return 1;
                        }))
                        .then(literal("submerged").executes(ctx -> {
                            teleportTransparency(ctx.getSource().getPlayerOrException(), 3);
                            return 1;
                        }))
                        .then(literal("underwater").executes(ctx -> {
                            teleportTransparency(ctx.getSource().getPlayerOrException(), 4);
                            return 1;
                        }))
                        .then(literal("tinted").executes(ctx -> {
                            teleportTransparency(ctx.getSource().getPlayerOrException(), 5);
                            return 1;
                        })))
                .then(literal("experiments")
                        .then(literal("build").requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    buildTransparencyShowcase(ctx.getSource().getLevel());
                                    visitExperiment(ctx.getSource().getPlayerOrException(), 1);
                                    return 1;
                                }))
                        .then(literal("clear").executes(ctx -> {
                            visitExperiment(ctx.getSource().getPlayerOrException(), 0); return 1;
                        }))
                        .then(literal("glass").executes(ctx -> {
                            visitExperiment(ctx.getSource().getPlayerOrException(), 1); return 1;
                        }))
                        .then(literal("water").executes(ctx -> {
                            visitExperiment(ctx.getSource().getPlayerOrException(), 2); return 1;
                        }))
                        .then(literal("inside").executes(ctx -> {
                            visitExperiment(ctx.getSource().getPlayerOrException(), 3); return 1;
                        }))
                        .then(literal("underwater").executes(ctx -> {
                            visitExperiment(ctx.getSource().getPlayerOrException(), 4); return 1;
                        }))
                        .then(literal("tinted").executes(ctx -> {
                            visitExperiment(ctx.getSource().getPlayerOrException(), 5); return 1;
                        }))
                        .then(literal("opaque").executes(ctx -> {
                            visitExperiment(ctx.getSource().getPlayerOrException(), 6); return 1;
                        }))
                        .then(literal("overlap").executes(ctx -> {
                            visitExperiment(ctx.getSource().getPlayerOrException(), 7); return 1;
                        }))
                        .then(literal("clouds").executes(ctx -> {
                            visitCloudLab(ctx.getSource().getPlayerOrException()); return 1;
                        })))
                .then(literal("network").requires(src -> src.hasPermission(2))
                        .then(literal("static").executes(ctx ->
                                VfxNetworkMuseum.start(ctx.getSource().getPlayerOrException(), "static")))
                        .then(literal("entity").executes(ctx ->
                                VfxNetworkMuseum.start(ctx.getSource().getPlayerOrException(), "entity")))
                        .then(literal("offset").executes(ctx ->
                                VfxNetworkMuseum.start(ctx.getSource().getPlayerOrException(), "offset")))
                        .then(literal("socket").executes(ctx ->
                                VfxNetworkMuseum.start(ctx.getSource().getPlayerOrException(), "socket")))
                        .then(literal("block").executes(ctx ->
                                VfxNetworkMuseum.start(ctx.getSource().getPlayerOrException(), "block")))
                        .then(literal("stream").executes(ctx ->
                                VfxNetworkMuseum.start(ctx.getSource().getPlayerOrException(), "stream")))
                        .then(literal("auto").executes(ctx ->
                                VfxNetworkMuseum.start(ctx.getSource().getPlayerOrException(), "auto")))
                        .then(literal("event").executes(ctx ->
                                VfxNetworkMuseum.start(ctx.getSource().getPlayerOrException(), "event")))
                        .then(literal("trigger").executes(ctx ->
                                VfxNetworkMuseum.trigger(ctx.getSource().getPlayerOrException())))
                        .then(literal("parameter").executes(ctx ->
                                VfxNetworkMuseum.parameter(ctx.getSource().getPlayerOrException())))
                        .then(literal("rebind").executes(ctx ->
                                VfxNetworkMuseum.rebind(ctx.getSource().getPlayerOrException())))
                        .then(literal("stop").executes(ctx ->
                                VfxNetworkMuseum.stop(ctx.getSource().getPlayerOrException())))
                        .then(literal("status").executes(ctx ->
                                VfxNetworkMuseum.status(ctx.getSource().getPlayerOrException())))));
    }

    /** Build in a dedicated, fixed developer world; no runtime gameplay registration. */
    public static void build(ServerLevel level) {
        // Initial museum setup may run before the player teleports here. Force
        // load the former/current display chunks before querying old entities;
        // unloaded TextDisplays otherwise survive every rebuild and duplicate.
        preloadMuseumChunks(level);
        removeServiceEntities(level);
        clearOldGallery(level);
        for (var exhibit : VfxMuseumCatalog.exhibits()) {
            buildExhibit(level, exhibit);
        }
        buildGlobalConsole(level);
        if (Boolean.getBoolean("pyrovfx.museum.runtimeShowcase")
                || Boolean.getBoolean("pyrovfx.museum.visualOnly"))
            buildTransparencyShowcase(level);
        place(level, VfxMuseumCatalog.MARKER, Blocks.LODESTONE.defaultBlockState());
        place(level, VfxMuseumCatalog.MARKER.above(), Blocks.SEA_LANTERN.defaultBlockState());
        level.setDefaultSpawnPos(new BlockPos(VfxMuseumCatalog.ORIGIN_X + 6,
                VfxMuseumCatalog.FLOOR_Y + 2, VfxMuseumCatalog.ORIGIN_Z + 4), 180f);
    }

    /** Rendering compatibility gallery: no legacy exhibits,
     * controls, global console or comparison labels.
     * Launched only in its own dedicated developer world. */
    public static void buildVisualOnly(ServerLevel level) {
        buildTransparencyShowcase(level);
        level.setDayTime(6000L);
        level.setDefaultSpawnPos(new BlockPos(TRANSPARENCY_X+6,
                VfxMuseumCatalog.FLOOR_Y+2, TRANSPARENCY_Z+4), 0f);
    }

    /** Developer-only walkable visibility comparison, never added to the
     * production VFX block set or to regular user worlds. */
    private static void buildTransparencyShowcase(ServerLevel level) {
        int y0 = VfxMuseumCatalog.FLOOR_Y;
        if (Boolean.getBoolean("pyrovfx.museum.visualOnly")) {
            // Rebuilding on every lab client launch used to accumulate TextDisplay
            // entities in the dedicated save. Clean only this lab's marked area.
            for (int cx=TRANSPARENCY_X>>4;cx<=(CLOUD_LAB_X+25)>>4;cx++)
                for (int cz=TRANSPARENCY_Z>>4;cz<=(TRANSPARENCY_Z+25)>>4;cz++)
                    level.getChunk(cx,cz);
            var area=new AABB(TRANSPARENCY_X-1, y0-2, TRANSPARENCY_Z-2,
                    CLOUD_LAB_X+26, CLOUD_LAB_Y+34, TRANSPARENCY_Z+26);
            for (var label:level.getEntitiesOfClass(Display.TextDisplay.class,area,
                    entity -> entity.getTags().contains("pyrovfx_museum")))
                label.discard();
        }
        for (int lane = 0; lane < EXPERIMENT_LANES; lane++) {
            int x0 = TRANSPARENCY_X + lane * TRANSPARENCY_SPACING;
            int z0 = TRANSPARENCY_Z;
            for (int cx = x0 >> 4; cx <= (x0 + 14) >> 4; cx++)
                for (int cz = z0 >> 4; cz <= (z0 + 24) >> 4; cz++)
                    level.getChunk(cx, cz);
            // Clear previous runs and build a physically navigable platform.
            for (int dx = 0; dx <= 14; dx++)
                for (int dz = 0; dz <= 24; dz++) {
                    for (int dy = 1; dy <= 12; dy++) {
                        BlockPos pos = new BlockPos(x0 + dx, y0 + dy, z0 + dz);
                        if (!level.getBlockState(pos).isAir())
                            place(level, pos, Blocks.AIR.defaultBlockState());
                    }
                    var floor = (dx == 0 || dx == 14 || dz == 0 || dz == 24)
                            ? Blocks.YELLOW_CONCRETE : Blocks.SMOOTH_STONE;
                    place(level, new BlockPos(x0 + dx, y0, z0 + dz), floor.defaultBlockState());
                }
            // Dark distant backing makes the identical alpha sprites readable
            // through water and glass, rather than disappearing into blue sky.
            for (int dx = 1; dx <= 13; dx++)
                for (int dy = 1; dy <= 10; dy++)
                    place(level, new BlockPos(x0 + dx, y0 + dy, z0 + 23),
                            Blocks.BLACK_CONCRETE.defaultBlockState());
            if (lane == 1) {
                for (int dx = 1; dx <= 13; dx++)
                    for (int dy = 2; dy <= 9; dy++)
                        place(level, new BlockPos(x0 + dx, y0 + dy, z0 + 9),
                                Blocks.GLASS.defaultBlockState());
            } else if (lane == 2) {
                // Sealed source-water plane: the observer's line of sight
                // actually crosses water between z+8 (glass) and z+10 (glass).
                for (int dx = 1; dx <= 13; dx++)
                    for (int dy = 2; dy <= 9; dy++) {
                        place(level, new BlockPos(x0 + dx, y0 + dy, z0 + 8),
                                Blocks.GLASS.defaultBlockState());
                        place(level, new BlockPos(x0 + dx, y0 + dy, z0 + 10),
                                Blocks.GLASS.defaultBlockState());
                        place(level, new BlockPos(x0 + dx, y0 + dy, z0 + 9),
                                Blocks.WATER.defaultBlockState());
                    }
                for (int dz = 8; dz <= 10; dz++) {
                    for (int dx = 0; dx <= 14; dx++)
                        for (int dy : new int[]{1, 10})
                            place(level, new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                                    Blocks.GLASS.defaultBlockState());
                    for (int dy = 2; dy <= 9; dy++)
                        for (int dx : new int[]{0, 14})
                            place(level, new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                                    Blocks.GLASS.defaultBlockState());
                }
            } else if (lane == 5) {
                // The SAME large red alpha sprite spans both sides of the
                // tinted-glass edge. Move the camera sideways to catch the
                // edge case without using an automated camera.
                for (int dx = 1; dx <= 6; dx++)
                    for (int dy = 2; dy <= 9; dy++)
                        place(level, new BlockPos(x0 + dx, y0 + dy, z0 + 9),
                                Blocks.TINTED_GLASS.defaultBlockState());
            } else if (lane == 6) {
                // Zero special-case depth hacks: ordinary opaque blocks must
                // fully cover both prototype alpha and native model geometry.
                for (int dx = 1; dx <= 13; dx++)
                    for (int dy = 2; dy <= 9; dy++)
                        place(level, new BlockPos(x0 + dx,y0 + dy,z0 + 9),
                                Blocks.STONE.defaultBlockState());
            } else if (lane == 3 || lane == 4) {
                // For these stages the colored particles are physically
                // INSIDE the fluid. Lane 3 is viewed through the water from
                // air; lane 4 immerses the viewer in the same water volume.
                int zNear = lane == 4 ? 2 : 12;
                int zFar = 21;
                for (int dx = 0; dx <= 14; dx++)
                    for (int dy = 1; dy <= 10; dy++)
                        for (int dz = zNear - 1; dz <= zFar + 1; dz++)
                            if (dx == 0 || dx == 14 || dy == 1 || dy == 10
                                    || dz == zNear - 1 || dz == zFar + 1)
                                place(level, new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                                        Blocks.GLASS.defaultBlockState());
                for (int dx = 1; dx <= 13; dx++)
                    for (int dy = 2; dy <= 9; dy++)
                        for (int dz = zNear; dz <= zFar; dz++)
                            place(level, new BlockPos(x0 + dx, y0 + dy, z0 + dz),
                                    Blocks.WATER.defaultBlockState());
            }
            String name = switch (lane) {
                case 0 -> "CLEAR AIR";
                case 1 -> "BEHIND GLASS";
                case 2 -> "BEHIND WATER";
                case 3 -> "PARTICLES INSIDE WATER - VIEW FROM AIR";
                case 4 -> "CAMERA + PARTICLES INSIDE WATER";
                case 5 -> "HALF TINTED GLASS - WALK SIDEWAYS";
                case 6 -> "OPAQUE WALL - NOTHING VISIBLE THROUGH STONE";
                default -> "INTERSECTING SPRITES + TRANSLUCENT MODELS";
            };
            Display.TextDisplay sign = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
            sign.setPos(x0 + 7.5, y0 + 11.2, z0 + 11);
            VfxMuseumText.configure(sign, name, 0.85f, 180f, 320);
            sign.addTag("pyrovfx_museum");
            level.addFreshEntity(sign);
            if (Boolean.getBoolean("pyrovfx.museum.visualOnly")) {
                buildSampleLabels(level,transparencyOrigin(lane));
            }
        }
        if (Boolean.getBoolean("pyrovfx.museum.visualOnly"))
            buildCloudLab(level);
    }

    private static void buildSampleLabels(ServerLevel level, Vec3 center) {
        for (int i=0;i<SAMPLE_LABELS.length;i++) {
            Display.TextDisplay tag=new Display.TextDisplay(EntityType.TEXT_DISPLAY,level);
            tag.setPos(center.x + SAMPLE_COLUMNS[i%4],
                    center.y + SAMPLE_ROWS[i/4] + 0.83,
                    center.z + 3.7);
            VfxMuseumText.configure(tag,SAMPLE_LABELS[i],0.23f,180f,190);
            tag.addTag("pyrovfx_museum");
            tag.addTag("pyrovfx_render_matrix");
            level.addFreshEntity(tag);
        }
    }

    private static void buildCloudLab(ServerLevel level) {
        int x0=CLOUD_LAB_X, z0=TRANSPARENCY_Z, y0=CLOUD_LAB_Y;
        for (int cx=x0>>4;cx<=(x0+24)>>4;cx++)
            for (int cz=z0>>4;cz<=(z0+24)>>4;cz++) level.getChunk(cx,cz);
        // No fake cloud meshes: the real moving vanilla clouds cross Y=192..196.
        // The near emitters are BELOW that layer and the far ones ABOVE it.
        for (int dx=0;dx<=24;dx++)
            for (int dz=0;dz<=24;dz++) {
                var floor=(dx==0||dx==24||dz==0||dz==24)
                        ? Blocks.PURPLE_CONCRETE : Blocks.SMOOTH_STONE;
                place(level,new BlockPos(x0+dx,y0,z0+dz),floor.defaultBlockState());
            }
        Display.TextDisplay label=new Display.TextDisplay(EntityType.TEXT_DISPLAY,level);
        label.setPos(x0+12.5,y0+5,z0+5);
        VfxMuseumText.configure(label,
                "REAL MINECRAFT CLOUDS / Y=192..196\n"
                + "12 PARTICLE TYPES ON EACH SIDE OF CLOUD HEIGHT\n"
                + "NEAR: BELOW CLOUDS | FAR: ABOVE CLOUDS",
                0.57f,180f,450);
        label.addTag("pyrovfx_museum");
        level.addFreshEntity(label);
        Vec3 near=new Vec3(x0+7.0,186.3,z0+16.5);
        Vec3 far=new Vec3(x0+17.0,198.5,z0+16.5);
        buildSampleLabels(level,near);
        buildSampleLabels(level,far);
        Display.TextDisplay nearSign=new Display.TextDisplay(EntityType.TEXT_DISPLAY,level);
        nearSign.setPos(x0+7.0,y0+2.5,z0+12);
        VfxMuseumText.configure(nearSign,"12 TYPES / NEAR (BELOW CLOUDS)",
                0.37f,180f,280);
        nearSign.addTag("pyrovfx_museum");
        level.addFreshEntity(nearSign);
        Display.TextDisplay farSign=new Display.TextDisplay(EntityType.TEXT_DISPLAY,level);
        farSign.setPos(x0+17.0,y0+13.2,z0+12);
        VfxMuseumText.configure(farSign,"12 TYPES / FAR (ABOVE CLOUDS)",
                0.37f,180f,280);
        farSign.addTag("pyrovfx_museum");
        level.addFreshEntity(farSign);
    }

    private static void preloadMuseumChunks(ServerLevel level) {
        int minX = VfxMuseumCatalog.ORIGIN_X - 4;
        int maxX = VfxMuseumCatalog.ORIGIN_X
                + VfxMuseumCatalog.COLUMNS * VfxMuseumCatalog.CELL_X + 4;
        int minZ = VfxMuseumCatalog.ORIGIN_Z - 4;
        int maxZ = VfxMuseumCatalog.ORIGIN_Z
                + VfxMuseumCatalog.rows() * VfxMuseumCatalog.CELL_Z + 4;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++)
                level.getChunk(cx, cz);
    }

    private static void removeServiceEntities(ServerLevel level) {
        AABB region = new AABB(VfxMuseumCatalog.ORIGIN_X - 4, VfxMuseumCatalog.FLOOR_Y - 4,
                VfxMuseumCatalog.ORIGIN_Z - 4,
                Math.max(VfxMuseumCatalog.ORIGIN_X + 90,
                        TRANSPARENCY_X + EXPERIMENT_LANES * TRANSPARENCY_SPACING + 15),
                VfxMuseumCatalog.FLOOR_Y + 18,
                VfxMuseumCatalog.ORIGIN_Z + 245);
        for (Entity entity : level.getEntities((Entity) null, region)) {
            if (entity.getTags().contains("pyrovfx_museum"))
                entity.discard();
        }
    }

    /** Remove the historical 66-stand layout only when the old sign is present. */
    private static void clearOldGallery(ServerLevel level) {
        if (!level.getBlockState(new BlockPos(VfxMuseumCatalog.ORIGIN_X + 7,
                VfxMuseumCatalog.FLOOR_Y + 1, VfxMuseumCatalog.ORIGIN_Z + 14)).is(Blocks.OAK_SIGN))
            return;
        for (int i = 0; i < 66; i++) {
            BlockPos base = new BlockPos(VfxMuseumCatalog.ORIGIN_X + i % 5 * 15,
                    VfxMuseumCatalog.FLOOR_Y, VfxMuseumCatalog.ORIGIN_Z + i / 5 * 17);
            for (int x = 0; x <= 16; x++)
                for (int z = 0; z <= 18; z++)
                    for (int y = 0; y <= 8; y++) {
                        BlockPos pos = base.offset(x,y,z);
                        if (!level.getBlockState(pos).isAir())
                            place(level,pos,Blocks.AIR.defaultBlockState());
                    }
        }
    }

    private static net.minecraft.world.level.block.Block accent(String category) {
        return switch (category) {
            case "Emission" -> Blocks.CYAN_CONCRETE;
            case "Motion & Rotation" -> Blocks.ORANGE_CONCRETE;
            case "Appearance & Rendering" -> Blocks.PURPLE_CONCRETE;
            case "Curves & Expressions" -> Blocks.LIME_CONCRETE;
            case "Events & Collisions" -> Blocks.RED_CONCRETE;
            case "Transforms & Attachments" -> Blocks.LIGHT_BLUE_CONCRETE;
            case "CPU / GPU" -> Blocks.YELLOW_CONCRETE;
            default -> Blocks.WHITE_CONCRETE;
        };
    }

    private static void buildExhibit(ServerLevel level, VfxMuseumCatalog.Exhibit e) {
        var base = e.base();
        var palette = accent(e.category());
        for (int x = 0; x < VfxMuseumCatalog.CELL_X; x++) {
            for (int z = 0; z < VfxMuseumCatalog.CELL_Z; z++) {
                boolean edge = x == 0 || x == VfxMuseumCatalog.CELL_X-1
                        || z == 0 || z == VfxMuseumCatalog.CELL_Z-1;
                place(level, base.offset(x,0,z), (edge ? palette : Blocks.SMOOTH_STONE).defaultBlockState());
            }
        }
        for (int x=1; x <= 10; x++) {
            for (int y=2; y<=8; y++) {
                place(level,base.offset(x,y,11),
                        (y==8 || x==1 || x==10 ? palette : Blocks.BLACK_CONCRETE).defaultBlockState());
            }
        }
        place(level,base.offset(5,1,11),Blocks.SEA_LANTERN.defaultBlockState());
        place(level,base.offset(6,1,11),Blocks.SEA_LANTERN.defaultBlockState());
        if (e.id().equals("collision_events")) {
            // Each drop has a matching full-block collision pad and an empty
            // two-block gap to the next lane. Nothing is simulated in the air.
            var pads = new net.minecraft.world.level.block.Block[] {
                    Blocks.LIGHT_BLUE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.RED_CONCRETE
            };
            for (int i = 0; i < pads.length; i++) {
                int x = 3 + i * 3;
                for (int z = 4; z <= 6; z++)
                    place(level,base.offset(x,1,z),pads[i].defaultBlockState());
            }
        }
        if (e.id().equals("runtime_collision") || e.id().equals("runtime_self_emit")) {
            // The two event test fixtures must hit real Minecraft blocks.
            // A broad stage (not an invisible collision override) keeps the
            // physics visible from any viewing angle at this stand.
            for (int x = 3; x <= 9; x++)
                for (int z = 3; z <= 7; z++)
                    place(level, base.offset(x, 1, z),
                            Blocks.ORANGE_CONCRETE.defaultBlockState());
        }
        for (VfxMuseumCatalog.Action action : VfxMuseumCatalog.Action.values()) {
            var button=e.button(action);
            var pedestal = switch (action) {
                case TOGGLE -> Blocks.LIME_CONCRETE;
                case SOLO -> Blocks.YELLOW_CONCRETE;
                case INFO -> Blocks.LIGHT_BLUE_CONCRETE;
            };
            place(level,button.below(),pedestal.defaultBlockState());
            place(level,button,Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR));
            spawnText(level,e,"control_"+action.name().toLowerCase(java.util.Locale.ROOT),
                    switch(action) {
                        case TOGGLE -> "ON/OFF";
                        case SOLO -> "SOLO";
                        case INFO -> "INFO";
                    }, button.getX()-base.getX()+0.5, 3.0, 10.68, 0.42f);
        }
        spawnText(level,e,"title",e.title() + "\\n" + e.category(),
                6.5,7.2,10.68,0.62f,190);
        spawnText(level,e,"status","ENABLED  |  LOADING",6.5,5.9,10.68,0.46f);
        String formula = VfxMuseumCatalog.formulas(e.id());
        spawnText(level,e,"details",VfxMuseumCatalog.hint(e.id(),e.description()),
                6.5,formula.isBlank() ? 4.6 : 5.05,10.68,0.33f);
        if (!formula.isBlank())
            spawnText(level,e,"formula",formula,6.5,4.30,10.68,0.26f,460);
        if (!VfxMuseumCatalog.legend(e.id()).isBlank()
                && VfxMuseumCatalog.sampleLabels(e.id()).isEmpty())
            spawnText(level,e,"legend",VfxMuseumCatalog.legend(e.id()),6.5,3.50,10.68,0.34f);
        int labelIndex = 0;
        for (var label : VfxMuseumCatalog.sampleLabels(e.id()))
            spawnText(level,e,"sample_"+(labelIndex++),label.text(),
                    6.5+label.offsetX(),5.2,7.75,0.32f);
        BlockPos anchor=base.offset(6,1,5);
        if (e.id().equals("source_block"))
            place(level,anchor,Blocks.COPPER_BLOCK.defaultBlockState());
        else if (e.id().equals("source_block_entity"))
            place(level,anchor,Blocks.BARREL.defaultBlockState());
        if (e.id().equals("source_entity_local") ||
                e.id().equals("source_model_part") ||
                e.id().equals("source_custom") ||
                e.id().equals("source_distance") ||
                e.id().equals("source_model_local")) {
            Husk model=EntityType.HUSK.create(level);
            if (model != null) {
                model.setPos(base.getX()+6.5,base.getY()+1,base.getZ()+5.5);
                model.setNoAi(true);
                model.setSilent(true);
                model.setInvulnerable(true);
                model.setGlowingTag(true);
                // Glowing outline is enough: a vanilla mob nameplate scales up
                // and overlays the scene when inspected from close range.
                model.setCustomName(Component.literal("VFX SOURCE [" + e.id() + "]"));
                model.setCustomNameVisible(false);
                model.setPersistenceRequired();
                model.addTag("pyrovfx_museum");
                model.addTag("pyrovfx_museum_source_"+e.id());
                level.addFreshEntity(model);
            }
        }
    }

    /** Shared museum-wide switchboard at the entrance. */
    private static void buildGlobalConsole(ServerLevel level) {
        var base = VfxMuseumCatalog.exhibits().getFirst().base();
        for (int x=1; x<=10; x++) {
            for (int y=2; y<=5; y++)
                place(level,base.offset(x,y,0),
                        (y == 5 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE).defaultBlockState());
        }
        spawnGlobalText(level,"MUSEUM CONTROLS",6.5,5.1,0.75,0.72f);
        for (var action : VfxMuseumCatalog.GlobalAction.values()) {
            BlockPos button=VfxMuseumCatalog.globalButton(action);
            place(level,button.below(),
                    (action == VfxMuseumCatalog.GlobalAction.ALL_ON
                            ? Blocks.LIME_CONCRETE : Blocks.RED_CONCRETE).defaultBlockState());
            place(level,button,Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR));
            spawnGlobalText(level, action==VfxMuseumCatalog.GlobalAction.ALL_ON ? "ALL ON" : "ALL OFF",
                    button.getX()-base.getX()+0.5,3.15,1.2,0.52f);
        }
    }

    private static void spawnGlobalText(ServerLevel level,String label,
                                        double x,double y,double z,float scale) {
        Display.TextDisplay display=new Display.TextDisplay(EntityType.TEXT_DISPLAY,level);
        display.setPos(VfxMuseumCatalog.ORIGIN_X+x,VfxMuseumCatalog.FLOOR_Y+y,VfxMuseumCatalog.ORIGIN_Z+z);
        VfxMuseumText.configure(display,label,scale,0f);
        display.addTag("pyrovfx_museum");
        level.addFreshEntity(display);
    }

    /** Real TextDisplay entities backed by solid black concrete, never cropped signs. */
    private static void spawnText(ServerLevel level,VfxMuseumCatalog.Exhibit e,
                                  String kind,String text,double x,double y,double z,float scale) {
        spawnText(level,e,kind,text,x,y,z,scale,240);
    }

    private static void spawnText(ServerLevel level,VfxMuseumCatalog.Exhibit e,
                                  String kind,String text,double x,double y,double z,float scale,int width) {
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY,level);
        display.setPos(e.base().getX()+x,e.base().getY()+y,e.base().getZ()+z);
        VfxMuseumText.configure(display,text.replace("\\n","\n"),scale,180f,width);
        display.addTag("pyrovfx_museum");
        display.addTag("pyrovfx_museum_"+e.id()+"_"+kind);
        level.addFreshEntity(display);
    }

    /** Controlled 2.6-block-wide travel; the animated vanilla model is visible. */
    @SubscribeEvent
    public static void animateSource(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.getChunkSource().hasChunk(VfxMuseumCatalog.ORIGIN_X>>4,
                    VfxMuseumCatalog.ORIGIN_Z>>4)
                || !level.getBlockState(VfxMuseumCatalog.MARKER).is(Blocks.LODESTONE)) return;
        AABB room = new AABB(VfxMuseumCatalog.ORIGIN_X, VfxMuseumCatalog.FLOOR_Y,
                VfxMuseumCatalog.ORIGIN_Z,
                VfxMuseumCatalog.ORIGIN_X + VfxMuseumCatalog.COLUMNS * VfxMuseumCatalog.CELL_X,
                VfxMuseumCatalog.FLOOR_Y + 10,
                VfxMuseumCatalog.ORIGIN_Z + VfxMuseumCatalog.rows() * VfxMuseumCatalog.CELL_Z);
        for (Husk model : level.getEntitiesOfClass(Husk.class, room,
                candidate -> candidate.getTags().contains("pyrovfx_museum"))) {
            for (String tag : model.getTags()) {
                if (!tag.startsWith("pyrovfx_museum_source_")) continue;
                var e=VfxMuseumCatalog.find(tag.substring("pyrovfx_museum_source_".length()));
                if (e==null) break;
                double t=level.getGameTime()*0.055;
                double dx=Math.sin(t)*1.25;
                model.setPos(e.base().getX()+6.5+dx,e.base().getY()+1,e.base().getZ()+5.5);
                model.setYRot((float)(Math.cos(t)>=0 ? -90 : 90));
                model.setYBodyRot(model.getYRot());
                model.setYHeadRot(model.getYRot()+(float)(Math.sin(t*2)*22));
                break;
            }
        }
    }

    public static String[] expectedLines(String description) {
        String[] lines = {"","","","","",""};
        int i=0;
        for (String word : description.split("\\s+")) {
            if (i>=lines.length) break;
            if (lines[i].length()+word.length()+1>15) i++;
            if (i>=lines.length) break;
            lines[i]+=(lines[i].isEmpty()?"":" ")+word;
        }
        return lines;
    }

    private static void place(ServerLevel level, BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(pos,state,3);
    }
}
