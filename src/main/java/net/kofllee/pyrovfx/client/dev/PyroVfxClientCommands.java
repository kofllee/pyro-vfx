package net.kofllee.pyrovfx.client.dev;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kofllee.pyrovfx.client.vfx.VfxPlayOptions;
import net.kofllee.pyrovfx.vfx.definition.VfxDefinition;
import net.kofllee.pyrovfx.vfx.resource.VfxRegistry;
import net.minecraft.ChatFormatting;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(
        modid = PyroVfx.MOD_ID,
        value = Dist.CLIENT
)
public final class PyroVfxClientCommands {
    private PyroVfxClientCommands(){}

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                literal("pyrovfx")
                        .then(literal("play")
                                .then(argument("effect", ResourceLocationArgument.id())
                                        .suggests(PyroVfxClientCommands::suggestEffects)
                                        .executes(context -> playEffectWithParameters(
                                                ResourceLocationArgument.getId(context, "effect"),
                                                new CompoundTag()
                                        ))
                                        .then(argument("parameters", CompoundTagArgument.compoundTag())
                                                .executes(context -> playEffectWithParameters(
                                                        ResourceLocationArgument.getId(context, "effect"),
                                                        CompoundTagArgument.getCompoundTag(context, "parameters")
                                                ))
                                        )
                                )
                        )
                        .then(literal("list")
                                .executes(context -> listEffects())
                        )
                        .then(literal("stop_all")
                                .executes(context -> stopAll())
                        )
                        .then(literal("play_at")
                                .then(argument("effect", ResourceLocationArgument.id())
                                        .suggests(PyroVfxClientCommands::suggestEffects)
                                        .then(argument("pos", Vec3Argument.vec3())
                                                .executes(context -> playEffectAtWithParameters(
                                                        ResourceLocationArgument.getId(context, "effect"),
                                                        Vec3Argument.getVec3(context, "pos"),
                                                        new CompoundTag()
                                                ))
                                                .then(argument("parameters", CompoundTagArgument.compoundTag())
                                                        .executes(context -> playEffectAtWithParameters(
                                                                ResourceLocationArgument.getId(context, "effect"),
                                                                Vec3Argument.getVec3(context, "pos"),
                                                                CompoundTagArgument.getCompoundTag(context, "parameters")
                                                        ))
                                                )
                                        )
                                )
                        )
        );
    }

    private static int playEffectAtWithParameters(
            ResourceLocation effect,
            Vec3 position,
            CompoundTag parametersTag
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null) {
            return 0;
        }

        VfxDefinition definition = VfxRegistry.get(effect);
        if (definition == null) {
            player.displayClientMessage(
                    Component.literal("No Pyro VFX found with id: " + effect).withStyle(ChatFormatting.RED),
                    false
            );
            return 0;
        }

        VfxPlayOptions options = parsePlayOptions(definition, parametersTag, player);

        ClientVfxManager.play(definition, position, options);

        player.displayClientMessage(
                Component.literal("Playing Pyro VFX: " + effect + " with " + options.parameters().size() + " parameter(s)"),
                false
        );

        return 1;
    }

    private static VfxPlayOptions parsePlayOptions(
            VfxDefinition definition,
            CompoundTag tag,
            Player player
    ) {
        VfxPlayOptions.Builder builder = VfxPlayOptions.builder();

        for (String key : tag.getAllKeys()) {
            if (!definition.parameters().containsKey(key)) {
                player.displayClientMessage(
                        Component.literal("Unknown Pyro VFX parameter '" + key + "' for effect " + definition.id())
                                .withStyle(ChatFormatting.YELLOW),
                        false
                );
                continue;
            }

            Tag valueTag = tag.get(key);

            if (!(valueTag instanceof NumericTag numericTag)) {
                player.displayClientMessage(
                        Component.literal("Pyro VFX parameter '" + key + "' must be numeric")
                                .withStyle(ChatFormatting.RED),
                        false
                );
                continue;
            }

            builder.param(key, numericTag.getAsDouble());
        }

        return builder.build();
    }

    private static int playEffectWithParameters(ResourceLocation effect, CompoundTag parametersTag) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if (player == null) {
            return 0;
        }

        return playEffectAtWithParameters(
                effect,
                player.position().add(0, player.getEyeHeight(), 0),
                parametersTag
        );
    }

    private static CompletableFuture<Suggestions> suggestParameters(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        ResourceLocation effect;

        try {
            effect = ResourceLocationArgument.getId(context, "effect");
        } catch (IllegalArgumentException exception) {
            return builder.buildFuture();
        }

        VfxDefinition definition = VfxRegistry.get(effect);
        if (definition == null) {
            return builder.buildFuture();
        }

        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (String parameter : definition.parameters().keySet()) {
            if (parameter.toLowerCase(Locale.ROOT).startsWith(input)) {
                builder.suggest(parameter);
            }
        }

        return builder.buildFuture();
    }

    private static int stopAll() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        ClientVfxManager.clear();

        if(player != null){
            player.displayClientMessage(
                    Component.literal("Stopped all Pyro VFX"),
                    false
            );
        }

        return 1;
    }

    private static int listEffects() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if(player == null){
            return 0;
        }

        if(VfxRegistry.ids().isEmpty()){
            player.displayClientMessage(
                    Component.literal("No Pyro VFX registered"),
                    false
            );
            return 1;
        }

        player.displayClientMessage(
                Component.literal("Pyro VFX registered:"),
                false
        );

        for(ResourceLocation id : VfxRegistry.ids()){
            player.displayClientMessage(
                    Component.literal("- " + id.toString()),
                    false
            );
        }

        return 1;
    }

    private static CompletableFuture<Suggestions> suggestEffects(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (ResourceLocation id : VfxRegistry.ids()) {
            if (id.toString().toLowerCase(Locale.ROOT).startsWith(input)) {
                builder.suggest(id.toString());
            }
        }

        return builder.buildFuture();
    }
}
