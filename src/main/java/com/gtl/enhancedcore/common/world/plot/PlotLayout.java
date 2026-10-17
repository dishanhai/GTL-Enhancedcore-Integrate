package com.gtl.enhancedcore.common.world.plot;

/** Shared deterministic sampler used by both terrain generation and the creation preview. */
public final class PlotLayout {
    public enum Cell { GROUND, ROAD, BORDER, MARKER, LINE }
    private PlotLayout() {}

    public static Cell surfaceAt(PlotSettings settings, int worldX, int worldZ) {
        int period = settings.plotBlocks();
        int x = Math.floorMod(worldX, period), z = Math.floorMod(worldZ, period);
        int markerPeriod = settings.markerMode() == PlotSettings.MarkerMode.CHUNK ? 16 : period;
        if (settings.markerMode() != PlotSettings.MarkerMode.NONE
                && centered(Math.floorMod(worldX, markerPeriod), markerPeriod, settings.effectiveMarkerSize())
                && centered(Math.floorMod(worldZ, markerPeriod), markerPeriod, settings.effectiveMarkerSize())) return Cell.MARKER;
        int edge = Math.min(Math.min(x, period - 1 - x), Math.min(z, period - 1 - z));
        // Each side owns half the shared street; clamp only to preserve a usable plot interior.
        int width = effectiveRoadWidth(settings), border = effectiveBorderWidth(settings);
        Cell base = switch (settings.roadStyle()) {
            case NONE -> Cell.GROUND;
            case OUTLINE -> edge < border ? Cell.BORDER : Cell.GROUND;
            case RING, HIGHWAY, BOULEVARD -> edge < width ? Cell.ROAD
                    : edge < width + border ? Cell.BORDER : Cell.GROUND;
            case GARDEN -> edge < border || (edge >= border + width && edge < border * 2 + width)
                    ? Cell.BORDER : edge < border + width ? Cell.ROAD : Cell.GROUND;
            case AVENUE -> edge < width || centered(x, period, width) || centered(z, period, width)
                    ? Cell.ROAD : edge < width + border ? Cell.BORDER : Cell.GROUND;
        };
        if (base != Cell.ROAD || settings.lineStyle() == PlotSettings.LineStyle.NONE) return base;
        int inset = settings.roadStyle() == PlotSettings.RoadStyle.GARDEN ? border : 0;
        int xEdge = Math.min(x, period - 1 - x), zEdge = Math.min(z, period - 1 - z);
        boolean vertical = xEdge >= inset && xEdge < inset + width;
        boolean horizontal = zEdge >= inset && zEdge < inset + width;
        // Leave junctions open, then put zebra stripes immediately outside the junction.
        if (vertical && horizontal) return base;
        if (settings.lineStyle() == PlotSettings.LineStyle.CROSSWALK) {
            int along = vertical ? z : x;
            int across = vertical ? x : z;
            int distance = Math.min(along, period - 1 - along);
            if ((vertical || horizontal) && distance >= inset + width && distance < inset + width + 3 && across % 2 == 0)
                return Cell.LINE;
        }
        int axis = vertical ? x : z, along = vertical ? z : x;
        boolean stripe = settings.lineStyle() == PlotSettings.LineStyle.DOUBLE
                ? (axis == inset + Math.min(1, width - 1) || axis == period - inset - 1 - Math.min(1, width - 1))
                : axis == inset;
        boolean dash = Math.floorMod(along, settings.dashLength() + settings.dashGap()) < settings.dashLength();
        if ((vertical || horizontal) && stripe && (settings.lineStyle() == PlotSettings.LineStyle.SOLID
                || settings.lineStyle() == PlotSettings.LineStyle.DOUBLE || dash)) return Cell.LINE;
        // Central avenue arms use the same chosen markings and leave the central junction clear.
        if (settings.roadStyle() == PlotSettings.RoadStyle.AVENUE && !vertical && !horizontal) {
            boolean centerVertical = centered(x, period, width), centerHorizontal = centered(z, period, width);
            if (centerVertical && centerHorizontal) return base;
            if (centerVertical || centerHorizontal) {
                int cross = centerVertical ? x : z, coordinate = centerVertical ? z : x;
                int first = period / 2 - width / 2, middle = first + width / 2;
                int distance = Math.abs(coordinate - period / 2);
                if (settings.lineStyle() == PlotSettings.LineStyle.CROSSWALK
                        && distance >= (width + 1) / 2 && distance < (width + 1) / 2 + 3
                        && (cross - first) % 2 == 0) return Cell.LINE;
                boolean lane = settings.lineStyle() == PlotSettings.LineStyle.DOUBLE
                        ? width >= 3 ? cross == middle - 1 || cross == middle + 1
                            : cross == first || cross == first + width - 1
                        : cross == middle;
                boolean continuous = settings.lineStyle() == PlotSettings.LineStyle.SOLID
                        || settings.lineStyle() == PlotSettings.LineStyle.DOUBLE;
                if (lane && (continuous || Math.floorMod(coordinate, settings.dashLength() + settings.dashGap())
                        < settings.dashLength())) return Cell.LINE;
            }
        }
        return base;
    }

    public static boolean supportsLaneMarkings(PlotSettings settings) {
        return settings.roadStyle() != PlotSettings.RoadStyle.NONE && settings.roadStyle() != PlotSettings.RoadStyle.OUTLINE;
    }

    public static int effectiveRoadWidth(PlotSettings settings) {
        int reserve = settings.roadStyle() == PlotSettings.RoadStyle.GARDEN
                ? effectiveBorderWidth(settings) * 2 : effectiveBorderWidth(settings);
        return Math.min(settings.roadWidth(), Math.max(1, settings.plotBlocks() / 2 - reserve - 2));
    }

    public static int effectiveBorderWidth(PlotSettings settings) {
        int reserve = Math.max(0, settings.plotBlocks() / 2 - 3);
        if (settings.roadStyle() == PlotSettings.RoadStyle.GARDEN) reserve /= 2;
        return Math.min(settings.borderWidth(), reserve);
    }

    private static boolean centered(int coordinate, int period, int width) {
        int first = period / 2 - width / 2;
        return coordinate >= first && coordinate < first + width;
    }

    public static String surfaceBlock(PlotSettings settings, int worldX, int worldZ) {
        return switch (surfaceAt(settings, worldX, worldZ)) {
            case GROUND -> settings.groundBlock();
            case ROAD -> settings.roadBlock();
            case BORDER -> settings.borderBlock();
            case MARKER -> settings.markerBlock();
            case LINE -> settings.lineBlock();
        };
    }

    public static String blockIdAt(PlotSettings settings, int worldX, int y, int worldZ) {
        if (y < settings.bedrockY() || y > settings.surfaceY()) return "minecraft:air";
        if (y == settings.bedrockY()) return "minecraft:bedrock";
        if (y == settings.surfaceY()) return surfaceBlock(settings, worldX, worldZ);
        return settings.groundBlock();
    }

    public static int plotCoordinate(PlotSettings settings, int worldCoordinate) {
        return Math.floorDiv(worldCoordinate, settings.plotBlocks());
    }
}
