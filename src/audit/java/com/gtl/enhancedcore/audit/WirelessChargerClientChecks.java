package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.WirelessChargerMachine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Disposable real client/dispatcher probe; no player world or process is touched. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class WirelessChargerClientChecks {
    private static final Path FIXTURE = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/wireless-charger-client-20261017").toAbsolutePath().normalize();
    private static final BlockPos[] POS = {new BlockPos(0, 80, 0), new BlockPos(9, 80, 0)};
    private static final Map<String,Object> REPORT = new LinkedHashMap<>();
    private static int state, ticks, checks;
    private static boolean done;
    private static CompletableFuture<Void> setup;
    private WirelessChargerClientChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.wirelessChargerClientAudit") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            check(mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(FIXTURE), "Strict isolated game directory");
            check(Files.isRegularFile(FIXTURE.resolve("wireless-charger-audit.marker")), "Isolated fixture marker exists");
            mc.options.pauseOnLostFocus = false;
            if (state == 0) {
                if (mc.getOverlay() != null || mc.screen == null) return;
                GTLEnhancedcore.LOGGER.info("[WIRELESS_CHARGER_CLIENT] Creating fresh world from {}", mc.screen.getClass().getName());
                check(!Files.exists(FIXTURE.resolve("saves/charger-audit")), "Create a fresh fixture world");
                state = 1;
                mc.createWorldOpenFlows().createFreshLevel("charger-audit",
                        new LevelSettings("Charger Render Audit", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(170017L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (state == 1) {
                var server = mc.getSingleplayerServer();
                setup = CompletableFuture.runAsync(() -> {
                    var world = server.overworld();
                    world.setDayTime(6000);
                    for (int x = -5; x <= 15; x++) for (int z = -4; z <= 22; z++)
                        world.setBlock(new BlockPos(x, 79, z), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
                    var defs = GTLEnhancedcoreMachines.getWirelessChargers();
                    for (int i = 0; i < 2; i++) {
                        world.setBlock(POS[i], defs[i == 0 ? GTValues.LV : GTValues.MV].defaultBlockState(), 3);
                        ((WirelessChargerMachine)MetaMachine.getMachine(world, POS[i])).setWorkingEnabled(false);
                    }
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.connection.teleport(4.5, 82, 18.5, 180, -12);
                }, server);
                state = 2; ticks = 0;
                return;
            }
            if (state == 2) {
                if (!setup.isDone() || ++ticks < 80) return;
                setup.join();
                mc.setScreen(null); mc.options.hideGui = true;
                for (int i = 0; i < 2; i++) {
                    var entity = mc.level.getBlockEntity(POS[i]);
                    check(entity != null, "Client receives actual machine block entity");
                    var machine = (WirelessChargerMachine)MetaMachine.getMachine(mc.level, POS[i]);
                    check(!machine.isWorkingEnabled() && !machine.isActive() && machine.getUserId() == null,
                            "Test machine is disabled, inactive and unbound");
                    var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(entity);
                    check(renderer != null, "Actual BlockEntityRenderDispatcher has registered BER");
                    Map<String,Counter> buffers = new LinkedHashMap<>();
                    MultiBufferSource counted = type -> buffers.computeIfAbsent(type.toString(), key -> new Counter());
                    mc.getBlockEntityRenderDispatcher().render(entity, 0.5F, new PoseStack(), counted);
                    Counter globe = buffers.entrySet().stream().filter(e -> e.getKey().contains("wireless_globe")).map(Map.Entry::getValue).findFirst().orElse(null);
                    Counter current = buffers.entrySet().stream().filter(e -> e.getKey().contains("wireless_current")).map(Map.Entry::getValue).findFirst().orElse(null);
                    check(globe != null && globe.vertices > 0, "Registered dispatcher emits globe vertices while disabled");
                    check(current != null && current.vertices > 0, "Registered dispatcher emits electric current vertices while disabled");
                    REPORT.put(i == 0 ? "LV" : "MV", Map.of("renderer", renderer.getClass().getName(), "globeVertices", globe.vertices,
                            "currentVertices", current.vertices, "disabled", true, "ownerMissing", true));
                }
                state = 3; ticks = 0;
                return;
            }
            if (state == 3 && ++ticks >= 60) {
                try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    screenshot.writeToFile(FIXTURE.resolve("wireless-charger-world.png"));
                }
                REPORT.put("status", "PASS"); REPORT.put("checks", checks);
                REPORT.put("scope", "Fresh isolated integrated world; real client BlockEntityRenderDispatcher; disabled, inactive, unbound machines. Screenshot of actual world frame.");
                writeReport();
                GTLEnhancedcore.LOGGER.info("[WIRELESS_CHARGER_CLIENT] PASS {}", REPORT);
                done = true;
                mc.stop();
            }
        } catch (Throwable error) {
            done = true;
            REPORT.put("status", "FAIL"); REPORT.put("checks", checks); REPORT.put("error", error.toString());
            try { writeReport(); } catch (Exception suppressed) { error.addSuppressed(suppressed); }
            GTLEnhancedcore.LOGGER.error("[WIRELESS_CHARGER_CLIENT] FAIL", error);
            mc.stop();
        }
    }
    private static void writeReport() throws Exception {
        Files.writeString(FIXTURE.resolve("wireless-charger-client-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
    }
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
        checks++;
    }
    private static final class Counter implements VertexConsumer {
        int vertices;
        public VertexConsumer vertex(double x, double y, double z) { return this; }
        public VertexConsumer color(int r,int g,int b,int a) { return this; }
        public VertexConsumer uv(float u,float v) { return this; }
        public VertexConsumer overlayCoords(int u,int v) { return this; }
        public VertexConsumer uv2(int u,int v) { return this; }
        public VertexConsumer normal(float x,float y,float z) { return this; }
        public void endVertex() { vertices++; }
        public void defaultColor(int r,int g,int b,int a) {}
        public void unsetDefaultColor() {}
    }
}
