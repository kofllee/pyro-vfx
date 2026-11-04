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
                }))
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
        removeServiceEntities(level);
        clearOldGallery(level);
        for (var exhibit : VfxMuseumCatalog.exhibits()) {
            buildExhibit(level, exhibit);
        }
        place(level, VfxMuseumCatalog.MARKER, Blocks.LODESTONE.defaultBlockState());
        place(level, VfxMuseumCatalog.MARKER.above(), Blocks.SEA_LANTERN.defaultBlockState());
        level.setDefaultSpawnPos(new BlockPos(VfxMuseumCatalog.ORIGIN_X + 6,
                VfxMuseumCatalog.FLOOR_Y + 2, VfxMuseumCatalog.ORIGIN_Z + 11), 180f);
    }

    private static void removeServiceEntities(ServerLevel level) {
        AABB region = new AABB(VfxMuseumCatalog.ORIGIN_X - 4, VfxMuseumCatalog.FLOOR_Y - 4,
                VfxMuseumCatalog.ORIGIN_Z - 4,
                VfxMuseumCatalog.ORIGIN_X + 90, VfxMuseumCatalog.FLOOR_Y + 18,
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
        for (VfxMuseumCatalog.Action action : VfxMuseumCatalog.Action.values()) {
            var button=e.button(action);
            place(level,button.below(),Blocks.POLISHED_ANDESITE.defaultBlockState());
            place(level,button,Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR));
        }
        spawnText(level,e,"title",e.title() + "\\n" + e.category(),
                6.5,7.0,10.68,0.54f);
        spawnText(level,e,"status","AUTO  |  LOADING",6.5,5.0,10.68,0.45f);
        spawnText(level,e,"details","REPLAY     PAUSE     SOLO     INFO",6.5,3.45,10.68,0.43f);
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
                model.setPersistenceRequired();
                model.addTag("pyrovfx_museum");
                model.addTag("pyrovfx_museum_source_"+e.id());
                level.addFreshEntity(model);
            }
        }
    }

    /** Real TextDisplay entities backed by solid black concrete, never cropped signs. */
    private static void spawnText(ServerLevel level,VfxMuseumCatalog.Exhibit e,
                                  String kind,String text,double x,double y,double z,float scale) {
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY,level);
        display.setPos(e.base().getX()+x,e.base().getY()+y,e.base().getZ()+z);
        VfxMuseumText.configure(display,text.replace("\\n","\n"),scale);
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
