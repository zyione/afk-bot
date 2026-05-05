package com.aibot;

/**
 * Simple three-state machine for the bot.
 * No movement involved in any state.
 */
public enum BotState {
    /** No weapon available, waiting for /ai stop */
    IDLE,
    /** Holding a weapon, swinging at cooldown pace */
    ATTACKING,
    /** Depositing old item and/or fetching new item from chest */
    SWAPPING
}
