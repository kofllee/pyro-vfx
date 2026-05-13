package net.kofllee.pyrovfx.client.dev;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kofllee.pyrovfx.vfx.VfxDefinition;
import net.kofllee.pyrovfx.vfx.resource.VfxRegistry;
import net.minecraft.ChatFormatting;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
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
                                        .executes(context -> playEffect(
                                                ResourceLocationArgument.getId(context, "effect")
                                        ))
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
                                                .executes(context -> playEffectAt(
                                                        ResourceLocationArgument.getId(context, "effect"),
                                                        Vec3Argument.getVec3(context, "pos")
                                                ))
                                        )
                                )
                        )
        );
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

    private static int playEffect(ResourceLocation effectIdText){
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if(player == null){
            return 0;
        }

        return playEffectAt(effectIdText, player.position().add(0, player.getEyeHeight(), 0));
    }

    private static int playEffectAt(ResourceLocation effect, Vec3 position){
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if(player == null){
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

        ClientVfxManager.play(definition, position);
        player.displayClientMessage(
                Component.literal("Playing Pyro VFX: " + effect),
                false
        );

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
