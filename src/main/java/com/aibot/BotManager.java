package com.aibot;

import com.mojang.authlib.GameProfile;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player registry for bot instances.
 * Handles spawn/despawn lifecycle, chunk ticket management, and cleanup.
 */
public class BotManager {

    private static final Map<UUID, BotPlayer> activeBots = new HashMap<>();

    /**
     * Spawns a new bot for the given player.
     * If the player already has a bot, it is despawned first.
     */
    public static void spawnBot(ServerPlayerEntity owner, ServerWorld world) {
        // Clean up any existing bot first
        despawnBot(owner);

        // Create fake game profile
        UUID botUuid = UUID.nameUUIDFromBytes(
                ("bot_" + owner.getUuidAsString()).getBytes()
        );
        String botName = owner.getName().getString() + "[BOT]";
        // Trim to 16 chars max (Minecraft player name limit)
        if (botName.length() > 16) {
            botName = botName.substring(0, 16);
        }
        GameProfile profile = new GameProfile(botUuid, botName);

        // Capture player state at command execution time
        float yaw = owner.getYaw();
        float pitch = owner.getPitch();

        // Create the bot entity
        BotPlayer bot = new BotPlayer(
                world.getServer(), world, profile, owner,
                yaw, pitch
        );
        bot.setPosition(owner.getPos());

        // The bot needs a network handler set to avoid NPEs.
        // We connect the bot through the player manager which sets up everything properly.
        // Instead, we'll set the network handler manually via our accessor mixin.
        // For a fake player, we need to be careful here.
        // We'll spawn it as an entity in the world directly.

        // Add to world - using the player manager's approach for adding to the world
        world.spawnEntity(bot);

        // Force-load chunks at spawn position
        ChunkPos chunkPos = new ChunkPos(bot.getBlockPos());
        world.getChunkManager().addTicket(
                ChunkTicketType.FORCED, chunkPos, 2, chunkPos
        );

        activeBots.put(owner.getUuid(), bot);

        // Notify the owner
        bot.sendOwnerMessage(
                "Bot spawned at your position! Facing: yaw=" +
                        String.format("%.1f", yaw) + ", pitch=" + String.format("%.1f", pitch),
                Formatting.GREEN
        );
    }

    /**
     * Despawns the calling player's bot.
     * Returns held item to chest if possible, otherwise drops it.
     */
    public static void despawnBot(ServerPlayerEntity owner) {
        BotPlayer bot = activeBots.remove(owner.getUuid());
        if (bot == null) return;

        // Remove chunk ticket
        ServerWorld world = (ServerWorld) bot.getWorld();
        ChunkPos chunkPos = new ChunkPos(bot.getBlockPos());
        world.getChunkManager().removeTicket(
                ChunkTicketType.FORCED, chunkPos, 2, chunkPos
        );

        // Try to return held item to chest before despawning
        ItemStack held = bot.getMainHandStack();
        if (!held.isEmpty()) {
            if (bot.getChestPos() != null) {
                bot.returnItemToChest(held.copy());
            } else {
                bot.dropItem(held, false);
            }
        }

        bot.discard();

        bot.sendOwnerMessage("Bot despawned.", Formatting.YELLOW);
    }

    /**
     * Gets the active bot for a player, or null if none exists.
     */
    public static BotPlayer getBot(ServerPlayerEntity owner) {
        return activeBots.get(owner.getUuid());
    }

    /**
     * Called when a player disconnects — auto-despawn their bot.
     */
    public static void onOwnerDisconnect(ServerPlayerEntity owner) {
        despawnBot(owner);
    }

    /**
     * Called every server tick to clean up bots that were killed externally.
     */
    public static void tickAllBots(MinecraftServer server) {
        activeBots.entrySet().removeIf(entry -> {
            BotPlayer bot = entry.getValue();
            if (bot.isRemoved()) {
                // Clean up chunk ticket for externally removed bots
                ServerWorld world = (ServerWorld) bot.getWorld();
                ChunkPos chunkPos = new ChunkPos(bot.getBlockPos());
                world.getChunkManager().removeTicket(
                        ChunkTicketType.FORCED, chunkPos, 2, chunkPos
                );
                return true;
            }
            return false;
        });
    }
}
