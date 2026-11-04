package net.kofllee.pyrovfx.client.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.VfxTransformProviders;
import net.kofllee.pyrovfx.vfx.transform.VfxTransform;
import net.kofllee.pyrovfx.vfx.transform.VfxTransformProvider;
import net.kofllee.pyrovfx.client.vfx.VfxPlayOptions;
import net.kofllee.pyrovfx.museum.VfxMuseumCatalog;
import net.kofllee.pyrovfx.vfx.resource.VfxRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Quaternionf;

import java.util.*;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/** In-world technical lab: physical controls, proximity activation and local TextDisplay updates. */
@EventBusSubscriber(modid = PyroVfx.MOD_ID, value = Dist.CLIENT)
public final class VfxMuseumClient {
    private static final String[] FLAGS = {
            "pyrovfx.analytic.forceCpu", "pyrovfx.render.forceLegacy",
            "pyrovfx.model.forceLegacy", "pyrovfx.cpu.forceLegacy"
    };
    private static final Map<String, String> ORIGINAL_FLAGS = new HashMap<>();
    private static final Map<BlockPos, Boolean> PREVIOUS_BUTTONS = new HashMap<>();
    private static final Map<String, Long> HANDLES = new HashMap<>();
    private static final Map<String, Long> NEXT_REPLAY = new HashMap<>();
    private static final Map<String, String> DISPLAYED = new HashMap<>();
    private static final Set<String> PAUSED = new HashSet<>();
    private static final int MAX_ACTIVE = 5;
    private static final int ACTIVATION_RADIUS = 27;
    public enum Mode { AUTO, CPU, LEGACY }
    private static Mode mode = Mode.AUTO;
    private static ClientLevel observedLevel;
    private static boolean museumAutoStarted, visiting, lastCompareWasCpu;
    private static String selected = "", solo = "", lastExhibit = "";

