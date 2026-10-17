package com.gtl.enhancedcore.network;

import com.gtl.enhancedcore.common.config.ConfigCatalog;
import com.gtl.enhancedcore.common.config.ServerConfigEditor;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public final class ConfigFilePackets {
    private ConfigFilePackets() {}
    public record Request(int id, ConfigCatalog kind, boolean save, String expected, String text) {
        public static void encode(Request p, FriendlyByteBuf b) {
            b.writeVarInt(p.id); b.writeEnum(p.kind); b.writeBoolean(p.save); b.writeUtf(p.expected, 64); b.writeUtf(p.text, 8_000);
        }
        public static Request decode(FriendlyByteBuf b) {
            return new Request(b.readVarInt(), b.readEnum(ConfigCatalog.class), b.readBoolean(), b.readUtf(64), b.readUtf(8_000));
        }
        public static void handle(Request p, Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> ServerConfigEditor.handle(ctx.getSender(), p)); ctx.setPacketHandled(true);
        }
    }
    public record Response(int id, String text, String error, boolean saved) {
        public static void encode(Response p, FriendlyByteBuf b) {
            b.writeVarInt(p.id); b.writeUtf(p.text, 8_000); b.writeUtf(p.error, 2048); b.writeBoolean(p.saved);
        }
        public static Response decode(FriendlyByteBuf b) {
            return new Response(b.readVarInt(), b.readUtf(8_000), b.readUtf(2048), b.readBoolean());
        }
        public static void handle(Response p, Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.gtl.enhancedcore.client.config.RemoteConfigScreen.receive(p)));
            ctx.setPacketHandled(true);
        }
    }
}
