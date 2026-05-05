package com.aibot.command;

import com.aibot.BotManager;
import com.aibot.BotPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Registers all /ai subcommands:
 *   /ai left_click  — spawn a bot at the player's position and facing direction
 *   /ai stop        — despawn the player's bot
 *   /ai status      — print bot status info
 */
public class AiCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("ai")
                        .then(CommandManager.literal("left_click")
                                .executes(AiCommand::executeLeftClick))
                        .then(CommandManager.literal("stop")
                                .executes(AiCommand::executeStop))
                        .then(CommandManager.literal("status")
                                .executes(AiCommand::executeStatus))
        );
    }

    // ─── /ai left_click ────────────────────────────────────────────────

    private static int executeLeftClick(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        ServerWorld world = player.getServerWorld();

        // Spawn the bot — BotManager handles despawning any existing bot
        BotManager.spawnBot(player, world);

        source.sendFeedback(
                () -> Text.literal("[AI Bot] ").formatted(Formatting.GOLD)
                        .append(Text.literal("Bot spawned! It will swing at whatever is in front of it.").formatted(Formatting.GREEN)),
                false
        );
        return 1;
    }

    // ─── /ai stop ──────────────────────────────────────────────────────

    private static int executeStop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        BotPlayer bot = BotManager.getBot(player);
        if (bot == null) {
            source.sendFeedback(
                    () -> Text.literal("[AI Bot] ").formatted(Formatting.GOLD)
                            .append(Text.literal("You don't have an active bot.").formatted(Formatting.RED)),
                    false
            );
            return 0;
        }

        BotManager.despawnBot(player);

        source.sendFeedback(
                () -> Text.literal("[AI Bot] ").formatted(Formatting.GOLD)
                        .append(Text.literal("Bot despawned.").formatted(Formatting.YELLOW)),
                false
        );
        return 1;
    }

    // ─── /ai status ────────────────────────────────────────────────────

    private static int executeStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        BotPlayer bot = BotManager.getBot(player);
        if (bot == null) {
            source.sendFeedback(
                    () -> Text.literal("[AI Bot] ").formatted(Formatting.GOLD)
                            .append(Text.literal("You don't have an active bot.").formatted(Formatting.RED)),
                    false
            );
            return 0;
        }

        // Build status message
        StringBuilder sb = new StringBuilder();
        sb.append("§6[AI Bot] Status Report\n");

        // State
        sb.append("§7State: §f").append(bot.getBotState().name()).append("\n");

        // Held item and durability
        ItemStack held = bot.getMainHandStack();
        if (held.isEmpty()) {
            sb.append("§7Held Item: §cNone\n");
        } else {
            float durability = bot.getDurabilityPercent(held);
            Formatting durColor = durability > 0.5f ? Formatting.GREEN :
                    durability > 0.2f ? Formatting.YELLOW : Formatting.RED;
            sb.append("§7Held Item: §f").append(held.getName().getString())
                    .append(" §7(").append(String.format("%.0f%%", durability * 100)).append(")\n");
        }

        // Locked facing
        sb.append("§7Facing: §fYaw=").append(String.format("%.1f", bot.getLockedYaw()))
                .append(", Pitch=").append(String.format("%.1f", bot.getLockedPitch())).append("\n");

        // Chest position
        if (bot.getChestPos() != null) {
            sb.append("§7Chest: §f(").append(bot.getChestPos().getX())
                    .append(", ").append(bot.getChestPos().getY())
                    .append(", ").append(bot.getChestPos().getZ()).append(")\n");
        } else {
            sb.append("§7Chest: §cNot found\n");
        }

        // Barrel position
        if (bot.getBarrelPos() != null) {
            sb.append("§7Barrel: §f(").append(bot.getBarrelPos().getX())
                    .append(", ").append(bot.getBarrelPos().getY())
                    .append(", ").append(bot.getBarrelPos().getZ()).append(")");
        } else {
            sb.append("§7Barrel: §cNot found");
        }

        source.sendFeedback(
                () -> Text.literal(sb.toString()),
                false
        );
        return 1;
    }
}
