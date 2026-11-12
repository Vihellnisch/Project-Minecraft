package com.slop.projectminecraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.slop.projectminecraft.arsenal.ClawServer;
import com.slop.projectminecraft.arsenal.GladiusServer;
import com.slop.projectminecraft.arsenal.GloveServer;
import com.slop.projectminecraft.registry.ModItems;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /projectminecraft cooldowns [players] (operators): everything of the mod's ready again, for the players given (or
 * yourself): the gloves' weapons, the Handmade Claw's serums, the gladius's skills and its E.G.O skills, and the item
 * cooldowns (the E.G.O weapons, the book, the invitation).
 */
public final class ProjectMinecraftCommand {
    private ProjectMinecraftCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("projectminecraft").requires(s -> s.hasPermission(2))
                .then(Commands.literal("cooldowns")
                        .executes(c -> reset(c.getSource(), List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> reset(c.getSource(), EntityArgument.getPlayers(c, "players"))))));
    }

    static int reset(CommandSourceStack src, Collection<ServerPlayer> players) {
        for (ServerPlayer p : players) {
            GloveServer.resetCooldowns(p);
            ClawServer.resetCooldowns(p);
            GladiusServer.resetCooldowns(p);
            for (var item : ModItems.ITEMS.getEntries()) p.getCooldowns().removeCooldown(item.get());
        }
        int n = players.size();
        src.sendSuccess(() -> n == 1 ? Component.translatable("commands.projectminecraft.cooldowns.one", players.iterator().next().getDisplayName())
                : Component.translatable("commands.projectminecraft.cooldowns.many", n), true);
        return n;
    }
}
