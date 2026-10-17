package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Audit-only integrated client probe. It never runs unless memoryAudit is explicitly enabled by
 * the isolated launcher. The probe changes only the disposable copy of the selected world.
 */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class MemoryClientChecks {
    private static final boolean ENABLED = Boolean.getBoolean("gtl.enhancedcore.memoryAudit");
    private static final int CYCLES = Math.max(1, Math.min(8,
            Integer.getInteger("gtl.enhancedcore.memoryAuditCycles", 2)));
    private static final int SETTLE_TICKS = Math.max(10, Math.min(200,
            Integer.getInteger("gtl.enhancedcore.memoryAuditSettleTicks", 40)));
    private static final int HOLD_SECONDS = Math.max(0, Math.min(3600,
            Integer.getInteger("gtl.enhancedcore.memoryAuditHoldSeconds", 0)));
    private static final int HOLD_AFTER_TITLE_SECONDS = Math.max(0, Math.min(3600,
            Integer.getInteger("gtl.enhancedcore.memoryAuditHoldAfterTitleSeconds", 0)));
    private static final Path DIRECTORY = Path.of(System.getProperty(
            "gtl.enhancedcore.memoryAuditDirectory", "_audit/memory-client"));
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static final List<Map<String, Object>> TRANSITIONS = new ArrayList<>();
    private static final List<WeakReference<Object>> OLD_WORLDS = new ArrayList<>();
    private static final List<WeakReference<Object>> OLD_PLAYERS = new ArrayList<>();
    private static MinecraftServer server;
    private static List<ResourceKey<Level>> dimensions = List.of();
    private static ResourceKey<Level> requested;
    private static long started;
    private static int cycle;
    private static int dimensionIndex;
    private static int transitionsInCycle;
    private static int settle;
    private static boolean startedRun;
    private static boolean gcRequested;
    private static boolean finished;
    private static long holdStarted;
    private static long titleStarted;
    private static long titleSampledAt;
    private static long previewWaitStarted;
    private static boolean exitingWorld;

    private MemoryClientChecks() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || Boolean.getBoolean("gtl.enhancedcore.lateGameMemoryAudit")
                || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus = false;
            if (exitingWorld) {
                if (!(mc.screen instanceof TitleScreen) || mc.level != null || mc.player != null
                        || mc.getSingleplayerServer() != null) return;
                if (titleStarted == 0) {
                    titleStarted = System.nanoTime();
                    log("TITLE_WAIT seconds=30");
                }
                if (System.nanoTime() - titleStarted < 30_000_000_000L) return;
                if (titleSampledAt == 0) {
                    System.gc();
                    sample(mc, "title_gc_after_30s");
                    titleSampledAt = System.nanoTime();
                    writeReport("TITLE_COMPLETE", "TITLE_COMPLETE.json");
                    log("TITLE_COMPLETE hold_seconds=" + HOLD_AFTER_TITLE_SECONDS + " waiting_for=CONTINUE_TITLE.marker");
                }
                if (HOLD_AFTER_TITLE_SECONDS > 0
                        && System.nanoTime() - titleSampledAt < HOLD_AFTER_TITLE_SECONDS * 1_000_000_000L
                        && !Files.isRegularFile(DIRECTORY.resolve("CONTINUE_TITLE.marker"))) return;
                finish(mc, "COMPLETE");
                return;
            }
            if (!startedRun) {
                if (!(mc.screen instanceof TitleScreen) && mc.level == null) return;
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
                if (!previewAuditPassed()) return;
                begin(mc);
                return;
            }
            if (server == null || mc.level == null || mc.player == null) return;
            if (requested != null) {
                if (!mc.level.dimension().equals(requested)) return;
                if (++settle < SETTLE_TICKS) return;
                completeTransition(mc);
                return;
            }
            if (cycle >= CYCLES) {
                if (holdStarted == 0) {
                    holdStarted = System.nanoTime();
                    sample(mc, "cycles_complete");
                    writeReport("CYCLES_COMPLETE", "CYCLES_COMPLETE.json");
                    log("CYCLES_COMPLETE hold_seconds=" + HOLD_SECONDS + " waiting_for=CONTINUE.marker");
                }
                if (HOLD_SECONDS > 0 && System.nanoTime() - holdStarted < HOLD_SECONDS * 1_000_000_000L
                        && !Files.isRegularFile(DIRECTORY.resolve("CONTINUE.marker"))) return;
                leaveWorld(mc);
                return;
            }
            ResourceKey<Level> target = nextDimension(mc.level.dimension());
            if (target == null) {
                cycle++;
                dimensionIndex = 0;
                sample(mc, "cycle_idle");
                return;
            }
            requestDimension(mc, target);
        } catch (Throwable error) {
            fail(mc, error);
        }
    }

    private static void begin(Minecraft mc) throws Exception {
        startedRun = true;
        started = System.nanoTime();
        server = mc.getSingleplayerServer();
        dimensions = java.util.Arrays.stream(System.getProperty("gtl.enhancedcore.memoryAuditDimensions",
                "minecraft:the_nether,minecraft:the_end,minecraft:overworld").split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> ResourceKey.create(Registries.DIMENSION, new ResourceLocation(value))).toList();
        for (ResourceKey<Level> dimension : dimensions)
            require(server.getLevel(dimension) != null, "Dimension is not registered: " + dimension.location());
        Files.createDirectories(DIRECTORY);
        require(Files.isRegularFile(DIRECTORY.resolve("memory-audit.marker")),
                "isolated memory-audit.marker is missing");
        log("START dimensions=" + dimensions + " cycles=" + CYCLES
                + " heap_max_mib=" + Runtime.getRuntime().maxMemory() / 1048576);
        sample(mc, "initial");
    }

    private static boolean previewAuditPassed() throws Exception {
        if (!Boolean.getBoolean("gtl.enhancedcore.lazyPreviewAudit")) return true;
        if (previewWaitStarted == 0) {
            previewWaitStarted = System.nanoTime();
            log("WAIT_LAZY_PREVIEW file=lazy-preview-memory-checks.json");
        }
        Path result = DIRECTORY.resolve("lazy-preview-memory-checks.json");
        if (!Files.isRegularFile(result)) {
            require(System.nanoTime() - previewWaitStarted < 300_000_000_000L,
                    "Lazy preview audit did not produce a report within 300 seconds");
            return false;
        }
        var report = com.google.gson.JsonParser.parseString(Files.readString(result)).getAsJsonObject();
        require(report.has("passed") && report.get("passed").getAsBoolean(),
                "Lazy preview audit failed: " + report);
        log("LAZY_PREVIEW_PASSED");
        return true;
    }

    private static ResourceKey<Level> nextDimension(ResourceKey<Level> current) {
        if (dimensions.isEmpty()) return null;
        for (int offset = 0; offset < dimensions.size(); offset++) {
            int candidate = (dimensionIndex + offset) % dimensions.size();
            ResourceKey<Level> key = dimensions.get(candidate);
            if (!key.equals(current)) {
                dimensionIndex = (candidate + 1) % dimensions.size();
                return key;
            }
        }
        return null;
    }

    private static void requestDimension(Minecraft mc, ResourceKey<Level> target) {
        ServerLevel destination = server.getLevel(target);
        ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (destination == null || player == null) throw new IllegalStateException("Missing dimension/player " + target);
        requested = target;
        settle = 0;
        // Keep the old client objects weakly reachable only for the observation below. No object is
        // retained by this helper after the report is written.
        OLD_WORLDS.add(new WeakReference<>(mc.level));
        OLD_PLAYERS.add(new WeakReference<>(mc.player));
        long before = System.nanoTime();
        server.execute(() -> {
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            player.teleportTo(destination, 0.5, 180, 0.5, player.getYRot(), player.getXRot());
        });
        Map<String, Object> transition = new LinkedHashMap<>();
        transition.put("cycle", cycle);
        transition.put("from", mc.level.dimension().location().toString());
        transition.put("to", target.location().toString());
        transition.put("requestNanos", before);
        TRANSITIONS.add(transition);
        log("DIMENSION_REQUEST cycle=" + cycle + " from=" + mc.level.dimension().location()
                + " to=" + target.location());
    }

    private static void completeTransition(Minecraft mc) {
        System.gc();
        System.runFinalization();
        gcRequested = true;
        sample(mc, "dimension_gc");
        Map<String, Object> transition = TRANSITIONS.getLast();
        transition.put("arrived", mc.level.dimension().location().toString());
        transition.put("oldWorldReachable", OLD_WORLDS.getLast().get() != null);
        transition.put("oldPlayerReachable", OLD_PLAYERS.getLast().get() != null);
        transition.put("gcRequested", gcRequested);
        transition.put("settleTicks", settle);
        requested = null;
        settle = 0;
        gcRequested = false;
        if (++transitionsInCycle >= dimensions.size()) {
            transitionsInCycle = 0;
            cycle++;
            sample(mc, "cycle_complete");
        }
    }

    private static void sample(Minecraft mc, String phase) {
        Map<String, Object> sample = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        sample.put("phase", phase);
        sample.put("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000.0);
        sample.put("dimension", mc.level == null ? null : mc.level.dimension().location().toString());
        sample.put("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory());
        sample.put("heapCommittedBytes", runtime.totalMemory());
        sample.put("heapMaxBytes", runtime.maxMemory());
        sample.put("heapBeanUsedBytes", usage(memory.getHeapMemoryUsage(), "used"));
        sample.put("nonHeapUsedBytes", usage(memory.getNonHeapMemoryUsage(), "used"));
        sample.put("oldWorldWeakReachable", countReachable(OLD_WORLDS));
        sample.put("oldPlayerWeakReachable", countReachable(OLD_PLAYERS));
        Map<String, Object> pools = new LinkedHashMap<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage usage = pool.getUsage();
            if (usage != null) pools.put(pool.getName(), Map.of("used", usage.getUsed(), "committed", usage.getCommitted(),
                    "max", usage.getMax()));
        }
        sample.put("memoryPools", pools);
        SAMPLES.add(sample);
        log("SAMPLE phase=" + phase + " dimension=" + sample.get("dimension")
                + " heap_used_mib=" + ((long) sample.get("heapUsedBytes") / 1048576)
                + " old_world_reachable=" + sample.get("oldWorldWeakReachable")
                + " old_player_reachable=" + sample.get("oldPlayerWeakReachable"));
    }

    private static long usage(MemoryUsage value, String field) {
        return value == null ? -1 : "used".equals(field) ? value.getUsed() : value.getCommitted();
    }

    private static int countReachable(List<WeakReference<Object>> references) {
        int count = 0;
        for (WeakReference<Object> reference : references) if (reference.get() != null) count++;
        return count;
    }

    private static void finish(Minecraft mc, String status) throws Exception {
        finished = true;
        writeReport(status, "memory-audit.json");
        log(status + " cycles=" + cycle + " samples=" + SAMPLES.size());
        mc.stop();
    }

    private static void leaveWorld(Minecraft mc) {
        OLD_WORLDS.add(new WeakReference<>(mc.level));
        OLD_PLAYERS.add(new WeakReference<>(mc.player));
        server = null;
        requested = null;
        exitingWorld = true;
        log("LEAVE_WORLD title_gc_wait_seconds=30");
        mc.level.disconnect();
        mc.clearLevel();
        mc.setScreen(new TitleScreen());
    }

    private static void writeReport(String status, String filename) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", status);
        report.put("cycles", cycle);
        report.put("dimensions", dimensions.stream().map(key -> key.location().toString()).toList());
        report.put("samples", SAMPLES);
        report.put("transitions", TRANSITIONS);
        report.put("oldWorldWeakReachable", countReachable(OLD_WORLDS));
        report.put("oldPlayerWeakReachable", countReachable(OLD_PLAYERS));
        report.put("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000.0);
        report.put("machineProcessing", "AUDIT_AGENT_PAUSED");
        Files.writeString(DIRECTORY.resolve(filename),
                new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static void fail(Minecraft mc, Throwable error) {
        finished = true;
        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(DIRECTORY.resolve("memory-audit-failure.txt"), error.toString());
        } catch (Exception ignored) {
            // The log below is still emitted if the target directory became unavailable.
        }
        GTLEnhancedcore.LOGGER.error("[MEMORY_CLIENT] FAIL", error);
        mc.stop();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[MEMORY_CLIENT] {}", message); }
}
