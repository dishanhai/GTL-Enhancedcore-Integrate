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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only runs in a marked, fresh, isolated client. Render timings and sampler costs are separate. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class ChargerVfxClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/charger-vfx-redesign-20261017").toAbsolutePath().normalize();
    private static final Path DIR = Path.of(System.getProperty("gtl.enhancedcore.chargerVfxDirectory", ROOT.resolve("invalid").toString())).toAbsolutePath().normalize();
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> SCENES = new ArrayList<>();
    private static final List<Long> MOTION_TIMES = new ArrayList<>();
    private static final List<BlockPos> POS = new ArrayList<>();
    private static final List<Long> FRAMES = new ArrayList<>();
    private static CompletableFuture<Void> pending;
    private static int state, ticks, frames, scene, checks, sequence;
    private static long previous;
    private static boolean done;
    private static final int WARMUP = 600, SAMPLES = 3000;
    private ChargerVfxClientChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.chargerVfxAudit") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (state == 0) {
                check(DIR.startsWith(ROOT) && !DIR.equals(ROOT) && mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(DIR), "Strict isolated game directory");
                check(Files.isRegularFile(DIR.resolve("charger-vfx-audit.marker")), "Fixture marker");
                mc.options.pauseOnLostFocus = false;
                if (mc.getOverlay() != null || mc.screen == null) return;
                check(!Files.exists(DIR.resolve("saves/charger-vfx-audit")), "Fresh world only");
                state = 1;
                mc.createWorldOpenFlows().createFreshLevel("charger-vfx-audit", new LevelSettings("Charger VFX Audit", GameType.CREATIVE,
                        false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(170017L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (state == 1) {
                var server = mc.getSingleplayerServer();
                pending = CompletableFuture.runAsync(() -> {
                    var world = server.overworld();
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    // Production onLoad auto-binds nearby owners; keep the fixture player away during placement.
                    player.connection.teleport(100, 140, 100, 180, 0);
                    world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    world.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                    world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                    world.setDayTime(6000);
                    for (int x = -16; x <= 16; x++) for (int z = -6; z <= 24; z++)
                        world.setBlock(new BlockPos(x, 79, z), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
                    place(world, new BlockPos(-3, 80, 0), GTValues.LV);
                    place(world, new BlockPos(3, 80, 0), GTValues.MV);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.connection.teleport(0.5, 84.5, 15.5, 180, 6);
                }, server);
                state = 2; ticks = 0;
            } else if (state == 2 && pending.isDone() && ++ticks >= 100) {
                pending.join(); mc.setScreen(null); mc.options.hideGui = true;
                REPORT.put("label", System.getProperty("gtl.enhancedcore.chargerVfxLabel", "unknown"));
                REPORT.put("resolution", List.of(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
                REPORT.put("method", "System.nanoTime between actual RenderTick END callbacks, including render/event/presentation cadence; warmup 600 frames, sample 3000 frames. Dispatcher CPU/counting sink sampled separately after frame measurements, not FPS.");
                state = 3; frames = 0; previous = 0;
            } else if (state == 4 && pending.isDone() && ++ticks >= 100) {
                pending.join(); state = 5; frames = 0; previous = 0;
            } else if (state == 6 && pending.isDone() && ++ticks >= 45) {
                pending.join(); state = 7; frames = 0; sequence = 0;
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    private static void place(net.minecraft.server.level.ServerLevel world, BlockPos pos, int tier) {
        world.setBlock(pos, GTLEnhancedcoreMachines.getWirelessChargers()[tier].defaultBlockState(), 3);
        ((WirelessChargerMachine) MetaMachine.getMachine(world, pos)).setWorkingEnabled(false);
        POS.add(pos);
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.chargerVfxAudit") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (state == 3 || state == 5) {
                long now = System.nanoTime();
                if (++frames > WARMUP && previous != 0) FRAMES.add(now - previous);
                previous = now;
                if (FRAMES.size() < SAMPLES) return;
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("machines", POS.size()); result.put("frameMilliseconds", stats(FRAMES, 1e6));
                result.put("meanCadenceFps", 1e9 / FRAMES.stream().mapToLong(Long::longValue).average().orElseThrow());
                result.put("dispatcher", dispatch(mc)); SCENES.add(result); FRAMES.clear();
                GTLEnhancedcore.LOGGER.info("[CHARGER_VFX_CLIENT] measured {} machines: {}", POS.size(), result);
                if (state == 3) {
                    var server = mc.getSingleplayerServer();
                    pending = CompletableFuture.runAsync(() -> {
                        var world = server.overworld();
                        server.getPlayerList().getPlayer(mc.player.getUUID()).connection.teleport(100, 140, 100, 180, 0);
                        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++) {
                            BlockPos pos = new BlockPos(-9 + col * 6, 80, row * 6);
                            if (!POS.contains(pos)) place(world, pos, col % 2 == 0 ? GTValues.LV : GTValues.MV);
                        }
                        // All sixteen centers stay inside the near LOD for a valid shared-mesh comparison.
                        server.getPlayerList().getPlayer(mc.player.getUUID()).connection.teleport(0.5, 88, 29.5, 180, 10);
                    }, server);
                    state = 4; ticks = 0;
                } else {
                    var server = mc.getSingleplayerServer();
                    pending = CompletableFuture.runAsync(() -> {
                        for (int i = POS.size() - 1; i >= 2; i--) server.overworld().setBlock(POS.remove(i), Blocks.AIR.defaultBlockState(), 3);
                    }, server);
                    scene = 0; nextView(mc);
                }
            } else if (state == 7 && ++frames >= 20) {
                screenshot(mc, "view-" + scene + (scene < 3 ? "-day" : "-night") + ".png");
                if (scene == 1 && sequence < 12) {
                    screenshot(mc, String.format(Locale.ROOT, "motion-%02d.png", sequence++));
                    MOTION_TIMES.add(System.nanoTime());
                    frames = 0; return;
                }
                if (++scene < 6) nextView(mc);
                else {
                    REPORT.put("scenes", SCENES); REPORT.put("motionCaptureNanoTimes", MOTION_TIMES);
                    REPORT.put("checks", checks); REPORT.put("status", "PASS");
                    REPORT.put("scope", "Fresh flat isolated integrated world; all chargers disabled, inactive and without owner. Same audit helper for baseline and candidate. No player saves read.");
                    write(); done = true; GTLEnhancedcore.LOGGER.info("[CHARGER_VFX_CLIENT] PASS {}", checks); mc.stop();
                }
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    private static List<Map<String, Object>> dispatch(Minecraft mc) {
        List<Map<String, Object>> results = new ArrayList<>();
        CountingSource source = new CountingSource();
        PoseStack pose = new PoseStack();
        List<Long> cold = new ArrayList<>(), shared = new ArrayList<>();
        // Each pass shares world time and partial ticks across machines of each tier, like a real frame.
        for (int pass = 0; pass < 100; pass++) {
            float partial = (pass + 0.25f) / 101f;
            for (int i = 0; i < POS.size(); i++) {
                var entity = mc.level.getBlockEntity(POS.get(i));
                check(entity != null && mc.getBlockEntityRenderDispatcher().getRenderer(entity) != null, "Actual registered BER");
                var machine = (WirelessChargerMachine) MetaMachine.getMachine(mc.level, POS.get(i));
                check(!machine.isWorkingEnabled() && !machine.isActive() && machine.getUserId() == null,
                        "Disabled inactive unbound machine " + POS.get(i) + ": enabled=" + machine.isWorkingEnabled()
                                + ",active=" + machine.isActive() + ",owner=" + machine.getUserId());
                source.clear(); long start = System.nanoTime();
                mc.getBlockEntityRenderDispatcher().render(entity, partial, pose, source);
                long ns = System.nanoTime() - start;
                if (pass >= 20) (i < 2 ? cold : shared).add(ns);
                check(source.vertices() > 0, "Dispatcher emits vertices while disabled");
                if (pass == 99) results.add(Map.of("position", POS.get(i).toShortString(), "tier", machine.getTier(),
                        "buffers", source.snapshot(), "vertices", source.vertices()));
            }
        }
        results.add(Map.of("scope", "100 passes, 20 warmup, sequential real dispatcher calls to counting VertexConsumer; includes counter sink overhead, excludes GPU draw/driver. First LV/MV calls vs same-frame reuse calls are separate.",
                "firstTierCallMicroseconds", stats(cold, 1e3), "sameFrameReuseCallMicroseconds", stats(shared, 1e3)));
        return results;
    }

    private static void nextView(Minecraft mc) {
        var preceding = pending;
        var server = mc.getSingleplayerServer();
        int view = scene;
        pending = preceding.thenRunAsync(() -> {
            server.overworld().setDayTime(view < 3 ? 6000 : 18000);
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            switch (view % 3) {
                case 0 -> player.connection.teleport(0.5, 83.5, 11.5, 180, 0);
                case 1 -> player.connection.teleport(11.5, 87, 11.5, 135, 17);
                default -> player.connection.teleport(0.5, 94.5, 0.5, 180, 90);
            }
        }, server);
        state = 6; ticks = 0;
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(DIR.resolve(name)); }
    }
    private static Map<String, Object> stats(List<Long> values, double divisor) {
        if (values.isEmpty()) return Map.of("samples", 0);
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        return Map.of("samples", sorted.length, "mean", values.stream().mapToLong(Long::longValue).average().orElseThrow() / divisor,
                "p50", sorted[sorted.length / 2] / divisor, "p95", sorted[(int) (sorted.length * .95)] / divisor,
                "p99", sorted[(int) (sorted.length * .99)] / divisor, "max", sorted[sorted.length - 1] / divisor);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    private static void write() throws Exception {
        REPORT.put("scenes", SCENES);
        Files.writeString(DIR.resolve("charger-vfx-client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
    }
    private static void fail(Minecraft mc, Throwable error) {
        done = true; REPORT.put("status", "FAIL"); REPORT.put("error", error.toString()); REPORT.put("checks", checks);
        try { write(); } catch (Exception suppressed) { error.addSuppressed(suppressed); }
        GTLEnhancedcore.LOGGER.error("[CHARGER_VFX_CLIENT] FAIL", error); mc.stop();
    }
    private static final class CountingSource implements MultiBufferSource {
        private final Map<RenderType, Counter> counters = new LinkedHashMap<>();
        public VertexConsumer getBuffer(RenderType type) { return counters.computeIfAbsent(type, ignored -> new Counter()); }
        void clear() { counters.values().forEach(c -> c.vertices = 0); }
        int vertices() { return counters.values().stream().mapToInt(c -> c.vertices).sum(); }
        Map<String, Integer> snapshot() { Map<String, Integer> result = new LinkedHashMap<>(); counters.forEach((type, count) -> { if (count.vertices > 0) result.put(type.toString(), count.vertices); }); return result; }
    }
    private static final class Counter implements VertexConsumer {
        int vertices;
        public VertexConsumer vertex(double x, double y, double z) { if (!Double.isFinite(x + y + z)) throw new AssertionError("Finite vertices"); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() { vertices++; }
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    }
}
