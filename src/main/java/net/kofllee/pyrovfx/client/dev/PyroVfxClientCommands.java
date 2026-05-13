package net.kofllee.pyrovfx.client.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.kofllee.pyrovfx.vfx.VfxDefinition;
import net.kofllee.pyrovfx.vfx.resource.VfxRegistry;
import net.minecraft.ChatFormatting;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyrovfx.client.vfx.ClientVfxManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

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
                                .then(argument("effect", StringArgumentType.greedyString())
                                        .suggests(((context, builder) -> {
                                            String input = builder.getRemaining().toLowerCase();

                                            for(ResourceLocation id : VfxRegistry.ids()){
                                                if(id.toString().toLowerCase().startsWith(input)){
                                                    builder.suggest(id.toString());
                                                }
                                            }
                                            return builder.buildFuture();
                                        }
                                        ))
                                        .executes(context -> playEffect(
                                                StringArgumentType.getString(context, "effect")
                                        ))
                                )
                        )
                        .then(literal("list")
                                .executes(context -> listEffects())
                        )
                        .then(literal("stop_all")
                                .executes(context -> stopAll())
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

    private static int playEffect(String effectIdText){
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        if(player == null){
            return 0;
        }

        ResourceLocation effectId = parseEffectId(effectIdText);
        if (effectId == null) {
            player.displayClientMessage(
                    Component.literal("Invalid Pyro VFX id: " + effectIdText).withStyle(ChatFormatting.RED),
                    false
            );
            return 0;
        }

        VfxDefinition definition = VfxRegistry.get(effectId);
        if (definition == null) {
            player.displayClientMessage(
                    Component.literal("No Pyro VFX found with id: " + effectId).withStyle(ChatFormatting.RED),
                    false
            );
            return 0;
        }

        ClientVfxManager.play(definition, player.position());
        player.displayClientMessage(
                Component.literal("Playing Pyro VFX: " + effectId),
                false
        );

        return 1;
    }

    private static ResourceLocation parseEffectId(String text) {
        if (!text.contains(":")) {
            text = PyroVfx.MOD_ID + ":" + text;
        }

        return ResourceLocation.tryParse(text);
    }
}
