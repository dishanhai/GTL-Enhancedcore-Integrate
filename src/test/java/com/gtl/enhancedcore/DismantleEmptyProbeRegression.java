package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.performance.DismantleEmptyProbe;
import com.gtl.enhancedcore.common.performance.DismantleCursor;
import com.gtl.enhancedcore.common.performance.TerminalDismantleGuard;
import java.util.HashSet;
import java.util.Random;

final class DismantleEmptyProbeRegression {
    private static int assertions;

    static int run() {
        assertions = 0;
        for (boolean air : new boolean[] {false, true})
            for (boolean controller : new boolean[] {false, true})
                for (boolean preserved : new boolean[] {false, true})
                    check(TerminalDismantleGuard.candidate(air, controller, preserved)
                            == (!air && !controller && !preserved), "only removable candidates keep empty checks alive");

        var huge = new DismantleEmptyProbe(-256, -64, -256, 255, 319, 255);
        int sections = 0;
        while (!huge.done()) { huge.skipSection(); sections++; }
        check(sections == 32 * 24 * 32, "a 100-million-cell empty region needs only 24576 constant-time section checks");

        Random random = new Random(128781);
        for (int trial = 0; trial < 40; trial++) {
            int minX = random.nextInt(80) - 40, minY = random.nextInt(80) - 40, minZ = random.nextInt(80) - 40;
            int width = random.nextInt(20) + 1, height = random.nextInt(20) + 1, depth = random.nextInt(20) + 1;
            var cursor = new DismantleEmptyProbe(minX, minY, minZ, minX + width - 1, minY + height - 1, minZ + depth - 1);
            var visited = new HashSet<String>();
            while (!cursor.done()) {
                if (!cursor.cells()) { cursor.beginCells(); continue; }
                int x = cursor.x(), y = cursor.y(), z = cursor.z();
                check(x >= minX && x < minX + width && y >= minY && y < minY + height && z >= minZ && z < minZ + depth,
                        "section intersections never inspect outside the structure bounds");
                check(Math.floorDiv(x, 16) == cursor.chunkX() && Math.floorDiv(y, 16) == cursor.sectionY()
                        && Math.floorDiv(z, 16) == cursor.chunkZ(), "negative coordinates resolve to their actual section");
                check(visited.add(x + "," + y + "," + z), "every nonempty-section position is checked once");
                cursor.advanceCell();
            }
            check(visited.size() == width * height * depth, "all intersection positions are covered without gaps");
        }
        controllerOnlyAndRebuild();
        for (int coordinate = -33; coordinate <= 33; coordinate++) for (int direction : new int[] {-1, 1}) {
            int span = TerminalDismantleGuard.emptySpan(coordinate, direction);
            check(span >= 1 && span <= 16, "empty spans are bounded by a single section");
            check(Math.floorDiv(coordinate, 16) == Math.floorDiv(coordinate + (span - 1) * direction, 16),
                    "skips include only the proven empty section in either direction");
            check(Math.floorDiv(coordinate, 16) != Math.floorDiv(coordinate + span * direction, 16),
                    "the next unproven section is never skipped");
        }
        var fast = new DismantleCursor(37, 4, new int[] {0, 3, 1}, -2);
        var slow = new DismantleCursor(37, 4, new int[] {0, 3, 1}, -2);
        int visited = 0;
        while (!fast.done()) {
            int skipped = fast.skipX(16);
            for (int i = 0; i < skipped; i++) slow.advance();
            visited += skipped;
            check(fast.x() == slow.x() && fast.y() == slow.y() && fast.z() == slow.z()
                    && fast.aisleIndex() == slow.aisleIndex() && fast.done() == slow.done(),
                    "bulk empty spans retain exact position across rows, aisles and zero repetitions");
        }
        check(visited == 37 * 4 * 4 && fast.skipX(16) == 0, "bulk skipping neither loses nor restarts layout cells");
        return assertions;
    }

    private static void controllerOnlyAndRebuild() {
        // Independent sparse world: one controller at 0,0,0; all other sections are empty.
        int cells = 0;
        var cursor = new DismantleEmptyProbe(-128, -64, -128, 127, 127, 127);
        while (!cursor.done()) {
            if (cursor.chunkX() != 0 || cursor.chunkZ() != 0 || cursor.sectionY() != 0) {
                cursor.skipSection(); continue;
            }
            if (!cursor.cells()) { cursor.beginCells(); continue; }
            boolean origin = cursor.x() == 0 && cursor.y() == 0 && cursor.z() == 0;
            check(!TerminalDismantleGuard.candidate(!origin, origin, false), "a retained controller cannot masquerade as a structure");
            cells++;
            cursor.advanceCell();
        }
        check(cells == 4096, "only the controller section needs per-cell inspection, not the surrounding millions of air blocks");
        // A later invocation starts fresh rather than trusting a previous empty result.
        var rebuilt = new DismantleEmptyProbe(0, 0, 0, 15, 15, 15);
        rebuilt.beginCells();
        rebuilt.advanceCell();
        check(rebuilt.x() == 1 && TerminalDismantleGuard.candidate(false, false, false),
                "a newly placed block beside the controller remains eligible on the next invocation");
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
