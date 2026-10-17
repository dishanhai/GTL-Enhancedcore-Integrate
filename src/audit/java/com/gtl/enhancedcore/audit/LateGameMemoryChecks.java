package com.gtl.enhancedcore.audit;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.crafting.CraftingTreeBudget;
import com.gtl.enhancedcore.client.crafting.CraftingTreeBuildControl;
import com.neuvillette.ae2ct.api.CraftingTreeHelper;
import com.neuvillette.ae2ct.api.RecipeHelper;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingCPU;

/** Disposable-world audit: production ticks, real installed AE patterns and actual chunk travel. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class LateGameMemoryChecks {
    private static final boolean ENABLED = Boolean.getBoolean("gtl.enhancedcore.lateGameMemoryAudit");
    private static final Path DIRECTORY = Path.of(System.getProperty(
            "gtl.enhancedcore.memoryAuditDirectory", "_audit/late-game-memory"));
    private static final List<Map<String, Object>> EVENTS = Collections.synchronizedList(new ArrayList<>());
    private static final List<Map<String, Object>> SAMPLES = Collections.synchronizedList(new ArrayList<>());
    private static final List<Map<String, Object>> ORDERS = Collections.synchronizedList(new ArrayList<>());
    private static final List<WeakReference<Object>> OLD = new ArrayList<>();
    private static volatile String phase = "WAIT_WORLD";
    private static volatile boolean pending;
    private static volatile boolean leaveRequested;
    private static volatile boolean finished;
    private static volatile String failure;
    private static MinecraftServer server;
    private static UUID playerId;
    private static long started;
    private static long phaseStarted;
    private static long nextStep;
    private static long nextSample;
    private static long titleStarted;
    private static long firstTitleGc;
    private static long titleComplete;
    private static volatile long performanceComplete;
    private static double homeX, homeY, homeZ;
    private static float homeYaw, homePitch;
    private static GameType homeMode;
    private static IGrid grid;
    private static IGridNode node;
    private static ICraftingService crafting;
    private static Future<ICraftingPlan> calculation;
    private static ICraftingLink submitted;
    private static ICraftingCPU submittedCpu;
    private static AEKey submittedKey;
    private static long submittedAmount;
    private static long stockBeforeSubmit;
    private static final List<AEKey> CANDIDATES = new ArrayList<>();
    private static int candidateIndex;
    private static Map<String, Object> order;
    private static FutureTask<Map<String, Object>> tree;
    private static long treeStarted;
    private static boolean treeAttempted;
    private static int waypoint;
    private static int initialServerTick;
    private static int finalServerTick;
    private static boolean homePrepared;
    private static int availableCraftableKeys;
    private static boolean aeNetworkFound;
    private static boolean coveragePartial;
    private static boolean baselineFrameCaptured;
    private static boolean returnFrameCaptured;
    private static final int[][] ROUTE = {{512, 0}, {1024, 0}, {1024, 512}, {1024, 1024},
            {512, 1024}, {0, 1024}, {-512, 512}, {-512, 0}};

    private LateGameMemoryChecks() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus = false;
            if (leaveRequested) { title(mc); return; }
            if (started == 0) {
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
                Files.createDirectories(DIRECTORY);
                if (!Files.isRegularFile(DIRECTORY.resolve("memory-audit.marker")))
                    throw new IllegalStateException("Disposable-instance memory-audit.marker missing");
                if (!Boolean.getBoolean("gtl.enhancedcore.memoryAuditProductionTicks"))
                    throw new IllegalStateException("Gameplay audit requires memoryAuditProductionTicks=true");
                Files.writeString(DIRECTORY.resolve("helper-contract-checks.json"),
                        new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(contractChecks()));
                started = System.nanoTime();
                server = mc.getSingleplayerServer();
                playerId = mc.player.getUUID();
                change("PREPARE_HOME");
                record("START", Map.of("machineProcessing", "NORMAL_PRODUCTION_TICKS",
                        "performanceForcedGc", false, "world", mc.level.dimension().location().toString()));
            }
            if (mc.level == null || mc.player == null || server == null)
                throw new IllegalStateException("World disappeared before scenario finished");
            captureFrame(mc);
            if (now() >= nextSample) { sample(mc, "natural"); nextSample = now() + 5_000_000_000L; }
            pollTree();
            if (performanceComplete != 0) {
                int hold = Integer.getInteger("gtl.enhancedcore.memoryAuditHoldSeconds", 0);
                if (secondsSince(performanceComplete) >= hold
                        || Files.isRegularFile(DIRECTORY.resolve("CONTINUE.marker"))) leave(mc);
                return;
            }
            if (now() < nextStep || pending) return;
            nextStep = now() + 1_000_000_000L;
            pending = true;
            MinecraftServer target = server;
            target.execute(() -> {
                try { step(target); }
                catch (Throwable error) { failure = error.toString(); record("SCENARIO_ERROR", Map.of("error", failure)); change("FINISH"); }
                finally { pending = false; }
            });
        } catch (Throwable error) {
            failure = error.toString();
            GTLEnhancedcore.LOGGER.error("[LATE_GAME_MEMORY] FAIL", error);
            try { release(); write("FAILED", "memory-audit.json"); } catch (Exception ignored) { }
            finished = true;
            mc.stop();
        }
    }

    private static void step(MinecraftServer target) throws Exception {
        ServerPlayer player = target.getPlayerList().getPlayer(playerId);
        if (player == null) throw new IllegalStateException("Integrated-server audit player missing");
        if (!homePrepared) {
            homePrepared = true;
            String dimension = System.getProperty("gtl.enhancedcore.lateGameHomeDimension", "");
            if (!dimension.isBlank()) {
                var destination = target.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension)));
                if (destination == null) throw new IllegalStateException("Requested audit home dimension missing: " + dimension);
                double x = Double.parseDouble(System.getProperty("gtl.enhancedcore.lateGameHomeX"));
                double y = Double.parseDouble(System.getProperty("gtl.enhancedcore.lateGameHomeY"));
                double z = Double.parseDouble(System.getProperty("gtl.enhancedcore.lateGameHomeZ"));
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || Math.abs(x) > 29_999_000 || Math.abs(z) > 29_999_000)
                    throw new IllegalStateException("Invalid audit home coordinates");
                record("HOME_OVERRIDE", Map.of("fromDimension", player.serverLevel().dimension().location().toString(),
                        "toDimension", dimension, "x", x, "y", y, "z", z, "scope", "DISPOSABLE_WORLD_ONLY"));
                player.teleportTo(destination, x, y, z, player.getYRot(), player.getXRot());
                change("HOME_LOAD");
                return;
            }
            change("BASELINE");
        }
        if (homeMode == null) {
            homeX = player.getX(); homeY = player.getY(); homeZ = player.getZ();
            homeYaw = player.getYRot(); homePitch = player.getXRot();
            homeMode = player.gameMode.getGameModeForPlayer();
            initialServerTick = target.getTickCount();
            record("HOME", Map.of("x", homeX, "y", homeY, "z", homeZ, "mode", homeMode.getName()));
        }
        if (secondsSince(started) > integer("Seconds", 600, 180, 3600) && !phase.equals("FINISH")) {
            record("SCENARIO_DEADLINE", Map.of("interruptedPhase", phase));
            failure = "Scenario deadline reached";
            change("FINISH");
        }
        switch (phase) {
            case "HOME_LOAD" -> { if (secondsSince(phaseStarted) >= 15) change("BASELINE"); }
            case "BASELINE" -> {
                if (secondsSince(phaseStarted) >= integer("BaselineSeconds", 60, 10, 180)) {
                    discover(player);
                    if (CANDIDATES.isEmpty()) { record("AE_UNAVAILABLE", Map.of("reason", "No installed craftable patterns in nearby loaded networks")); startTravel(player); }
                    else beginCalculation(player);
                }
            }
            case "CALCULATING" -> {
                if (calculation.isDone()) {
                    Future<ICraftingPlan> completed = calculation;
                    calculation = null;
                    try { processPlan(player, completed.get()); }
                    catch (Throwable error) {
                        GTLEnhancedcore.LOGGER.error("[LATE_GAME_MEMORY] AE calculation/order failed index={}", candidateIndex, error);
                        endOrder("CALCULATION_FAILED", Map.of("error", error.toString())); nextOrder(player);
                    }
                } else if (secondsSince(phaseStarted) >= integer("CalculationSeconds", 30, 5, 90)) {
                    calculation.cancel(true); calculation = null;
                    endOrder("CALCULATION_TIMEOUT_CANCELLED", Map.of()); nextOrder(player);
                }
            }
            case "ORDER_RUNNING" -> {
                observeOrder(player);
            }
            case "TRAVEL" -> {
                if (secondsSince(phaseStarted) >= integer("WaypointSeconds", 12, 5, 60)) {
                    record("WAYPOINT_SETTLED", Map.of("index", waypoint, "x", player.getX(), "z", player.getZ(),
                            "serverLoadedChunks", player.serverLevel().getChunkSource().getLoadedChunksCount()));
                    if (++waypoint < Math.min(ROUTE.length, integer("Waypoints", 8, 2, 8))) teleportWaypoint(player);
                    else {
                        player.teleportTo(player.serverLevel(), homeX, homeY, homeZ, homeYaw, homePitch);
                        player.setGameMode(homeMode); change("RETURN_STABLE");
                        record("RETURN_HOME", Map.of("x", player.getX(), "y", player.getY(), "z", player.getZ()));
                    }
                }
            }
            case "RETURN_STABLE" -> { if (secondsSince(phaseStarted) >= integer("ReturnSeconds", 45, 10, 180)) change("FINISH"); }
            case "FINISH" -> {
                cancelOwnOrder();
                if (calculation != null) { calculation.cancel(true); calculation = null; }
                player.teleportTo(player.serverLevel(), homeX, homeY, homeZ, homeYaw, homePitch);
                player.setGameMode(homeMode);
                grid = null; node = null; crafting = null; CANDIDATES.clear(); order = null;
                if (tree != null && !tree.isDone()) tree.cancel(true);
                Map<String, Object> coverage = coverage();
                long errors = (long)coverage.get("calculationFailures");
                if (errors > 0 && failure == null) failure = "Audit helper calculation/order failures: " + errors;
                coveragePartial = availableCraftableKeys > 0 && (long)coverage.get("submittedRealOrders") == 0;
                if (coveragePartial) record("AE_COVERAGE_PARTIAL", Map.of(
                        "reason", "Existing AE network had no accepted audit order",
                        "submitAttempts", coverage.get("submitAttempts"), "legitimateRejections", coverage.get("legitimateRejections")));
                finalServerTick = target.getTickCount();
                performanceComplete = now(); change("PERFORMANCE_COMPLETE");
                write("PERFORMANCE_COMPLETE", "CYCLES_COMPLETE.json");
                record("PERFORMANCE_COMPLETE", Map.of("forcedGcDuringPerformance", false));
            }
            default -> { }
        }
    }

    private static void discover(ServerPlayer player) {
        Set<IGrid> grids = Collections.newSetFromMap(new IdentityHashMap<>());
        int radius = integer("SearchChunkRadius", 16, 2, 32);
        int cx = player.chunkPosition().x, cz = player.chunkPosition().z;
        int entities = 0, loaded = 0, bestPatterns = -1;
        List<Map<String, Object>> networks = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            var chunk = player.serverLevel().getChunkSource().getChunkNow(x, z);
            if (chunk == null) continue;
            loaded++;
            for (var entity : chunk.getBlockEntities().values()) {
                entities++;
                List<IGridNode> nodes = new ArrayList<>();
                var host = GridHelper.getNodeHost(player.serverLevel(), entity.getBlockPos());
                if (host != null) {
                    for (Direction side : Direction.values()) {
                        IGridNode found = host.getGridNode(side);
                        if (found != null) nodes.add(found);
                    }
                }
                if (entity instanceof IActionHost action && action.getActionableNode() != null)
                    nodes.add(action.getActionableNode());
                if (entity instanceof MetaMachineBlockEntity machine) {
                    Object meta = machine.getMetaMachine();
                    if (meta instanceof IActionHost action && action.getActionableNode() != null)
                        nodes.add(action.getActionableNode());
                }
                for (IGridNode found : nodes) {
                    if (!found.isActive() || !grids.add(found.getGrid())) continue;
                    IGrid foundGrid = found.getGrid();
                    ICraftingService service = foundGrid.getCraftingService();
                    int patterns = service.getCraftables(AEKeyFilter.none()).size();
                    networks.add(Map.of("at", entity.getBlockPos().toShortString(), "nodes", foundGrid.size(),
                            "craftableKeys", patterns, "cpus", service.getCpus().size(),
                            "busyCpus", service.getCpus().stream().filter(cpu -> cpu.isBusy()).count()));
                    if (patterns > bestPatterns) { bestPatterns = patterns; grid = foundGrid; node = found; crafting = service; }
                }
            }
        }
        record("AE_DISCOVERY", Map.of("loadedChunksScanned", loaded, "blockEntitiesScanned", entities,
                "networks", networks, "searchChunkRadius", radius));
        aeNetworkFound = !grids.isEmpty();
        if (crafting == null || bestPatterns <= 0) return;
        availableCraftableKeys = bestPatterns;
        KeyCounter stock = grid.getStorageService().getCachedInventory();
        List<Candidate> ranked = new ArrayList<>();
        int cap = integer("CandidateScanLimit", 4096, 64, 20000);
        List<CanonicalKey> sortedKeys = new ArrayList<>();
        for (AEKey key : crafting.getCraftables(AEKeyFilter.none()))
            sortedKeys.add(new CanonicalKey(key, key.toTagGeneric().toString()));
        sortedKeys.sort(Comparator.comparing(CanonicalKey::canonical));
        for (CanonicalKey item : sortedKeys.subList(0, Math.min(cap, sortedKeys.size()))) {
            AEKey key = item.key();
            long missing = Long.MAX_VALUE;
            int inputs = 0;
            for (IPatternDetails pattern : crafting.getCraftingFor(key)) {
                long absent = 0;
                for (var input : pattern.getInputs()) {
                    boolean available = false;
                    for (GenericStack possible : input.getPossibleInputs())
                        if (stock.get(possible.what()) >= Math.max(1, possible.amount())) { available = true; break; }
                    if (!available) absent++;
                }
                missing = Math.min(missing, absent); inputs = Math.max(inputs, pattern.getInputs().length);
            }
            ranked.add(new Candidate(key, missing, inputs, item.canonical()));
        }
        ranked.sort(Comparator.comparingLong(Candidate::missing).thenComparingInt(Candidate::inputs)
                .thenComparing(Candidate::canonical));
        // Two likely satisfiable real orders, followed by one dependency-heavy calculation.
        for (Candidate value : ranked) { if (CANDIDATES.size() >= 2) break; CANDIDATES.add(value.key()); }
        AEKey complex = null;
        int best = -1;
        ranked.sort(Comparator.comparingInt(Candidate::inputs).reversed());
        for (Candidate value : ranked.subList(0, Math.min(128, ranked.size()))) {
            int score = dependencyScore(value.key(), new LinkedHashSet<>(), 0);
            if (score > best && !CANDIDATES.contains(value.key())) { best = score; complex = value.key(); }
        }
        if (complex != null) CANDIDATES.add(complex);
        record("AE_CANDIDATES", Map.of("availableCraftableKeys", bestPatterns, "inspectedKeys", ranked.size(),
                "complexDependencyScore", best, "keys", CANDIDATES.stream().map(Object::toString).toList(),
                "canonicalKeys", CANDIDATES.stream().map(key -> key.toTagGeneric().toString()).toList(),
                "existingBusyJobsPreserved", true));
    }

    private record CanonicalKey(AEKey key, String canonical) {}
    private record Candidate(AEKey key, long missing, int inputs, String canonical) {}

    private static int dependencyScore(AEKey key, Set<AEKey> visited, int depth) {
        if (depth >= 16 || visited.size() >= 512 || !visited.add(key)) return 0;
        int score = 1;
        var patterns = crafting.getCraftingFor(key);
        if (!patterns.isEmpty()) {
            IPatternDetails pattern = patterns.stream().min(Comparator.comparing(
                    value -> value.getDefinition().toTagGeneric().toString())).orElseThrow();
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length > 0) score += dependencyScore(possible[0].what(), visited, depth + 1);
            }
        }
        return score;
    }

    private static void beginCalculation(ServerPlayer player) {
        AEKey key = CANDIDATES.get(candidateIndex);
        long amount = candidateIndex >= 2 ? integer("ComplexAmount", 8, 1, 64) : 1;
        order = new LinkedHashMap<>();
        order.put("index", candidateIndex); order.put("key", key.toString()); order.put("amount", amount);
        order.put("canonicalKey", key.toTagGeneric().toString());
        order.put("startedElapsedSeconds", secondsSince(started));
        ORDERS.add(order);
        IActionSource source = IActionSource.ofPlayer(player);
        IGridNode requestNode = node;
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            @Override public IActionSource getActionSource() { return source; }
            @Override public IGridNode getGridNode() { return requestNode; }
        };
        calculation = crafting.beginCraftingCalculation(player.serverLevel(), requester, key, amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
        change("CALCULATING");
        record("AE_CALCULATION_STARTED", Map.of("index", candidateIndex, "key", key.toString(), "amount", amount));
    }

    private static void processPlan(ServerPlayer player, ICraftingPlan plan) throws Exception {
        order.put("planBytes", plan.bytes()); order.put("simulation", plan.simulation());
        order.put("patternKinds", plan.patternTimes().size()); order.put("missingKinds", plan.missingItems().size());
        order.put("usedKinds", plan.usedItems().size());
        record("AE_PLAN_READY", Map.of("index", candidateIndex, "patterns", plan.patternTimes().size(),
                "bytes", plan.bytes(), "simulation", plan.simulation(), "missingKinds", plan.missingItems().size()));
        if (candidateIndex >= 2 && !treeAttempted) startTree(plan, IActionSource.ofPlayer(player));
        if (plan.simulation()) { endOrder("MISSING_ITEMS_NOT_SUBMITTED", Map.of()); nextOrder(player); return; }
        ICraftingCPU requestedCpu = crafting.getCpus().stream()
                .filter(cpu -> !cpu.isBusy() && cpu.getAvailableStorage() >= plan.bytes())
                .min(Comparator.comparingLong(ICraftingCPU::getAvailableStorage)).orElse(null);
        if (requestedCpu == null) {
            endOrder("NO_IDLE_CPU_NOT_SUBMITTED", Map.of("cpus", crafting.getCpus().size(),
                    "busyCpus", crafting.getCpus().stream().filter(ICraftingCPU::isBusy).count()));
            nextOrder(player); return;
        }
        Set<ICraftingCPU> busyBefore = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ICraftingCPU cpu : crafting.getCpus()) if (cpu.isBusy()) busyBefore.add(cpu);
        String requestedCpuName = cpuName(requestedCpu);
        order.put("requestedCpuClass", requestedCpu.getClass().getName());
        order.put("requestedCpuName", requestedCpuName);
        submittedKey = plan.finalOutput().what(); submittedAmount = plan.finalOutput().amount();
        stockBeforeSubmit = grid.getStorageService().getCachedInventory().get(submittedKey);
        order.put("submitAttempted", true);
        var result = crafting.submitJob(plan, null, requestedCpu, false, IActionSource.ofPlayer(player));
        order.put("submitted", result.successful());
        if (!result.successful()) {
            endOrder("SUBMIT_REJECTED", Map.of("errorCode", String.valueOf(result.errorCode()),
                    "errorDetail", String.valueOf(result.errorDetail())));
            nextOrder(player); return;
        }
        submitted = result.link();
        // ICraftingSubmitResult.link() is @Nullable even on success. GTLCore's standalone
        // TransfiniteCraftingLogic explicitly returns successful(null) after creating its real job.
        // A capacity-view CPU also creates a separate active CPU, so resolve that new job by identity.
        submittedCpu = null;
        List<ICraftingCPU> activeAfter = new ArrayList<>(crafting.getCpus());
        if (requestedCpu instanceof TransfiniteCraftingCPU transfinite)
            transfinite.getHost().forEachActiveCpu(activeAfter::add);
        for (ICraftingCPU cpu : activeAfter) {
            var status = cpu.getJobStatus();
            if (!busyBefore.contains(cpu) && cpu.isBusy() && status != null
                    && status.crafting() != null && submittedKey.matches(status.crafting())) {
                if (submittedCpu == null || cpu == requestedCpu) submittedCpu = cpu;
                if (cpu == requestedCpu) break;
            }
        }
        if (submittedCpu == null && requestedCpu.isBusy()) submittedCpu = requestedCpu;
        if (submitted == null && submittedCpu instanceof TransfiniteCraftingCPU transfinite)
            submitted = transfinite.getCraftingLogic().getLastLink();
        order.putAll(submissionDetails(result, submitted, submittedCpu));
        record("AE_ORDER_SUBMITTED", Map.of("index", candidateIndex, "returnLinkPresent", result.link() != null,
                "monitorCpuFound", submittedCpu != null, "monitorLinkPresent", submitted != null,
                "requestedCpu", String.valueOf(requestedCpuName)));
        change("ORDER_RUNNING");
    }

    private static void observeOrder(ServerPlayer player) {
        var status = submittedCpu == null ? null : submittedCpu.getJobStatus();
        boolean busy = submittedCpu != null && submittedCpu.isBusy();
        long stockNow = grid.getStorageService().getCachedInventory().get(submittedKey);
        order.put("lastCpuBusy", busy);
        order.put("stockBeforeSubmit", stockBeforeSubmit); order.put("lastOutputStock", stockNow);
        if (status != null) {
            order.put("lastTotalItems", status.totalItems()); order.put("lastProgress", status.progress());
        }
        if (submitted != null && submitted.isCanceled()) {
            endOrder("CANCELLED_BY_NETWORK", Map.of()); clearOrderMonitor(); nextOrder(player);
        } else if (submitted != null && submitted.isDone()) {
            endOrder("PRODUCTION_COMPLETED", Map.of("linkDone", true)); clearOrderMonitor(); nextOrder(player);
        } else if (!busy && (submittedCpu != null || stockNow - stockBeforeSubmit >= submittedAmount)) {
            String outcome = stockNow - stockBeforeSubmit >= submittedAmount
                    ? "PRODUCTION_COMPLETED" : "CPU_IDLE_RESULT_UNCONFIRMED";
            endOrder(outcome, Map.of("cpuIdle", true, "outputStockDelta", stockNow - stockBeforeSubmit));
            clearOrderMonitor(); nextOrder(player);
        } else if (secondsSince(phaseStarted) >= integer("OrderSeconds", 45, 10, 180)) {
            boolean canceled = cancelOwnOrder();
            endOrder(canceled ? "PRODUCTION_TIMEOUT_CANCELLED" : "SUBMITTED_JOB_MONITOR_UNAVAILABLE",
                    Map.of("cancellationRequested", canceled));
            clearOrderMonitor(); nextOrder(player);
        }
    }

    private static boolean cancelOwnOrder() {
        if (submitted != null) { submitted.cancel(); return true; }
        if (submittedCpu != null && submittedCpu.isBusy()) {
            var status = submittedCpu.getJobStatus();
            if (status != null && status.crafting() != null && submittedKey != null
                    && submittedKey.matches(status.crafting())) { submittedCpu.cancelJob(); return true; }
        }
        return false;
    }

    private static void clearOrderMonitor() { submitted = null; submittedCpu = null; submittedKey = null; }

    private static String cpuName(ICraftingCPU cpu) {
        if (cpu == null) return null;
        var name = cpu.getName();
        return name == null ? null : name.getString();
    }

    private static Map<String, Object> submissionDetails(ICraftingSubmitResult result, ICraftingLink monitorLink,
            ICraftingCPU cpu) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("returnLinkPresent", result.link() != null);
        details.put("monitorLinkPresent", monitorLink != null);
        details.put("monitorCpuClass", cpu == null ? null : cpu.getClass().getName());
        details.put("monitorCpuName", cpuName(cpu));
        details.put("craftingId", monitorLink == null ? null : monitorLink.getCraftingID().toString());
        return details;
    }

    private static void startTree(ICraftingPlan plan, IActionSource source) {
        treeAttempted = true;
        if (!(plan instanceof CraftingPlan concrete)) { record("TREE_UNAVAILABLE", Map.of("planClass", plan.getClass().getName())); return; }
        RecipeHelper recipeData = RecipeHelper.fromCraftingPlan(concrete);
        var entries = List.copyOf(CraftingPlanSummary.fromJob(grid, source, plan).getEntries());
        tree = new FutureTask<>(() -> {
            CraftingTreeHelper helper = new CraftingTreeHelper(recipeData, entries);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "REAL_AE_PLAN"); result.put("uiOpened", false);
            result.put("recipes", recipeData.recipes.size());
            try {
                var built = helper.build(false);
                result.put("status", "BUILT"); result.put("layoutEntries", built.map.size());
            } catch (CraftingTreeBudget.Stopped stopped) { result.put("status", "BUDGET_STOPPED"); result.put("reason", stopped.reason().name()); }
            if (helper instanceof CraftingTreeBuildControl control) result.put("nodes", control.enhancedcore$budget().nodes());
            return result;
        });
        treeStarted = now();
        Thread worker = new Thread(tree, "LateGameRealPlanTreeAudit"); worker.setDaemon(true); worker.start();
        record("TREE_STARTED", Map.of("source", "REAL_AE_PLAN", "uiOpened", false, "recipes", recipeData.recipes.size()));
    }

    private static void pollTree() {
        if (tree == null) return;
        try {
            if (tree.isDone()) { record("TREE_RESULT", tree.get()); tree = null; }
            else if (secondsSince(treeStarted) > 12) { tree.cancel(true); tree = null; record("TREE_TIMEOUT_CANCELLED", Map.of()); }
        } catch (Exception error) { tree = null; record("TREE_FAILED", Map.of("error", error.toString())); }
    }

    private static void endOrder(String status, Map<String, Object> extra) {
        if (order == null) return;
        order.put("status", status); order.put("endedElapsedSeconds", secondsSince(started)); order.putAll(extra);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("index", candidateIndex); result.put("status", status); result.putAll(extra);
        record("AE_ORDER_RESULT", result);
    }

    private static void nextOrder(ServerPlayer player) {
        if (++candidateIndex < CANDIDATES.size()) beginCalculation(player);
        else startTravel(player);
    }

    private static void startTravel(ServerPlayer player) {
        grid = null; node = null; crafting = null; CANDIDATES.clear(); order = null;
        player.setGameMode(GameType.SPECTATOR);
        waypoint = 0;
        record("TRAVEL_STARTED", Map.of("method", "SERVER_TELEPORT_WITH_REAL_CLIENT_CHUNK_LOADING",
                "mode", "spectator", "routePoints", Math.min(ROUTE.length, integer("Waypoints", 8, 2, 8))));
        teleportWaypoint(player);
    }

    private static void teleportWaypoint(ServerPlayer player) {
        double y = Math.min(player.serverLevel().getMaxBuildHeight() - 24, Math.max(homeY + 32,
                player.serverLevel().getMinBuildHeight() + 96));
        double x = homeX + ROUTE[waypoint][0], z = homeZ + ROUTE[waypoint][1];
        player.teleportTo(player.serverLevel(), x, y, z, (waypoint * 45) % 360, 20);
        change("TRAVEL");
        record("WAYPOINT_REQUEST", Map.of("index", waypoint, "x", x, "y", y, "z", z));
    }

    private static void leave(Minecraft mc) throws Exception {
        OLD.add(new WeakReference<>(mc.level)); OLD.add(new WeakReference<>(mc.player)); OLD.add(new WeakReference<>(server));
        server = null; playerId = null; release();
        leaveRequested = true; change("TITLE_WAIT");
        record("LEAVE_WORLD", Map.of("diagnosticGcOnlyAfterTitle", true));
        mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
    }

    private static void title(Minecraft mc) throws Exception {
        if (mc.level != null || mc.player != null || mc.getSingleplayerServer() != null || !(mc.screen instanceof TitleScreen)) return;
        if (titleStarted == 0) { titleStarted = now(); sample(mc, "title_natural"); }
        if (firstTitleGc == 0 && secondsSince(titleStarted) >= 30) {
            System.gc(); firstTitleGc = now(); sample(mc, "title_diagnostic_gc_1");
            record("TITLE_DIAGNOSTIC_GC", Map.of("number", 1, "weakReachable", reachable()));
        }
        if (firstTitleGc != 0 && titleComplete == 0 && secondsSince(firstTitleGc) >= 20) {
            System.gc(); sample(mc, "title_diagnostic_gc_2"); titleComplete = now();
            record("TITLE_DIAGNOSTIC_GC", Map.of("number", 2, "weakReachable", reachable()));
            write("TITLE_COMPLETE", "TITLE_COMPLETE.json");
        }
        if (titleComplete == 0) return;
        int hold = Integer.getInteger("gtl.enhancedcore.memoryAuditHoldAfterTitleSeconds", 0);
        if (secondsSince(titleComplete) < hold && !Files.isRegularFile(DIRECTORY.resolve("CONTINUE_TITLE.marker"))) return;
        finished = true; change("COMPLETE"); write(failure == null && !coveragePartial ? "COMPLETE" : "PARTIAL", "memory-audit.json");
        if (failure == null && !coveragePartial) GTLEnhancedcore.LOGGER.info("[MEMORY_CLIENT] COMPLETE scenario=late_game");
        else GTLEnhancedcore.LOGGER.error("[MEMORY_CLIENT] FAIL scenario=late_game error={} coveragePartial={}", failure, coveragePartial);
        mc.stop();
    }

    private static void release() {
        if (calculation != null) calculation.cancel(true);
        if (tree != null) tree.cancel(true);
        calculation = null; tree = null; clearOrderMonitor(); grid = null; node = null; crafting = null; order = null; CANDIDATES.clear();
    }

    private static void captureFrame(Minecraft mc) {
        if (!Boolean.getBoolean("gtl.enhancedcore.lateGameCaptureFrames")) return;
        String filename = null;
        if (!baselineFrameCaptured && phase.equals("BASELINE")
                && secondsSince(phaseStarted) >= Math.max(0, integer("BaselineSeconds", 60, 10, 180) - 5)) {
            baselineFrameCaptured = true; filename = "late-game-baseline.png";
        } else if (!returnFrameCaptured && phase.equals("RETURN_STABLE")
                && secondsSince(phaseStarted) >= Math.max(0, integer("ReturnSeconds", 45, 10, 180) - 5)) {
            returnFrameCaptured = true; filename = "late-game-return.png";
        }
        if (filename == null) return;
        Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), message -> {});
        record("FRAME_CAPTURE_REQUEST", Map.of("file", "screenshots/" + filename, "source", "GAME_RENDER_TARGET"));
    }

    private static void sample(Minecraft mc, String kind) {
        Map<String, Object> value = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        value.put("elapsedSeconds", secondsSince(started)); value.put("time", Instant.now().toString());
        value.put("phase", phase); value.put("kind", kind);
        if (phase.equals("TRAVEL")) value.put("waypoint", waypoint);
        value.put("heapUsedBytes", runtime.totalMemory() - runtime.freeMemory());
        value.put("heapCommittedBytes", runtime.totalMemory()); value.put("heapMaxBytes", runtime.maxMemory());
        Map<String, Object> collectors = new LinkedHashMap<>();
        for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) collectors.put(collector.getName(),
                Map.of("count", collector.getCollectionCount(), "timeMs", collector.getCollectionTime()));
        value.put("collectors", collectors);
        if (mc.level != null && mc.player != null) {
            value.put("dimension", mc.level.dimension().location().toString());
            value.put("x", mc.player.getX()); value.put("y", mc.player.getY()); value.put("z", mc.player.getZ());
            value.put("clientHasCurrentChunk", mc.level.hasChunkAt(BlockPos.containing(mc.player.position())));
            value.put("clientLoadedChunks", mc.level.getChunkSource().getLoadedChunksCount());
        }
        value.put("oldClientWorldReachable", OLD.size() > 0 && OLD.get(0).get() != null);
        value.put("oldClientPlayerReachable", OLD.size() > 1 && OLD.get(1).get() != null);
        value.put("oldIntegratedServerReachable", OLD.size() > 2 && OLD.get(2).get() != null);
        SAMPLES.add(value);
        record("SAMPLE", Map.of("kind", kind, "heapUsedMiB", (runtime.totalMemory() - runtime.freeMemory()) / 1048576));
    }

    private static void write(String status, String file) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", status); report.put("scenario", "late_game_real_patterns_and_chunk_travel");
        report.put("machineProcessing", "NORMAL_PRODUCTION_TICKS"); report.put("performanceForcedGc", false);
        report.put("coveragePartial", coveragePartial);
        report.put("frameCaptureEnabled", Boolean.getBoolean("gtl.enhancedcore.lateGameCaptureFrames"));
        report.put("failure", failure); report.put("elapsedSeconds", secondsSince(started));
        synchronized (EVENTS) { report.put("events", new ArrayList<>(EVENTS)); }
        synchronized (SAMPLES) { report.put("samples", new ArrayList<>(SAMPLES)); }
        synchronized (ORDERS) { report.put("orders", new ArrayList<>(ORDERS)); }
        long settledPoints;
        synchronized (EVENTS) { settledPoints = EVENTS.stream().filter(value -> "WAYPOINT_SETTLED".equals(value.get("event"))).count(); }
        Map<String, Object> coverage = coverage();
        coverage.put("settledTravelPoints", settledPoints);
        coverage.put("plannedTravelPoints", Math.min(ROUTE.length, integer("Waypoints", 8, 2, 8)));
        coverage.put("treeAttempted", treeAttempted);
        coverage.put("serverTicksDuringScenario", Math.max(0, finalServerTick - initialServerTick));
        report.put("coverage", coverage);
        report.put("weakReachableAtTitle", reachable());
        Files.writeString(DIRECTORY.resolve(file), new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(report));
    }

    private static Map<String, Object> coverage() {
        Map<String, Object> result = summarizeOrders(ORDERS, availableCraftableKeys);
        result.put("aeNetworkFound", aeNetworkFound);
        return result;
    }

    private static Map<String, Object> summarizeOrders(List<Map<String, Object>> orders, int craftableKeys) {
        Map<String, Object> result = new LinkedHashMap<>();
        synchronized (orders) {
            long submittedCount = orders.stream().filter(value -> Boolean.TRUE.equals(value.get("submitted"))).count();
            result.put("submittedRealOrders", submittedCount);
            result.put("completedRealOrders", orders.stream().filter(value -> "PRODUCTION_COMPLETED".equals(value.get("status"))).count());
            result.put("submitAttempts", orders.stream().filter(value -> Boolean.TRUE.equals(value.get("submitAttempted"))).count());
            result.put("legitimateRejections", orders.stream().filter(value -> "SUBMIT_REJECTED".equals(value.get("status"))).count());
            result.put("calculationFailures", orders.stream().filter(value -> "CALCULATION_FAILED".equals(value.get("status"))).count());
            result.put("localCalculationErrors", result.get("calculationFailures"));
            result.put("aeSubmissionExercised", submittedCount > 0);
            result.put("availableCraftableKeys", craftableKeys);
            result.put("aeAbsentSkipped", craftableKeys == 0);
        }
        return result;
    }

    /** Runs without opening a game: nullable third-party contracts exercised by this audit helper. */
    public static void main(String[] args) throws Exception {
        String json = new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(contractChecks());
        if (args.length > 0) Files.writeString(Path.of(args[0]), json);
        System.out.println(json);
    }

    private static Map<String, Object> contractChecks() {
        int[] nameReads = {0};
        ICraftingCPU anonymousCpu = new ICraftingCPU() {
            @Override public boolean isBusy() { return false; }
            @Override public appeng.api.networking.crafting.CraftingJobStatus getJobStatus() { return null; }
            @Override public void cancelJob() { throw new AssertionError("idle fixture must never be cancelled"); }
            @Override public long getAvailableStorage() { return 1024; }
            @Override public int getCoProcessors() { return 0; }
            @Override public net.minecraft.network.chat.Component getName() { nameReads[0]++; return null; }
            @Override public appeng.api.config.CpuSelectionMode getSelectionMode() { return appeng.api.config.CpuSelectionMode.ANY; }
        };
        if (cpuName(null) != null || cpuName(anonymousCpu) != null || nameReads[0] != 1)
            throw new AssertionError("Nullable CPU/name must be safe and name fetched exactly once");
        ICraftingSubmitResult standalone = CraftingSubmitResult.successful(null);
        if (!standalone.successful() || standalone.link() != null)
            throw new AssertionError("Successful standalone result may have no link");
        var details = submissionDetails(standalone, null, anonymousCpu);
        if (!Boolean.FALSE.equals(details.get("monitorLinkPresent")) || details.get("monitorCpuName") != null
                || details.get("craftingId") != null || anonymousCpu.getJobStatus() != null || nameReads[0] != 2)
            throw new AssertionError("Nullable name, link and idle job-status metadata");
        var missingCpu = submissionDetails(standalone, null, null);
        if (missingCpu.get("monitorCpuClass") != null || missingCpu.get("monitorCpuName") != null)
            throw new AssertionError("Unavailable CPU monitor must be explicit null");
        List<Map<String, Object>> rejected = List.of(new LinkedHashMap<>(Map.of(
                "submitAttempted", true, "submitted", false, "status", "SUBMIT_REJECTED")));
        var rejectionCoverage = summarizeOrders(rejected, 1);
        if (!Long.valueOf(1).equals(rejectionCoverage.get("submitAttempts"))
                || !Long.valueOf(0).equals(rejectionCoverage.get("calculationFailures")))
            throw new AssertionError("Legitimate missing-ingredient rejection is an attempt, not helper failure");
        if (!Boolean.TRUE.equals(summarizeOrders(List.of(), 0).get("aeAbsentSkipped")))
            throw new AssertionError("Empty-world AE absence is a legitimate skip");
        var failed = summarizeOrders(List.of(new LinkedHashMap<>(Map.of("status", "CALCULATION_FAILED"))), 1);
        if (!Long.valueOf(1).equals(failed.get("localCalculationErrors")))
            throw new AssertionError("Helper exceptions must count as local calculation errors");
        UUID testId = UUID.fromString("8c054b19-64d4-4cb7-9dd3-5e025ca14539");
        ICraftingLink monitorLink = new ICraftingLink() {
            @Override public boolean isCanceled() { return false; }
            @Override public boolean isDone() { return false; }
            @Override public void cancel() { }
            @Override public boolean isStandalone() { return true; }
            @Override public void writeToNBT(net.minecraft.nbt.CompoundTag tag) { }
            @Override public UUID getCraftingID() { return testId; }
        };
        if (!testId.toString().equals(submissionDetails(standalone, monitorLink, null).get("craftingId")))
            throw new AssertionError("Optional CPU monitor link retains its actual ID");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", true); result.put("checks", List.of("null_cpu", "unnamed_cpu_once", "nullable_job_status",
                "successful_null_link", "nullable_submission_metadata", "missing_cpu_monitor", "rejection_is_attempt", "empty_world_skip"));
        result.put("additionalChecks", List.of("helper_error_count", "optional_monitor_link_id"));
        result.put("anonymousSubmission", details);
        return result;
    }

    private static int reachable() { int count = 0; for (var ref : OLD) if (ref.get() != null) count++; return count; }
    private static void change(String value) { phase = value; phaseStarted = now(); }
    private static long now() { return System.nanoTime(); }
    private static double secondsSince(long since) { return (now() - since) / 1_000_000_000.0; }
    private static int integer(String suffix, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, Integer.getInteger("gtl.enhancedcore.lateGame" + suffix, fallback)));
    }
    private static void record(String type, Map<String, ?> data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", type); event.put("phase", phase); event.put("time", Instant.now().toString());
        event.put("elapsedSeconds", started == 0 ? 0 : secondsSince(started)); event.putAll(data); EVENTS.add(event);
        GTLEnhancedcore.LOGGER.info("[LATE_GAME_MEMORY] {} phase={} {}", type, phase, data);
    }
}
