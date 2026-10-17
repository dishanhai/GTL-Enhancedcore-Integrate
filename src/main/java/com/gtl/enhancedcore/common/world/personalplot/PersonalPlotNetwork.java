package com.gtl.enhancedcore.common.world.personalplot;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Separate, strictly directed channel. Settings are bounded and validated again on the server thread. */
public final class PersonalPlotNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GTLEnhancedcore.MOD_ID, "personal_plot"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean initialized;
    public enum Action { OPEN, CREATE, TEMPLATE, TRAVEL, RETURN }
    public record Snapshot(boolean dedicated, boolean canEditTemplate, boolean canCreatePersonal,
                           boolean canCustomizePersonal, boolean templateReady, boolean templatePending,
                           PlotSettings templateSettings, PlotSettings activeTemplateSettings,
                           PersonalPlotWorlds.State state, PlotSettings settings,
                           boolean inOwnWorld, boolean canReturn, String notice) {}
    private record Request(Action action, String settings) {}
    private record Environment(boolean dedicated) {}
    private record DimensionAdded(ResourceLocation dimension) {}
    private PersonalPlotNetwork() {}
    public static synchronized void register() {
        if (initialized) return;
        CHANNEL.registerMessage(0, Request.class, (p, b) -> { b.writeEnum(p.action); b.writeUtf(p.settings, PlotPresetShare.MAX_TEXT_LENGTH); },
                b -> new Request(b.readEnum(Action.class), b.readUtf(PlotPresetShare.MAX_TEXT_LENGTH)), PersonalPlotNetwork::request,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, PersonalPlotNetwork::encode, PersonalPlotNetwork::decode, (packet, supplier) -> {
            NetworkEvent.Context ctx = supplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.gtl.enhancedcore.common.item.PlotControllerEnvironment.accept(packet.dedicated);
                com.gtl.enhancedcore.client.plotinfo.PersonalPlotWorldScreen.receiveSnapshot(packet);
            })); ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Environment.class, (p, b) -> b.writeBoolean(p.dedicated), b -> new Environment(b.readBoolean()), (packet, supplier) -> {
            NetworkEvent.Context ctx = supplier.get(); ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.gtl.enhancedcore.common.item.PlotControllerEnvironment.accept(packet.dedicated))); ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, DimensionAdded.class, (p, b) -> b.writeResourceLocation(p.dimension), b -> new DimensionAdded(b.readResourceLocation()), (packet, supplier) -> {
            NetworkEvent.Context ctx = supplier.get(); ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
                if (connection != null) connection.levels().add(ResourceKey.create(Registries.DIMENSION, packet.dimension));
            })); ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        initialized = true;
    }
    public static void open(ServerPlayer player) { send(player, ""); }
    public static void sendEnvironment(ServerPlayer player) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Environment(player.server.isDedicatedServer())); }
    public static void broadcastDimension(ResourceKey<Level> dimension) { CHANNEL.send(PacketDistributor.ALL.noArg(), new DimensionAdded(dimension.location())); }
    private static void send(ServerPlayer player, String notice) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), PersonalPlotWorlds.snapshot(player, notice)); }
    public static void requestOpen() { CHANNEL.sendToServer(new Request(Action.OPEN, "")); }
    public static void requestCreate(PlotSettings settings) { CHANNEL.sendToServer(new Request(Action.CREATE, settings == null ? "" : PlotPresetShare.encode(settings))); }
    public static void requestTemplate(PlotSettings settings) { CHANNEL.sendToServer(new Request(Action.TEMPLATE, PlotPresetShare.encode(settings))); }
    public static void requestTravel() { CHANNEL.sendToServer(new Request(Action.TRAVEL, "")); }
    public static void requestReturn() { CHANNEL.sendToServer(new Request(Action.RETURN, "")); }
    private static void request(Request packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get(); ServerPlayer player = ctx.getSender();
        if (player != null) ctx.enqueueWork(() -> {
            PlotSettings settings = null;
            try {
                // A dedicated server never accepts player-controlled terrain for CREATE.
                if (packet.action == Action.TEMPLATE || packet.action == Action.CREATE && !player.server.isDedicatedServer())
                    settings = PlotPresetShare.decode(packet.settings);
                send(player, PersonalPlotWorlds.handle(player, packet.action, settings));
            } catch (IllegalArgumentException invalid) { send(player, "gtl_enhancedcore.personal_plot.invalid_settings"); }
        }); ctx.setPacketHandled(true);
    }
    private static void writeSettings(FriendlyByteBuf b, PlotSettings settings) {
        b.writeBoolean(settings != null); if (settings != null) b.writeUtf(PlotPresetShare.encode(settings), PlotPresetShare.MAX_TEXT_LENGTH);
    }
    private static PlotSettings readSettings(FriendlyByteBuf b) { return b.readBoolean() ? PlotPresetShare.decode(b.readUtf(PlotPresetShare.MAX_TEXT_LENGTH)) : null; }
    private static void encode(Snapshot p, FriendlyByteBuf b) {
        b.writeBoolean(p.dedicated); b.writeBoolean(p.canEditTemplate); b.writeBoolean(p.canCreatePersonal); b.writeBoolean(p.canCustomizePersonal);
        b.writeBoolean(p.templateReady); b.writeBoolean(p.templatePending); writeSettings(b, p.templateSettings); writeSettings(b, p.activeTemplateSettings);
        b.writeEnum(p.state); writeSettings(b, p.settings); b.writeBoolean(p.inOwnWorld); b.writeBoolean(p.canReturn); b.writeUtf(p.notice, 192);
    }
    private static Snapshot decode(FriendlyByteBuf b) {
        return new Snapshot(b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                readSettings(b), readSettings(b), b.readEnum(PersonalPlotWorlds.State.class), readSettings(b), b.readBoolean(), b.readBoolean(), b.readUtf(192));
    }
}
