package com.aibot;

import com.aibot.command.AiCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI Bot Mod — Server-side only Fabric mod for Minecraft 1.21.11
 *
 * Spawns a stationary fake-player bot that swings a sword in the direction
 * the player was looking when the command was executed.
 */
public class AiBotMod implements ModInitializer {

    public static final String MOD_ID = "aibot";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Register /ai commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                AiCommand.register(dispatcher)
        );

        // Auto-despawn bot when owner disconnects
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                BotManager.onOwnerDisconnect(handler.player)
        );

        // Tick all bots — clean up any externally removed bots
        ServerTickEvents.END_SERVER_TICK.register(server ->
                BotManager.tickAllBots(server)
        );

        LOGGER.info("[AI Bot] Mod loaded successfully! Use /ai left_click to spawn a bot.");
    }
}
