package net.kofllee.pyrovfx.museum;

import net.kofllee.pyrovfx.PyroVfx;
import net.minecraft.ChatFormatting;
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
                    player.teleportTo(VfxMuseumCatalog.ORIGIN_X + 7.5,
                            VfxMuseumCatalog.FLOOR_Y + 2,
                            VfxMuseumCatalog.ORIGIN_Z + 13.5);
                    return 1;
                })));
    }

    public static void build(ServerLevel level) {
        for (var exhibit : VfxMuseumCatalog.exhibits()) {
            buildExhibit(level, exhibit);
            BlockPos base = exhibit.base();
            // Wide, well-lit pedestrian bridges connect the 35 cells.
            if (exhibit.index() % VfxMuseumCatalog.COLUMNS < VfxMuseumCatalog.COLUMNS - 1
                    && exhibit.index() + 1 < VfxMuseumCatalog.exhibits().size()) {
                for (int dx = 14; dx <= 16; dx++)
                    for (int dz = 10; dz <= 14; dz++)
                        place(level, base.offset(dx, 0, dz), Blocks.SMOOTH_STONE.defaultBlockState());
            }
            if (exhibit.index() + VfxMuseumCatalog.COLUMNS < VfxMuseumCatalog.exhibits().size()) {
                for (int dx = 5; dx <= 10; dx++)
                    for (int dz = 16; dz <= 18; dz++)
                        place(level, base.offset(dx, 0, dz), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        // The marker is the only fixed-location museum identification.
        place(level, VfxMuseumCatalog.MARKER, Blocks.LODESTONE.defaultBlockState());
        place(level, VfxMuseumCatalog.MARKER.above(), Blocks.LANTERN.defaultBlockState());
        level.setDefaultSpawnPos(new BlockPos(VfxMuseumCatalog.ORIGIN_X + 7,
                VfxMuseumCatalog.FLOOR_Y + 2, VfxMuseumCatalog.ORIGIN_Z + 13), 180f);
    }

    private static void buildExhibit(ServerLevel level, VfxMuseumCatalog.Exhibit e) {
        BlockPos base = e.base();
        var palette = switch (e.category()) {
            case "Sprites" -> Blocks.LIGHT_BLUE_CONCRETE;
            case "3D Models" -> Blocks.ORANGE_CONCRETE;
            case "Transparency" -> Blocks.GRAY_CONCRETE;
            case "Lighting" -> Blocks.YELLOW_CONCRETE;
            case "Animations" -> Blocks.PINK_CONCRETE;
            case "Rotation" -> Blocks.PURPLE_CONCRETE;
            case "Motion" -> Blocks.CYAN_CONCRETE;
            case "Collisions" -> Blocks.RED_CONCRETE;
            case "Expressions" -> Blocks.LIME_CONCRETE;
            case "Curves" -> Blocks.GREEN_CONCRETE;
            case "Events" -> Blocks.MAGENTA_CONCRETE;
            default -> Blocks.WHITE_CONCRETE;
        };
        // Each exhibit is a genuinely traversable 13x15 platform with an
        // open flight volume; the palette is a quick physical category key.
        for (int dx = 1; dx <= 13; dx++) {
            for (int dz = 1; dz <= 15; dz++) {
                boolean edge = dx == 1 || dx == 13 || dz == 1 || dz == 15;
                place(level, base.offset(dx, 0, dz),
                        edge ? palette.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        // Large sight frame and backdrop to read volume/opacity against world.
        for (int dy = 1; dy <= 7; dy++) {
            for (int dz = 2; dz <= 9; dz++) {
                if (dy == 1 || dy == 7 || dz == 2 || dz == 9) {
                    place(level, base.offset(2, dy, dz), palette.defaultBlockState());
                }
            }
        }
        for (int x = 3; x <= 12; x++) {
            place(level, base.offset(x, 1, 3), Blocks.BLACK_CONCRETE.defaultBlockState());
        }
        place(level, base.offset(7, 1, 5), Blocks.SEA_LANTERN.defaultBlockState());
        // 3 powered, rising-edge buttons: local client picks up synced state.
        for (VfxMuseumCatalog.Action action : VfxMuseumCatalog.Action.values()) {
            BlockPos button = e.button(action);
            place(level, button.below(), Blocks.POLISHED_ANDESITE.defaultBlockState());
            place(level, button, Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
            putSign(level, button.offset(0, -1, 2), action.name(), "", "", "");
        }
        putSign(level, base.offset(7, 1, 14),
                e.category(), e.title(), e.id(), e.count() + " particles");
        putSign(level, base.offset(11, 1, 14),
                "PYRO VFX", "PLAY / STOP", "COMPARE A/B", "/pyrovfx museum");
        // Per-stand light for maximum visibility without global material changes.
        place(level, base.offset(3, 1, 10), Blocks.SEA_LANTERN.defaultBlockState());
        place(level, base.offset(11, 1, 10), Blocks.SEA_LANTERN.defaultBlockState());
    }

    private static void putSign(ServerLevel level, BlockPos pos,
                                String a, String b, String c, String d) {
        place(level, pos.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        place(level, pos, Blocks.OAK_SIGN.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            var text = sign.getFrontText();
            String[] lines = {a,b,c,d};
            for (int i = 0; i < 4; i++) {
                String shortLine = lines[i].length() > 15 ? lines[i].substring(0, 15) : lines[i];
                text = text.setMessage(i, Component.literal(shortLine).withStyle(ChatFormatting.DARK_BLUE));
            }
            sign.setText(text, true);
            sign.setChanged();
            level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
        }
    }

    private static void place(ServerLevel level, BlockPos pos,
                              net.minecraft.world.level.block.state.BlockState state) {
        level.setBlock(pos, state, 3);
    }
}
