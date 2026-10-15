package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.crafting.CraftingTreeBudget;
import com.gtl.enhancedcore.client.crafting.CraftingTreeJobs;
import com.gtl.enhancedcore.client.crafting.CraftingTreeLayoutMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Pure tests need neither Minecraft bootstrap nor an optional AE2CT class. */
public final class CraftingTreeBudgetRegression {
    private static int checks;
    private CraftingTreeBudgetRegression() {}
    public static void main(String[] args) throws Exception { run(); System.out.println("CraftingTreeBudgetRegression: " + checks); }

    public static int run() throws Exception {
        checks = 0;
        AtomicLong time = new AtomicLong();
        CraftingTreeBudget nodes = new CraftingTreeBudget(time::get, 2, 3, 3, 10);
        nodes.begin();
        nodes.enterNode(); nodes.leaveNode(); nodes.enterNode(); nodes.leaveNode();
        stopped(CraftingTreeBudget.Reason.NODES, nodes::enterNode);
        CraftingTreeBudget depth = new CraftingTreeBudget(time::get, 100, 2, 3, 10);
        depth.begin(); depth.enterNode(); depth.enterNode();
        stopped(CraftingTreeBudget.Reason.DEPTH, depth::enterNode);
        CraftingTreeBudget layout = new CraftingTreeBudget(time::get, 100, 100, 3, 10);
        layout.begin(); layout.beginLayout();
        CraftingTreeLayoutMap<Integer, Object> map = new CraftingTreeLayoutMap<>(layout);
        map.put(1, null); map.put(2, null); map.put(3, null);
        stopped(CraftingTreeBudget.Reason.LAYOUT, () -> map.put(4, null));
        check(map.size() == 3, "compact empty spacing is bounded");
        CraftingTreeBudget deadline = new CraftingTreeBudget(time::get, 100, 100, 100, 10);
        deadline.begin(); time.set(10);
        stopped(CraftingTreeBudget.Reason.TIME, deadline::checkpoint);
        CraftingTreeBudget cancelled = new CraftingTreeBudget(); cancelled.begin(); cancelled.cancel();
        stopped(CraftingTreeBudget.Reason.CANCELLED, cancelled::checkpoint);
        CraftingTreeBudget small = new CraftingTreeBudget(); small.begin();
        for (int i = 0; i < 100; i++) { small.enterNode(); small.leaveNode(); }
        small.beginLayout(); for (int i = 0; i < 200; i++) small.layoutCell(); small.finish();
        check(small.reason() == null && small.nodes() == 100 && small.layoutCells() == 200, "ordinary tree unchanged");
        check(CraftingTreeBudget.screenshotFits(20, 20), "ordinary screenshot retained");
        check(!CraftingTreeBudget.screenshotFits(10000, 128), "huge screenshot blocked before allocation");
        check(!CraftingTreeBudget.screenshotFits(Integer.MAX_VALUE, Integer.MAX_VALUE), "screenshot dimensions do not overflow");

        AtomicInteger active = new AtomicInteger(), maxActive = new AtomicInteger(), cleaned = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), cleanup = new CountDownLatch(3);
        CraftingTreeJobs.Job<Integer> first = CraftingTreeJobs.submit(() -> {
            int now = active.incrementAndGet(); maxActive.accumulateAndGet(now, Math::max); started.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("job release timeout"); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            finally { active.decrementAndGet(); }
            return 1;
        }, () -> {}, () -> { cleaned.incrementAndGet(); cleanup.countDown(); });
        check(started.await(5, TimeUnit.SECONDS), "first worker started");
        CraftingTreeJobs.Job<Integer> second = CraftingTreeJobs.submit(() -> 2, () -> {},
                () -> { cleaned.incrementAndGet(); cleanup.countDown(); });
        CraftingTreeJobs.Job<Integer> third = CraftingTreeJobs.submit(() -> {
            int now = active.incrementAndGet(); maxActive.accumulateAndGet(now, Math::max);
            active.decrementAndGet(); return 3;
        }, () -> {}, () -> { cleaned.incrementAndGet(); cleanup.countDown(); });
        release.countDown();
        check(third.future().get(5, TimeUnit.SECONDS) == 3, "latest tree completes");
        check(cleanup.await(5, TimeUnit.SECONDS) && cleaned.get() == 3, "replaced and finished inputs released");
        check(first.future().isCancelled() && second.future().isCancelled(), "old tasks cancelled");
        check(maxActive.get() == 1, "only one active display worker");
        return checks;
    }

    private static void stopped(CraftingTreeBudget.Reason expected, Runnable action) {
        try { action.run(); throw new AssertionError("expected " + expected); }
        catch (CraftingTreeBudget.Stopped result) { check(result.reason() == expected, expected.name()); }
    }
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
}
