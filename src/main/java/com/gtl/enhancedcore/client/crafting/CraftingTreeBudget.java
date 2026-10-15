package com.gtl.enhancedcore.client.crafting;

import java.util.function.LongSupplier;

/** Limits only the optional display tree, never AE2's actual crafting calculation. */
public final class CraftingTreeBudget {
    public static final int MAX_NODES = 32_768;
    public static final int MAX_DEPTH = 128;
    public static final int MAX_LAYOUT_CELLS = 262_144;
    public static final long MAX_BUILD_NANOS = 5_000_000_000L;
    public static final long MAX_SCREENSHOT_PIXELS = 16_777_216L;

    public enum Reason { NODES, DEPTH, LAYOUT, TIME, CANCELLED, FAILED }

    /** Stackless because this is an expected stop condition for an optional visualization. */
    public static final class Stopped extends RuntimeException {
        private final Reason reason;
        public Stopped(Reason reason) {
            super(reason.name(), null, false, false);
            this.reason = reason;
        }
        public Reason reason() { return reason; }
    }

    private final LongSupplier clock;
    private final int maxNodes;
    private final int maxDepth;
    private final int maxLayoutCells;
    private final long maxNanos;
    private volatile boolean cancelled;
    private volatile Reason reason;
    private long started;
    private int nodes;
    private int depth;
    private int layoutCells;
    private boolean timed;
    private boolean layoutActive;

    public CraftingTreeBudget() {
        this(System::nanoTime, MAX_NODES, MAX_DEPTH, MAX_LAYOUT_CELLS, MAX_BUILD_NANOS);
    }

    public CraftingTreeBudget(LongSupplier clock, int maxNodes, int maxDepth, int maxLayoutCells, long maxNanos) {
        this.clock = clock;
        this.maxNodes = maxNodes;
        this.maxDepth = maxDepth;
        this.maxLayoutCells = maxLayoutCells;
        this.maxNanos = maxNanos;
    }

    public void begin() {
        checkCancelled();
        started = clock.getAsLong();
        nodes = depth = layoutCells = 0;
        timed = true;
    }

    public void finish() { timed = false; layoutActive = false; }

    public void enterNode() {
        checkpoint();
        if (nodes >= maxNodes) stop(Reason.NODES);
        if (depth >= maxDepth) stop(Reason.DEPTH);
        nodes++;
        depth++;
    }

    public void leaveNode() { if (depth > 0) depth--; }

    public void beginLayout() {
        checkpoint();
        layoutCells = 0;
        layoutActive = true;
        // A layout change after opening the screen gets its own bounded time window.
        if (!timed) { started = clock.getAsLong(); timed = true; }
    }

    public void layoutCell() {
        checkpoint();
        if (layoutCells >= maxLayoutCells) stop(Reason.LAYOUT);
        layoutCells++;
    }

    public void checkpoint() {
        checkCancelled();
        if (reason != null) throw new Stopped(reason);
        if (timed && clock.getAsLong() - started >= maxNanos) stop(Reason.TIME);
    }

    private void checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) stop(Reason.CANCELLED);
    }

    public void cancel() { cancelled = true; }
    public Reason reason() { return reason; }
    public int nodes() { return nodes; }
    public int layoutCells() { return layoutCells; }
    public boolean layoutActive() { return layoutActive; }
    public void fail() { if (reason == null) reason = Reason.FAILED; }

    public static boolean screenshotFits(int columns, int rows) {
        long width = (long)columns * 110L;
        long height = (long)rows * 110L;
        return width > 0 && height > 0 && width <= 16_384 && height <= 16_384
                && width * height <= MAX_SCREENSHOT_PIXELS;
    }

    private void stop(Reason value) { reason = value; throw new Stopped(value); }
}
