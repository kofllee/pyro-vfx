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
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Quaternionf;

import java.util.*;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/** In-world technical lab: physical controls, persistent playback and local TextDisplay updates. */
@EventBusSubscriber(modid = PyroVfx.MOD_ID, value = Dist.CLIENT)
public final class VfxMuseumClient {
    private static final String[] FLAGS = {
            "pyrovfx.analytic.forceCpu", "pyrovfx.render.forceLegacy",
            "pyrovfx.model.forceLegacy", "pyrovfx.cpu.forceLegacy"
    };
    private static final Map<String, String> ORIGINAL_FLAGS = new HashMap<>();
    private static final Map<String, Long> HANDLES = new HashMap<>();
    private static final Map<String, Long> NEXT_RETRY_TICK = new HashMap<>();
    private static final Map<Integer, Long> TRANSPARENCY_HANDLES = new HashMap<>();
    private static final Set<String> PREVIEW_EFFECTS = Set.of(
            "analytic_moving_light", "analytic_random_life", "analytic_random_uv",
            "analytic_model_surface", "analytic_velocity", "analytic_world_lit",
            "model_shared_color");
    private static long previewHandle;
    private static final Map<String, String> DISPLAYED = new HashMap<>();
    private static final Set<String> ENABLED = new HashSet<>();
    private static final Map<BlockPos, Boolean> POWERED_BUTTONS = new HashMap<>();
    private static final Map<BlockPos, Long> LAST_PRESS = new HashMap<>();

    public enum Mode { AUTO, CPU, LEGACY }
    private static Mode mode = Mode.AUTO;
    private static ClientLevel observedLevel;
    private static boolean museumAutoStarted, visiting, initializedEnabled;
    private static String selected = "", solo = "", lastExhibit = "";

