package net.kofllee.pyrovfx.client.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.kofllee.pyrovfx.client.vfx.VfxBudgetConfig;
import net.kofllee.pyrovfx.client.vfx.VfxPlayOptions;
import net.kofllee.pyrovfx.museum.VfxMuseumCatalog;
import net.kofllee.pyrovfx.vfx.resource.VfxRegistry;
import net.kofllee.pyrovfx.client.render.VfxGpuTelemetry;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

/**
 * Client-local controls; physical buttons work in singleplayer and on servers
 * which have the same Pyro VFX client. No client->server packets or command
 * blocks are required to replay purely client-rendered effects.
 */
@EventBusSubscriber(modid = PyroVfx.MOD_ID, value = Dist.CLIENT)
public final class VfxMuseumClient {
    private static final String[] FLAGS = {
            "pyrovfx.analytic.forceCpu", "pyrovfx.render.forceLegacy",
            "pyrovfx.model.forceLegacy", "pyrovfx.cpu.forceLegacy"
    };
    private static final Map<String, String> ORIGINAL_FLAGS = new HashMap<>();
    private static final Map<net.minecraft.core.BlockPos, Boolean> PREVIOUS_BUTTONS = new HashMap<>();
    public enum Mode { AUTO, CPU, LEGACY }
    private static Mode mode = Mode.AUTO;
    private static String lastExhibit = "";
    private static boolean lastCompareWasCpu;
    private static int pendingInspectionTicks;
    private static net.minecraft.client.multiplayer.ClientLevel observedLevel;
    private static boolean museumAutoStarted;