    private VfxMuseumClient() {}

    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        var root = literal("museum");
        root.then(literal("list").executes(ctx -> list()));
        root.then(literal("stop").executes(ctx -> stop()));
        root.then(literal("status").executes(ctx -> status()));
        root.then(literal("mode")
                .then(literal("auto").executes(ctx -> changeMode(Mode.AUTO)))
                .then(literal("cpu").executes(ctx -> changeMode(Mode.CPU)))
                .then(literal("legacy").executes(ctx -> changeMode(Mode.LEGACY))));
        root.then(literal("play").then(argument("id", StringArgumentType.word())
                .suggests((ctx,b) -> suggest(b))
                .executes(ctx -> play(StringArgumentType.getString(ctx,"id"), -1))
                .then(argument("count", IntegerArgumentType.integer(1, 100_000))
                        .executes(ctx -> play(StringArgumentType.getString(ctx,"id"),
                                IntegerArgumentType.getInteger(ctx, "count"))))));
        root.then(literal("info").then(argument("id", StringArgumentType.word())
                .suggests((ctx,b) -> suggest(b))
                .executes(ctx -> info(StringArgumentType.getString(ctx,"id")))));
        root.then(literal("compare").then(argument("id", StringArgumentType.word())
                .suggests((ctx,b) -> suggest(b))
                .executes(ctx -> compare(StringArgumentType.getString(ctx,"id")))));
        return root;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        String input = b.getRemaining().toLowerCase(Locale.ROOT);
        for (var e : VfxMuseumCatalog.exhibits())
            if (e.id().startsWith(input)) b.suggest(e.id());
        return b.buildFuture();
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != observedLevel) {
            leave();
            observedLevel = level;
            museumAutoStarted = false;
        }
        if (!museumAutoStarted && Boolean.getBoolean("pyrovfx.museum.autoStart")
                && mc.getSingleplayerServer() != null && mc.player != null) {
            museumAutoStarted = true;
            var server = mc.getSingleplayerServer();
            var playerId = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null) return;
                net.kofllee.pyrovfx.museum.VfxMuseumWorld.build(player.serverLevel());
                player.serverLevel().setDayTime(6000);
                player.teleportTo(VfxMuseumCatalog.ORIGIN_X + 6.5,
                        VfxMuseumCatalog.FLOOR_Y + 2.0,
                        VfxMuseumCatalog.ORIGIN_Z + 10.5);
            });
        }
        boolean inside = level != null && mc.player != null &&
                VfxMuseumCatalog.inside(mc.player.getBlockX(),mc.player.getBlockY(),mc.player.getBlockZ()) &&
                level.getBlockState(VfxMuseumCatalog.MARKER).is(Blocks.LODESTONE);
        if (!inside) {
            if (visiting) leave();
            return;
        }
        visiting = true;
        for (var exhibit : VfxMuseumCatalog.exhibits()) {
            if (exhibit.base().distSqr(mc.player.blockPosition()) > 38*38) continue;
            for (var action : VfxMuseumCatalog.Action.values()) {
                var position=exhibit.button(action);
                var state=level.getBlockState(position);
                boolean powered=state.hasProperty(BlockStateProperties.POWERED)
                        && state.getValue(BlockStateProperties.POWERED);
                boolean previous=PREVIOUS_BUTTONS.getOrDefault(position,false);
                if (powered && !previous) button(exhibit,action);
                PREVIOUS_BUTTONS.put(position,powered);
            }
        }
        autoPlayback(mc);
        updatePanels(mc);
    }

    private static void button(VfxMuseumCatalog.Exhibit e,VfxMuseumCatalog.Action action) {
        selected=e.id();
        switch(action) {
            case PLAY -> {
                PAUSED.remove(e.id());
                stopHandle(e.id());
                NEXT_REPLAY.remove(e.id());
            }
            case STOP -> {
                PAUSED.add(e.id());
                stopHandle(e.id());
            }
            case COMPARE -> {
                solo = solo.equals(e.id()) ? "" : e.id();
                lastCompareWasCpu=!lastCompareWasCpu;
                changeMode(solo.isEmpty() ? Mode.AUTO : (lastCompareWasCpu ? Mode.CPU : Mode.AUTO));
                stopManaged();
            }
            case INFO -> { /* Panel selection only, no chat output. */ }
        }
    }

    /** Nearby stands automatically run; at most five, to avoid accumulating 35 demos at once. */
    private static void autoPlayback(Minecraft mc) {
        var candidates=new ArrayList<VfxMuseumCatalog.Exhibit>();
        for (var e:VfxMuseumCatalog.exhibits()) {
            if (!solo.isEmpty() && !solo.equals(e.id())) continue;
            if (PAUSED.contains(e.id())) continue;
            double distance=e.effectOrigin().distSqr(mc.player.blockPosition());
            if (distance <= ACTIVATION_RADIUS*ACTIVATION_RADIUS) candidates.add(e);
        }
        candidates.sort(Comparator.comparingDouble(e -> e.effectOrigin().distSqr(mc.player.blockPosition())));
        if (candidates.size()>MAX_ACTIVE) candidates.subList(MAX_ACTIVE,candidates.size()).clear();
        Set<String> desired=new HashSet<>();
        for (var e:candidates) desired.add(e.id());
        for (String id:new ArrayList<>(HANDLES.keySet())) if (!desired.contains(id)) stopHandle(id);
        long tick=mc.level.getGameTime();
        for (var e:candidates) {
            long handle=HANDLES.getOrDefault(e.id(),0L);
            if (handle!=0 && ClientVfxManager.handleActive(handle)) continue;
            HANDLES.remove(e.id());
            if (tick < NEXT_REPLAY.getOrDefault(e.id(),0L)) continue;
            start(e,e.count());
            NEXT_REPLAY.put(e.id(),tick+95);
        }
    }

    private static void start(VfxMuseumCatalog.Exhibit e,int count) {
        var mc=Minecraft.getInstance();
        if (mc.level==null || VfxRegistry.get(e.effect())==null) return;
        var origin=Vec3.atCenterOf(e.effectOrigin());
        var source=providerFor(e.id(),origin,e.base().offset(6,1,5));
        if (source==null) return;
        long handle=ClientVfxManager.playManaged(e.effect(),source,Map.of("count",(double)count));
        if (handle!=0) HANDLES.put(e.id(),handle);
        lastExhibit=e.id();
    }

    private static void stopHandle(String id) {
        Long old=HANDLES.remove(id);
        if (old!=null) ClientVfxManager.stopHandle(old);
    }
    private static void stopManaged() {
        for (String id:new ArrayList<>(HANDLES.keySet())) stopHandle(id);
    }
    private static void leave() {
        stopManaged();
        restoreFlags();
        PREVIOUS_BUTTONS.clear();
        DISPLAYED.clear();
        NEXT_REPLAY.clear();
        PAUSED.clear();
        selected="";solo="";lastExhibit="";visiting=false;
    }

    /** Update only when the actual label changes. TextDisplay is never used as a button. */
    private static void updatePanels(Minecraft mc) {
        for (var e:VfxMuseumCatalog.exhibits()) {
            if (e.base().distSqr(mc.player.blockPosition()) > 38*38) continue;
            Long handle=HANDLES.get(e.id());
            boolean active=handle!=null && ClientVfxManager.handleActive(handle);
            String backend=active ? ClientVfxManager.backendForHandle(handle) : "";
            String state=PAUSED.contains(e.id()) ? "PAUSED" : active ? "ACTIVE" : "READY";
            if (solo.equals(e.id())) state="SOLO "+state;
            String modeLine=state+"  |  "+mode+(backend.isEmpty()?"":"  |  "+backend);
            showText(mc.level,e,"status",modeLine);
            String details=selected.equals(e.id())
                    ? e.id()+"\n"+e.description()
                    : "REPLAY     PAUSE     SOLO     INFO";
            showText(mc.level,e,"details",details);
        }
    }
    private static void showText(ClientLevel level,VfxMuseumCatalog.Exhibit e,String kind,String value) {
        String key=e.id()+":"+kind;
        if (value.equals(DISPLAYED.get(key))) return;
        AABB area=new AABB(e.base()).inflate(12);
        var entities=level.getEntitiesOfClass(Display.TextDisplay.class,area,
                display -> display.getTags().contains("pyrovfx_museum_"+e.id()+"_"+kind));
        if (entities.isEmpty()) return;
        for (var display:entities) net.kofllee.pyrovfx.museum.VfxMuseumText.update(display,value);
        DISPLAYED.put(key,value);
    }

    /** All entity sources are visibly instantiated in the museum, not attached to an invisible player. */
    static VfxTransformProvider providerFor(String id,Vec3 origin,BlockPos blockAnchor) {
        var mc=Minecraft.getInstance();
        if (mc.level==null) return null;
        if (id.equals("source_block")) return VfxTransformProviders.block(mc.level,blockAnchor);
        if (id.equals("source_block_entity")) {
            var entity=mc.level.getBlockEntity(blockAnchor);
            return entity==null ? null : VfxTransformProviders.blockEntity(entity);
        }
        if (id.startsWith("source_") && !id.equals("source_rotation")) {
            Husk model=findSource(mc.level,id,origin);
            if (model==null) return null;
            if (id.equals("source_model_part")) {
                var paths=net.kofllee.pyrovfx.client.vfx.VfxSockets.vanillaParts(model,1);
                String socket=paths.contains("head") ? "head" :
                    paths.stream().filter(path -> path.endsWith("/head")).findFirst().orElse("head");
                return VfxTransformProviders.modelPart(model,(entity,partial) ->
                    net.kofllee.pyrovfx.client.vfx.VfxSockets.resolve(entity,socket,partial));
            }
            if (id.equals("source_custom") || id.equals("source_distance")) {
                return partial -> {
                    if (model.isRemoved() || mc.level!=model.level()) return null;
                    float t=Mth.clamp(partial,0,1);
                    Vec3 pos=new Vec3(Mth.lerp(t,model.xo,model.getX()),
                        Mth.lerp(t,model.yo,model.getY()),Mth.lerp(t,model.zo,model.getZ()));
                    return new VfxTransform(pos,new Quaternionf().rotationY(
                        (float)Math.toRadians(-Mth.rotLerp(t,model.yRotO,model.getYRot()))));
                };
            }
            return VfxTransformProviders.entity(model);
        }
        return VfxTransformProviders.fixed(origin);
    }
    private static Husk findSource(ClientLevel level,String id,Vec3 origin) {
        var nearby=level.getEntitiesOfClass(Husk.class,
                new AABB(origin,origin).inflate(5.0),
                zombie -> zombie.getTags().contains("pyrovfx_museum_source_"+id));
        return nearby.isEmpty()?null:nearby.getFirst();
    }

    /** Retain the benchmark fixture's explicit clock-domain behavior. */
    static VfxTransformProvider benchmarkProviderFor(String id,Vec3 origin,BlockPos blockAnchor) {
        var level=Minecraft.getInstance().level;
        long start=level.getGameTime();
        if (id.equals("source_rotation") || id.equals("source_model_local"))
            return partial -> new VfxTransform(origin,
                new Quaternionf().rotationY((level.getGameTime()-start+partial)*0.11f));
        if (id.equals("source_custom") || id.equals("source_distance"))
            return partial -> {
                double time=(level.getGameTime()-start+partial)*0.07;
                return new VfxTransform(origin.add(Math.sin(time)*3,0,0),
                    new Quaternionf().rotationY((float)time));
            };
        if (id.equals("source_entity_world") || id.equals("source_entity_local") ||
                id.equals("source_model_part")) return VfxTransformProviders.entity(Minecraft.getInstance().player);
        return providerFor(id,origin,blockAnchor);
    }

    private static int play(String id,int requestedCount) {
        var e=VfxMuseumCatalog.find(id);
        if (e==null) return 0;
        PAUSED.remove(id);
        selected=id;solo=id;
        stopManaged();
        NEXT_REPLAY.remove(id);
        start(e,requestedCount>0?requestedCount:e.count());
        return HANDLES.containsKey(id)?1:0;
    }
    private static int info(String id) {
        if (VfxMuseumCatalog.find(id)==null) return 0;
        selected=id;
        say(VfxMuseumCatalog.find(id).description());
        return 1;
    }
    private static int compare(String id) {
        var e=VfxMuseumCatalog.find(id);
        if (e==null) return 0;
        button(e,VfxMuseumCatalog.Action.COMPARE);
        return 1;
    }
    private static int list() {
        for (var e:VfxMuseumCatalog.exhibits()) say(e.id()+" ["+e.category()+"]");
        return VfxMuseumCatalog.exhibits().size();
    }
    private static int stop() {
        for(var e:VfxMuseumCatalog.exhibits()) PAUSED.add(e.id());
        stopManaged();
        return 1;
    }
    private static int status() {
        say("Museum: "+HANDLES.size()+" nearby effects, "+mode+
                (solo.isEmpty()?"":" selected="+solo));
        return 1;
    }
    private static int changeMode(Mode next) {
        if (ORIGINAL_FLAGS.isEmpty())
            for (String flag:FLAGS) ORIGINAL_FLAGS.put(flag,System.getProperty(flag));
        mode=next;
        set(FLAGS[0],next!=Mode.AUTO);
        set(FLAGS[1],next!=Mode.AUTO);
        set(FLAGS[2],next!=Mode.AUTO);
        set(FLAGS[3],next==Mode.LEGACY);
        return 1;
    }
    private static void set(String flag,boolean enabled) {
        if (enabled) System.setProperty(flag,"true");
        else System.clearProperty(flag);
    }
    private static void restoreFlags() {
        for (var entry:ORIGINAL_FLAGS.entrySet()) {
            if (entry.getValue()==null) System.clearProperty(entry.getKey());
            else System.setProperty(entry.getKey(),entry.getValue());
        }
        ORIGINAL_FLAGS.clear();mode=Mode.AUTO;
    }
    private static void say(String message) {
        var player=Minecraft.getInstance().player;
        if (player!=null) player.displayClientMessage(Component.literal(message),false);
    }
}
