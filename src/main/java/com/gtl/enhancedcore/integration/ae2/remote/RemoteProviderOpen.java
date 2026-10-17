package com.gtl.enhancedcore.integration.ae2.remote;

import com.gtl.enhancedcore.network.RemoteProviderPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Called only after Plus has resolved a provider from the player's current crafting grid. */
public final class RemoteProviderOpen {
    private RemoteProviderOpen() {}

    public static boolean prepare(BlockEntity host, ServerPlayer player) {
        if (host == null || host.isRemoved() || !RemoteProviderRange.allows(
                host.getLevel() == player.serverLevel(),
                player.distanceToSqr(Vec3.atCenterOf(host.getBlockPos())))) return false;
        var pos = host.getBlockPos();
        var chunk = player.serverLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(pos) != host) return false;
        return RemoteProviderPackets.send(player, host);
    }
}
