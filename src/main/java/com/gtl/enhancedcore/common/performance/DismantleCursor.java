package com.gtl.enhancedcore.common.performance;

/** Visits every cell of a chosen repeated layout once, preserving upstream x/y/z order. */
public final class DismantleCursor {
    private final int width;
    private final int height;
    private final int[] repetitions;
    private int aisle;
    private int repetition;
    private int x;
    private int y;
    private int z;

    public DismantleCursor(int width, int height, int[] repetitions, int minZ) {
        this.width = width;
        this.height = height;
        this.repetitions = repetitions.clone();
        z = minZ;
        skipEmpty();
    }

    public boolean done() { return aisle == repetitions.length; }
    public int aisleIndex() { return aisle; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public void advance() {
        skipX(1);
    }

    /** Skip a proven empty span within the current local X row, without crossing that row. */
    public int skipX(int count) {
        if (count < 1) throw new IllegalArgumentException("Positive skip required");
        if (done()) return 0;
        int skipped = Math.min(count, width - x);
        x += skipped;
        if (x < width) return skipped;
        x = 0;
        if (++y < height) return skipped;
        y = 0;
        z++;
        if (++repetition < repetitions[aisle]) return skipped;
        repetition = 0;
        aisle++;
        skipEmpty();
        return skipped;
    }

    private void skipEmpty() {
        while (aisle < repetitions.length && repetitions[aisle] <= 0) aisle++;
    }
}
