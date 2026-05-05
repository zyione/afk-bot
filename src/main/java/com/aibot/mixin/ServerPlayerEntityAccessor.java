package com.aibot.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to set the networkHandler field on ServerPlayerEntity.
 * Required for fake players that have no real network connection.
 */
@Mixin(ServerPlayerEntity.class)
public interface ServerPlayerEntityAccessor {

    @Accessor("networkHandler")
    @Mutable
    void setNetworkHandler(ServerPlayNetworkHandler handler);
}
