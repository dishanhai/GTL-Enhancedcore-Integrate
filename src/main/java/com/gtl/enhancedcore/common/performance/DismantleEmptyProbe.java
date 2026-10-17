package com.gtl.enhancedcore.common.performance;

/** Bounded section/cell cursor for a conservative empty-region check. No world references or cache. */
public final class DismantleEmptyProbe {
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final int firstChunkX, lastChunkX, firstChunkZ, lastChunkZ, lastSectionY;
    private int chunkX, chunkZ, sectionY;
    private int x, y, z;
    private boolean cells;

    public DismantleEmptyProbe(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Invalid probe bounds");
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        chunkX = firstChunkX = Math.floorDiv(minX, 16); lastChunkX = Math.floorDiv(maxX, 16);
        chunkZ = firstChunkZ = Math.floorDiv(minZ, 16); lastChunkZ = Math.floorDiv(maxZ, 16);
        sectionY = Math.floorDiv(minY, 16); lastSectionY = Math.floorDiv(maxY, 16);
    }

    public boolean done() { return sectionY > lastSectionY; }
    public boolean cells() { return cells; }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public int sectionY() { return sectionY; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public void beginCells() {
        cells = true;
        x = Math.max(minX, chunkX * 16);
        y = Math.max(minY, sectionY * 16);
        z = Math.max(minZ, chunkZ * 16);
    }

    public void advanceCell() {
        if (++x <= Math.min(maxX, chunkX * 16 + 15)) return;
        x = Math.max(minX, chunkX * 16);
        if (++z <= Math.min(maxZ, chunkZ * 16 + 15)) return;
        z = Math.max(minZ, chunkZ * 16);
        if (++y <= Math.min(maxY, sectionY * 16 + 15)) return;
        skipSection();
    }

    public void skipSection() {
        cells = false;
        if (++chunkX <= lastChunkX) return;
        chunkX = firstChunkX;
        if (++chunkZ <= lastChunkZ) return;
        chunkZ = firstChunkZ;
        sectionY++;
    }
}
