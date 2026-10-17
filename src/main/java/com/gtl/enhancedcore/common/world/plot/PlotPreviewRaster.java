package com.gtl.enhancedcore.common.world.plot;

/** Display-sized raster of the actual terrain, without synthetic marker overlays. */
public final class PlotPreviewRaster {
    public static final int MAX_PIXELS = 1024;
    private PlotPreviewRaster() {}

    /**
     * Palette order follows {@link PlotLayout.Cell}; output is opaque ARGB.
     * When shrinking, exact block coverage is averaged instead of dropping narrow roads or dashes.
     * Enlarged previews use nearest blocks so their square edges remain sharp.
     */
    public static int[] render(PlotSettings settings, int pixels, int plotsPerSide, int[] palette) {
        if (pixels < 1 || pixels > MAX_PIXELS || plotsPerSide < 1 || plotsPerSide > 2
                || palette.length != PlotLayout.Cell.values().length)
            throw new IllegalArgumentException("Invalid plot preview raster dimensions or palette");
        int cells = settings.plotBlocks() * plotsPerSide;
        int[] result = new int[pixels * pixels];
        if (pixels >= cells) {
            for (int z = 0; z < pixels; z++) for (int x = 0; x < pixels; x++) {
                int wx = (int) ((2L * x + 1) * cells / (2L * pixels));
                int wz = (int) ((2L * z + 1) * cells / (2L * pixels));
                result[z * pixels + x] = 0xff000000 | palette[PlotLayout.surfaceAt(settings, wx, wz).ordinal()];
            }
            return result;
        }
        // Common integer coordinates: one block is pixels units, one output pixel is cells units.
        // This makes partial coverage exact, even for odd target sizes and one-block markers.
        for (int z = 0; z < pixels; z++) for (int x = 0; x < pixels; x++) {
            int left = x * cells, right = left + cells, top = z * cells, bottom = top + cells;
            long red = 0, green = 0, blue = 0;
            for (int wz = top / pixels; wz < (bottom + pixels - 1) / pixels; wz++) {
                int weightZ = Math.min(bottom, (wz + 1) * pixels) - Math.max(top, wz * pixels);
                for (int wx = left / pixels; wx < (right + pixels - 1) / pixels; wx++) {
                    int weightX = Math.min(right, (wx + 1) * pixels) - Math.max(left, wx * pixels);
                    int rgb = palette[PlotLayout.surfaceAt(settings, wx, wz).ordinal()];
                    long weight = (long) weightX * weightZ;
                    red += ((rgb >>> 16) & 255) * weight;
                    green += ((rgb >>> 8) & 255) * weight;
                    blue += (rgb & 255) * weight;
                }
            }
            long total = (long) cells * cells;
            result[z * pixels + x] = 0xff000000 | ((int) ((red + total / 2) / total) << 16)
                    | ((int) ((green + total / 2) / total) << 8) | (int) ((blue + total / 2) / total);
        }
        return result;
    }
}
