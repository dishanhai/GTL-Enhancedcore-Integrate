package com.gtl.enhancedcore.client.ae2;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.integration.ae2.remote.RemoteMenuLease;
import com.gtl.enhancedcore.network.RemoteProviderPackets;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A single non-ticking client menu host, never installed in a real chunk. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class RemoteProviderHosts {
    private static Host pending;
    private RemoteProviderHosts() {}

    public static void accept(RemoteProviderPackets.Snapshot packet) {
        clear();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.player.containerMenu.containerId != packet.sourceMenu()
                || !level.dimension().location().equals(packet.dimension())) return;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.tag()));
        try {
            var state = Block.stateById(packet.stateId());
            if (!(state.getBlock() instanceof EntityBlock block)) return;
            BlockEntity entity = block.newBlockEntity(packet.pos(), state);
            if (entity == null) return;
            entity.setLevel(level);
            var tag = buffer.readNbt();
            if (tag == null || buffer.isReadable()) return;
            if (entity instanceof com.lowdragmc.lowdraglib.syncdata.blockentity.IManagedBlockEntity managed)
                managed.getRootStorage().init();
            entity.handleUpdateTag(tag);
            pending = new Host(packet.nonce(), level, packet.pos(), entity, mc.player.containerMenu,
                    System.nanoTime() + 5_000_000_000L);
        } catch (RuntimeException failure) {
            GTLEnhancedcore.LOGGER.warn("Unable to initialize remote provider menu host {}", packet.pos(), failure);
        } finally {
            buffer.release();
        }
    }

    public static BlockEntity find(Level level, BlockPos pos) {
        BlockEntity real = level.getBlockEntity(pos);
        if (real != null) return real;
        Host host = active();
        return host != null && host.level == level && host.pos.equals(pos) && claim(host) ? host.entity : null;
    }

    public static IPart findPart(BlockGetter level, BlockPos pos, Direction side) {
        IPart real = PartHelper.getPart(level, pos, side);
        if (real != null) return real;
        Host host = active();
        if (host == null || host.level != level || !host.pos.equals(pos) || !claim(host)) return null;
        return host.entity instanceof IPartHost partHost ? partHost.getPart(side) : null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        Host host = pending;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != host.level || mc.player == null) { clear(); return; }
        if (!host.lease.observe(mc.player.containerMenu, mc.player.inventoryMenu, System.nanoTime())) clear();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (pending != null && pending.level == event.getLevel()) clear();
    }

    private static void clear() {
        // This detached object was never onLoad/onReady/ticked; no unload callback should mutate world state.
        pending = null;
    }

    private static Host active() {
        Host host = pending;
        Minecraft mc = Minecraft.getInstance();
        if (host != null && (mc.player == null || mc.level != host.level)) {
            clear();
            return null;
        }
        return host;
    }

    private static boolean claim(Host host) {
        var player = Minecraft.getInstance().player;
        return player != null && host.lease.claim(player.containerMenu, player.inventoryMenu, System.nanoTime());
    }

    private static final class Host {
        final java.util.UUID nonce;
        final ClientLevel level;
        final BlockPos pos;
        final BlockEntity entity;
        final RemoteMenuLease<AbstractContainerMenu> lease;
        Host(java.util.UUID nonce, ClientLevel level, BlockPos pos, BlockEntity entity,
             AbstractContainerMenu origin, long expires) {
            this.nonce = nonce; this.level = level; this.pos = pos; this.entity = entity;
            this.lease = new RemoteMenuLease<>(origin, expires);
        }
    }
}