    private VfxMuseumClient() {}

    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        var root = literal("museum");
        root.then(literal("list").executes(ctx -> list()));
        root.then(literal("stop").executes(ctx -> stop()));
        root.then(literal("status").executes(ctx -> status()));
        root.then(literal("preview")
                .then(literal("stop").executes(ctx -> {
                    stopPreview();
                    return 1;
                }))
                .then(argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (var id : PREVIEW_EFFECTS)
                                if (id.startsWith(builder.getRemaining())) builder.suggest(id);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> preview(StringArgumentType.getString(ctx, "id")))));
        root.then(literal("all")
                .then(literal("on").executes(ctx -> global(VfxMuseumCatalog.GlobalAction.ALL_ON)))
                .then(literal("off").executes(ctx -> global(VfxMuseumCatalog.GlobalAction.ALL_OFF))));
        root.then(literal("solo").then(argument("id", StringArgumentType.word())
                .suggests((ctx,b) -> suggest(b))
                .executes(ctx -> solo(StringArgumentType.getString(ctx,"id")))));
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
            stopPreview();
            stopTransparency();
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
                if (Boolean.getBoolean("pyrovfx.museum.runtimeShowcase")) {
                    // Showcase is isolated to the dedicated developer save.
                    // Creative mode allows inspecting a submerged effect
                    // indefinitely without drowning or accidental block loss.
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    net.kofllee.pyrovfx.museum.VfxMuseumWorld.teleportTransparency(player, 4);
                } else {
                    player.teleportTo(VfxMuseumCatalog.ORIGIN_X + 6.5,
                            VfxMuseumCatalog.FLOOR_Y + 2.0,
                            VfxMuseumCatalog.ORIGIN_Z + 4.5);
                }
            });
        }
        if (Boolean.getBoolean("pyrovfx.museum.runtimeShowcase")
                && level != null && mc.player != null)
            updateTransparency(level);
        boolean inside = level != null && mc.player != null
                && VfxMuseumCatalog.inside(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())
                && level.getBlockState(VfxMuseumCatalog.MARKER).is(Blocks.LODESTONE);
        if (!inside) {
            if (visiting) leave();
            return;
        }
        visiting = true;
        if (!initializedEnabled) {
            for (var exhibit : VfxMuseumCatalog.exhibits()) ENABLED.add(exhibit.id());
            initializedEnabled = true;
        }
        scanPressedButtons(mc);
        autoPlayback(mc);
        updatePanels(mc);
    }

    private static void updateTransparency(ClientLevel level) {
        var fixture = ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/alpha_water_visibility");
        if (VfxRegistry.get(fixture) == null) return;
        int x = net.kofllee.pyrovfx.museum.VfxMuseumWorld.TRANSPARENCY_X;
        int z = net.kofllee.pyrovfx.museum.VfxMuseumWorld.TRANSPARENCY_Z;
        if (!level.getBlockState(new BlockPos(x, VfxMuseumCatalog.FLOOR_Y, z))
                .is(Blocks.YELLOW_CONCRETE)) return;
        for (int lane = 0; lane < 6; lane++) {
            // The tinted-glass lane needs one broad sprite; the standard
            // water-visibility fixture has only two small particles.
            var laneFixture = lane == 5
                    ? ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/alpha_tinted_split")
                    : fixture;
            if (VfxRegistry.get(laneFixture) == null) continue;
            long old = TRANSPARENCY_HANDLES.getOrDefault(lane, 0L);
            if (old != 0 && ClientVfxManager.handleActive(old)) continue;
            long handle = ClientVfxManager.playManaged(laneFixture,
                    VfxTransformProviders.fixed(
                            net.kofllee.pyrovfx.museum.VfxMuseumWorld.transparencyOrigin(lane)),
                    Map.of("count", 1.0));
            if (handle != 0) TRANSPARENCY_HANDLES.put(lane, handle);
        }
    }

    private static void stopTransparency() {
        for (long handle : TRANSPARENCY_HANDLES.values()) ClientVfxManager.stopHandle(handle);
        TRANSPARENCY_HANDLES.clear();
    }

    private static int preview(String id) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !PREVIEW_EFFECTS.contains(id)) return 0;
        var effect = ResourceLocation.fromNamespaceAndPath("pyro_vfx", "bench/" + id);
        if (VfxRegistry.get(effect) == null) {
            say("Preview not loaded: " + effect);
            return 0;
        }
        stopPreview();
        var origin = mc.player.getEyePosition().add(mc.player.getLookAngle().scale(8.0));
        previewHandle = ClientVfxManager.playManaged(effect,
                VfxTransformProviders.fixed(origin), Map.of("count", 120.0));
        if (previewHandle != 0) {
            say("Preview: " + id + " | " + ClientVfxManager.backendForHandle(previewHandle)
                    + " | /pyrovfx museum preview stop");
            return 1;
        }
        return 0;
    }

    private static void stopPreview() {
        if (previewHandle != 0) ClientVfxManager.stopHandle(previewHandle);
        previewHandle = 0;
    }


    /** Client click and synchronized redstone pulse are deduplicated by position/tick. */
    @SubscribeEvent
    public static void onButtonClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() && visiting && observedLevel == event.getLevel())
            press(event.getPos());
    }

    private static void scanPressedButtons(Minecraft mc) {
        for (var action : VfxMuseumCatalog.GlobalAction.values())
            scan(mc.level, VfxMuseumCatalog.globalButton(action));
        for (var e : VfxMuseumCatalog.exhibits()) {
            if (e.base().distSqr(mc.player.blockPosition()) > 20 * 20) continue;
            for (var action : VfxMuseumCatalog.Action.values()) scan(mc.level, e.button(action));
        }
    }

    private static void scan(ClientLevel level, BlockPos pos) {
        var state=level.getBlockState(pos);
        boolean powered=state.is(Blocks.STONE_BUTTON)
                && state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED);
        boolean old=POWERED_BUTTONS.getOrDefault(pos,false);
        if (powered && !old) press(pos);
        POWERED_BUTTONS.put(pos,powered);
    }

    private static void press(BlockPos pos) {
        var mc=Minecraft.getInstance();
        if (!visiting || mc.level == null || !mc.level.getBlockState(pos).is(Blocks.STONE_BUTTON)) return;
        long now=mc.level.getGameTime();
        if (now-LAST_PRESS.getOrDefault(pos,-100L) < 8L) return;
        for (var action : VfxMuseumCatalog.GlobalAction.values()) {
            if (VfxMuseumCatalog.globalButton(action).equals(pos)) {
                LAST_PRESS.put(pos,now);
                global(action);
                return;
            }
        }
        for (var exhibit : VfxMuseumCatalog.exhibits()) {
            if (exhibit.base().distSqr(pos) > 14 * 14) continue;
            for (var action : VfxMuseumCatalog.Action.values()) {
                if (exhibit.button(action).equals(pos)) {
                    LAST_PRESS.put(pos,now);
                    button(exhibit,action);
                    return;
                }
            }
        }
    }

    private static int global(VfxMuseumCatalog.GlobalAction action) {
        solo="";
        if (action == VfxMuseumCatalog.GlobalAction.ALL_ON) {
            for (var e : VfxMuseumCatalog.exhibits()) ENABLED.add(e.id());
            announce("ALL ON: all " + VfxMuseumCatalog.exhibits().size() + " exhibits running");
        } else {
            ENABLED.clear();
            stopManaged();
            announce("ALL OFF: every exhibit stopped");
        }
        return 1;
    }

    /** Exact same exhibit configuration as the interactive ALL ON/OFF
     * buttons, but without UI side effects during controlled client A/B runs. */
    static void benchmarkAllEnabled(boolean enabled) {
        initializedEnabled = true;
        solo = "";
        ENABLED.clear();
        if (enabled) {
            for (var exhibit : VfxMuseumCatalog.exhibits()) ENABLED.add(exhibit.id());
        } else {
            stopManaged();
        }
    }

    private static void button(VfxMuseumCatalog.Exhibit e,VfxMuseumCatalog.Action action) {
        selected=e.id();
        switch(action) {
            case TOGGLE -> {
                boolean wasActive=solo.equals(e.id()) || ENABLED.contains(e.id());
                solo="";
                if (wasActive) {
                    ENABLED.remove(e.id());
                    stopHandle(e.id());
                } else {
                    ENABLED.add(e.id());
                }
                announce(e.title()+": "+(wasActive ? "OFF" : "ON"));
            }
            case SOLO -> solo(e.id());
            case INFO -> info(e.id());
        }
    }

    private static int solo(String id) {
        var e=VfxMuseumCatalog.find(id);
        if (e == null) return 0;
        if (solo.equals(id)) {
            solo="";
            announce("SOLO OFF: previous exhibit selection restored");
        } else {
            solo=id;
            stopManaged();
            announce("SOLO: "+e.title()+" (press again to restore all)");
        }
        return 1;
    }

    /** All enabled exhibits play simultaneously, independent of player distance. */
    private static void autoPlayback(Minecraft mc) {
        var candidates=new ArrayList<VfxMuseumCatalog.Exhibit>();
        for (var e:VfxMuseumCatalog.exhibits()) {
            if (!solo.isEmpty()) {
                if (solo.equals(e.id())) candidates.add(e);
            } else if (ENABLED.contains(e.id())) {
                candidates.add(e);
            }
        }
        Set<String> desired=new HashSet<>();
        for (var e:candidates) desired.add(e.id());
        for (String id:new ArrayList<>(HANDLES.keySet()))
            if (!desired.contains(id)) stopHandle(id);
        for (var e:candidates) {
            long handle=HANDLES.getOrDefault(e.id(),0L);
            if (handle!=0 && ClientVfxManager.handleActive(handle)) continue;
            if (mc.level.getGameTime() < NEXT_RETRY_TICK.getOrDefault(e.id(), 0L))
                continue;
            HANDLES.remove(e.id());
            // The first tick after a finite effect finishes starts a new cycle.
            start(e,e.count());
            if (!HANDLES.containsKey(e.id()))
                NEXT_RETRY_TICK.put(e.id(), mc.level.getGameTime() + 20);
            else NEXT_RETRY_TICK.remove(e.id());
        }
    }

    private static void start(VfxMuseumCatalog.Exhibit e,int count) {
        var mc=Minecraft.getInstance();
        if (mc.level==null || VfxRegistry.get(e.effect())==null) return;
        var origin=Vec3.atCenterOf(e.effectOrigin());
        var source=providerFor(e.id(),origin,e.base().offset(6,1,5));
        // A freshly built stand may not yet be present in the client chunk.
        // Do not submit a provider known to be unavailable (or spam warnings).
        if (source==null || source.get(1f)==null) return;
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
        NEXT_RETRY_TICK.clear();
        restoreFlags();
        DISPLAYED.clear();
        POWERED_BUTTONS.clear();
        LAST_PRESS.clear();
        ENABLED.clear();
        initializedEnabled=false;
        selected="";solo="";lastExhibit="";visiting=false;
    }

    /** INFO goes to chat. The wall shows live playback state. */
    private static void updatePanels(Minecraft mc) {
        for (var e:VfxMuseumCatalog.exhibits()) {
            Long handle=HANDLES.get(e.id());
            boolean active=handle!=null && ClientVfxManager.handleActive(handle);
            boolean enabled=solo.equals(e.id()) || (solo.isEmpty() && ENABLED.contains(e.id()));
            String backend=active ? ClientVfxManager.backendForHandle(handle) : "";
            String state=!enabled ? "OFF" : active ? "RUNNING" : "ON / STANDBY";
            if (solo.equals(e.id())) state="SOLO / "+state;
            // Stand-specific live count separates a missing VFX source from a
            // renderer visibility issue, without introducing a render limit.
            if ((e.id().startsWith("source_") || e.id().startsWith("runtime_")) && active)
                state += " | " + ClientVfxManager.particleCountForHandle(handle) + " particles";
            showText(mc.level,e,"status",state+
                    (backend.isBlank() ? "" : "  |  "+backend));
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
        // Server-side scoreboard tags are not reliable client spawn metadata.
        // Prefer the synchronized custom name; old museum worlds fall back to
        // the sole Husk inside their stand (cells are 12 x 13 blocks apart).
        var nearby=level.getEntitiesOfClass(Husk.class,
                new AABB(origin,origin).inflate(4.5),
                husk -> !husk.isRemoved());
        if (nearby.isEmpty()) return null;
        for (var husk : nearby) {
            var name=husk.getCustomName();
            if (name!=null && name.getString().contains("[" + id + "]")) return husk;
            if (husk.getTags().contains("pyrovfx_museum_source_"+id)) return husk;
        }
        return nearby.stream().min(java.util.Comparator.comparingDouble(
                husk -> husk.position().distanceToSqr(origin))).orElse(null);
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
        ENABLED.add(id);
        selected=id;solo=id;
        stopManaged();
        start(e,requestedCount>0?requestedCount:e.count());
        return HANDLES.containsKey(id)?1:0;
    }
    private static int info(String id) {
        if (VfxMuseumCatalog.find(id)==null) return 0;
        var e=VfxMuseumCatalog.find(id);
        selected=id;
        say("[" + e.category() + "] " + e.title() + "  (" + e.id() + ")");
        say(e.description());
        if (!VfxMuseumCatalog.legend(id).isBlank())
            say("Compare: " + VfxMuseumCatalog.legend(id));
        if (!VfxMuseumCatalog.formulas(id).isBlank()) {
            say("Actual expressions:");
            for (String expression : VfxMuseumCatalog.formulas(id).split("\\n"))
                say(expression);
        }
        Long handle=HANDLES.get(id);
        say("Effect: "+e.effect()+" | count: "+e.count()+
                " | backend: "+(handle != null && ClientVfxManager.handleActive(handle)
                        ? ClientVfxManager.backendForHandle(handle) : "not running"));
        if (id.startsWith("source_")) {
            if (handle == null || !ClientVfxManager.handleActive(handle)) {
                say("Attachment: not running. Verify that the source entity/block is loaded.");
            } else {
                var source=ClientVfxManager.sourcePositionForHandle(handle);
                say("Attachment particles: "+ClientVfxManager.particleCountForHandle(handle)
                        +" | world source: "+(source == null ? "lost" : String.format(java.util.Locale.ROOT,
                        "(%.2f, %.2f, %.2f)",source.x,source.y,source.z)));
            }
        }
        if (id.equals("gpu_sprite") && handle != null && ClientVfxManager.handleActive(handle)) {
            say("LEFT actual: "+ClientVfxManager.backendForEmitter(handle,"gpu_resident"));
            say("RIGHT actual: "+ClientVfxManager.backendForEmitter(handle,"cpu_dense"));
        }
        return 1;
    }
    private static int compare(String id) {
        var e=VfxMuseumCatalog.find(id);
        if (e==null) return 0;
        return solo(id);
    }
    private static int list() {
        for (var e:VfxMuseumCatalog.exhibits()) say(e.id()+" ["+e.category()+"]");
        return VfxMuseumCatalog.exhibits().size();
    }
    private static int stop() {
        return global(VfxMuseumCatalog.GlobalAction.ALL_OFF);
    }
    private static int status() {
        say("Museum: "+HANDLES.size()+" running / "+ENABLED.size()+" enabled / "+
                VfxMuseumCatalog.exhibits().size()+" exhibits, mode="+mode+
                (solo.isEmpty()?"":" solo="+solo));
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
    private static void announce(String message) {
        var player=Minecraft.getInstance().player;
        if (player!=null) player.displayClientMessage(Component.literal(message),true);
    }
    private static void say(String message) {
        var player=Minecraft.getInstance().player;
        if (player!=null) player.displayClientMessage(Component.literal(message),false);
    }
}
