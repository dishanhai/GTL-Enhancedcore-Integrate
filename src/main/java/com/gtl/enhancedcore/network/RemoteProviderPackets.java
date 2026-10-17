package com.gtl.enhancedcore.network;

import com.gtl.enhancedcore.GTLEnhancedcore;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** One bounded visual/initial-sync snapshot; never sends inventories from saveWithFullMetadata. */
public final class RemoteProviderPackets {
    public static final int MAX_TAG_BYTES = 1024 * 1024;
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLEnhancedcore.MOD_ID, "remote_provider"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static boolean initialized;
    private RemoteProviderPackets() {}

    public static synchronized void register() {
        if (initialized) return;
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        initialized = true;
    }

    public static boolean send(ServerPlayer player, BlockEntity host) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(256, MAX_TAG_BYTES));
        try {
            buffer.writeNbt(host.getUpdateTag());
            if (buffer.readableBytes() > MAX_TAG_BYTES) return false;
            byte[] tag = new byte[buffer.readableBytes()];
            buffer.readBytes(tag);
            var snapshot = new Snapshot(UUID.randomUUID(), player.containerMenu.containerId,
                    player.serverLevel().dimension().location(), host.getBlockPos(), Block.getId(host.getBlockState()), tag);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
            return true;
        } catch (IndexOutOfBoundsException oversized) {
            // Refuse the target rather than allowing a giant managed payload to grow the network buffer.
            GTLEnhancedcore.LOGGER.warn("Remote provider snapshot exceeds {} bytes at {}", MAX_TAG_BYTES, host.getBlockPos());
            return false;
        } catch (RuntimeException failure) {
            GTLEnhancedcore.LOGGER.warn("Unable to send remote provider snapshot at {}", host.getBlockPos(), failure);
            return false;
        } finally {
            buffer.release();
        }
    }

    public record Snapshot(UUID nonce, int sourceMenu, ResourceLocation dimension, BlockPos pos,
                           int stateId, byte[] tag) {
        static void encode(Snapshot p, FriendlyByteBuf b) {
            b.writeUUID(p.nonce); b.writeVarInt(p.sourceMenu); b.writeResourceLocation(p.dimension);
            b.writeBlockPos(p.pos); b.writeVarInt(p.stateId); b.writeByteArray(p.tag);
        }
        static Snapshot decode(FriendlyByteBuf b) {
            return new Snapshot(b.readUUID(), b.readVarInt(), b.readResourceLocation(), b.readBlockPos(),
                    b.readVarInt(), b.readByteArray(MAX_TAG_BYTES));
        }
        static void handle(Snapshot p, Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.gtl.enhancedcore.client.ae2.RemoteProviderHosts.accept(p)));
            ctx.setPacketHandled(true);
        }
    }
}