    private VfxMuseumClient() {}

    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        var root = literal("museum");
        root.then(literal("list").executes(ctx -> list()));
        root.then(literal("stop").executes(ctx -> stop()));
        root.then(literal("status").executes(ctx -> status()));
        root.then(literal("budgets").executes(ctx -> budgets()));
        root.then(literal("mode")
                .then(literal("auto").executes(ctx -> changeMode(Mode.AUTO)))
                .then(literal("cpu").executes(ctx -> changeMode(Mode.CPU)))
                .then(literal("legacy").executes(ctx -> changeMode(Mode.LEGACY))));
        root.then(literal("play").then(argument("id", StringArgumentType.word())
                .suggests((ctx, b) -> suggest(b))
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
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level != observedLevel) {
            PREVIOUS_BUTTONS.clear();
            observedLevel = level;
            museumAutoStarted = false;
            restoreFlags();
            lastExhibit = "";
        }
        // Explicit runClientMuseum startup only. Never build/teleport when a
        // normal Minecraft client joins a world or server.
        if (!museumAutoStarted && Boolean.getBoolean("pyrovfx.museum.autoStart")
                && mc.getSingleplayerServer() != null && mc.player != null) {
            museumAutoStarted = true;
            var server = mc.getSingleplayerServer();
            var id = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player == null) return;
                net.kofllee.pyrovfx.museum.VfxMuseumWorld.build(player.serverLevel());
                player.serverLevel().setDayTime(6000);
                player.teleportTo(VfxMuseumCatalog.ORIGIN_X + 7.5,
                        VfxMuseumCatalog.FLOOR_Y + 2.0,
                        VfxMuseumCatalog.ORIGIN_Z + 13.5);
            });
        }
        if (pendingInspectionTicks > 0 && --pendingInspectionTicks == 0) status();
        if (level == null || mc.player == null
                || !VfxMuseumCatalog.inside(mc.player.getBlockX(),
                    mc.player.getBlockY(), mc.player.getBlockZ())) {
            PREVIOUS_BUTTONS.clear();
            return;
        }
        // Never force-load distant chunks or scan the full world. Only look
        // at stands around the visitor and detect *rising* powered states.
        for (var exhibit : VfxMuseumCatalog.exhibits()) {
            if (exhibit.base().distSqr(mc.player.blockPosition()) > 40 * 40) continue;
            for (var action : VfxMuseumCatalog.Action.values()) {
                var pos = exhibit.button(action);
                var state = level.getBlockState(pos);
                boolean powered = state.hasProperty(BlockStateProperties.POWERED)
                        && state.getValue(BlockStateProperties.POWERED);
                boolean previous = PREVIOUS_BUTTONS.getOrDefault(pos, false);
                if (powered && !previous) {
                    switch (action) {
                        case PLAY -> play(exhibit.id(), -1);
                        case STOP -> stop();
                        case COMPARE -> compare(exhibit.id());
                    }
                }
                PREVIOUS_BUTTONS.put(pos, powered);
            }
        }
    }

    private static int list() {
        for (var e : VfxMuseumCatalog.exhibits())
            say(e.id() + " [" + e.category() + "] " + e.title() + " - " + e.description());
        say("Buttons: PLAY / STOP / COMPARE. Commands: /pyrovfx museum play <id> [count]");
        return VfxMuseumCatalog.exhibits().size();
    }
    private static int info(String id) {
        var e = VfxMuseumCatalog.find(id);
        if (e == null) { say("Unknown exhibit: " + id); return 0; }
        say(e.title() + " [" + e.category() + "]: " + e.description());
        say("Effect " + e.effect() + ", " + e.count() + " demo particles, x" + e.visualScale()
                + " demo-only scale; mode " + mode);
        status();
        return 1;
    }
    private static int play(String id, int requestedCount) {
        var e = VfxMuseumCatalog.find(id);
        var mc = Minecraft.getInstance();
        if (e == null || mc.level == null || mc.player == null) {
            say("Unknown exhibit or missing client world: " + id);
            return 0;
        }
        var definition = VfxRegistry.get(e.effect());
        if (definition == null) { say("Missing museum effect resource: " + e.effect()); return 0; }
        int count = requestedCount > 0 ? requestedCount : e.count();
        // Museum is a controlled isolated test. Stop any earlier Pyro effect
        // to avoid invalid A/B comparisons (does not affect vanilla particles).
        ClientVfxManager.clear();
        var opts = VfxPlayOptions.builder().param("count", count).build();
        long seed = 0x50B0_2026L ^ e.id().hashCode();
        ClientVfxManager.playSeeded(definition,
                new net.minecraft.world.phys.Vec3(
                    e.effectOrigin().getX() + 0.5,
                    e.effectOrigin().getY() + 0.5,
                    e.effectOrigin().getZ() + 0.5),
                opts, seed);
        lastExhibit = id;
        say("Museum: " + e.title() + " [" + mode + "] x" + count + " seed=" + seed);
        say("EXPECTED: " + e.description());
        pendingInspectionTicks = 4; // Read backend after the first simulation tick.
        return 1;
    }
    private static int compare(String id) {
        lastCompareWasCpu = !lastCompareWasCpu;
        changeMode(lastCompareWasCpu ? Mode.CPU : Mode.AUTO);
        return play(id, -1);
    }
    private static int changeMode(Mode next) {
        if (ORIGINAL_FLAGS.isEmpty()) {
            for (String flag : FLAGS) ORIGINAL_FLAGS.put(flag, System.getProperty(flag));
        }
        mode = next;
        // Change both simulation AND renderer so the comparison isolates
        // actual end-to-end CPU vs GPU costs. Legacy is explicit opt-in.
        set(FLAGS[0], next != Mode.AUTO);
        set(FLAGS[1], next != Mode.AUTO);
        set(FLAGS[2], next != Mode.AUTO);
        set(FLAGS[3], next == Mode.LEGACY);
        say("Museum mode: " + mode + " (replay to reselect simulation backend)");
        return 1;
    }
    private static void set(String key, boolean value) {
        if (value) System.setProperty(key,"true");
        else System.clearProperty(key);
    }
    private static void restoreFlags() {
        if (ORIGINAL_FLAGS.isEmpty()) return;
        for (var entry : ORIGINAL_FLAGS.entrySet()) {
            if (entry.getValue() == null) System.clearProperty(entry.getKey());
            else System.setProperty(entry.getKey(), entry.getValue());
        }
        ORIGINAL_FLAGS.clear();
        mode = Mode.AUTO;
    }
    private static int stop() {
        ClientVfxManager.clear();
        say("Museum: stopped (" + lastExhibit + ")");
        return 1;
    }
    private static int status() {
        say("Museum [" + mode + "] active=" + ClientVfxManager.getActiveParticleCount()
                + " particles, GPU VFX "
                + String.format(Locale.ROOT, "%.3f ms", VfxGpuTelemetry.latestSampleNs()/1e6)
                + ", CPU EMA "
                + String.format(Locale.ROOT, "%.3f ms", ClientVfxManager.adaptiveSpawns().cpuEmaMs()));
        for (var instance : ClientVfxManager.instances()) {
            for (var emitter : instance.emitters()) {
                say(emitter.definition().id() + ": " + emitter.selectedBackend()
                        + " / " + emitter.selectedRenderer() + ", " + emitter.backendReason()
                        + ", state " + emitter.allocatedParticleStateBytes() + " B");
            }
        }
        return 1;
    }
    private static int budgets() {
        var global = ClientVfxManager.globalBudget();
        say("ACTIVE (TOML): global=" + VfxBudgetConfig.globalParticles()
                + " effect=" + VfxBudgetConfig.effectParticles()
                + " spawnGlobal/t=" + VfxBudgetConfig.globalSpawns()
                + " spawnEffect/t=" + VfxBudgetConfig.effectSpawns()
                + " maxEffects=" + VfxBudgetConfig.activeEffects());
        say("ACTIVE class ceilings: gpu=" + VfxBudgetConfig.classLimit(
                net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.GPU_ANALYTIC)
                + " cpu=" + VfxBudgetConfig.classLimit(
                net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_BASIC)
                + " collision=" + VfxBudgetConfig.classLimit(
                net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_COLLISION)
                + " models=" + VfxBudgetConfig.classLimit(
                net.kofllee.pyrovfx.client.vfx.VfxParticleCostClass.CPU_MODEL));
        say("Adaptive=" + VfxBudgetConfig.adaptiveSpawns()
                + ", spawnScale=" + Math.round(ClientVfxManager.adaptiveSpawns().spawnScale()*100) + "%"
                + ", budget remaining active=" + global.remainingActive());
        return 1;
    }
    private static void say(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(message), false);
    }
}
