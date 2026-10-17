package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.util.StructureDetectScanContext;
import java.util.List;

/** Verifies orientation aggregation without booting Minecraft or loading a structure. */
public final class StructureDetectScanContextRegression {
    private StructureDetectScanContextRegression() {}

    public static int run() {
        int assertions = 0;
        try (var scope = StructureDetectScanContext.begin()) {
            StructureDetectScanContext.record(false);
            StructureDetectScanContext.record(true);
            if (!scope.finish(List.of("failed rotation")).isEmpty()) {
                throw new AssertionError("A successful normal rotation must clear errors from failed rotations");
            }
        }
        assertions++;

        List<String> failures = List.of("first direction", "second direction");
        try (var outer = StructureDetectScanContext.begin()) {
            StructureDetectScanContext.record(false);
            try (var inner = StructureDetectScanContext.begin()) {
                StructureDetectScanContext.record(true);
                if (!inner.finish(failures).isEmpty()) throw new AssertionError("Inner match must clear inner failures");
                assertions++;
            }
            List<String> retained = outer.finish(failures);
            if (retained != failures) {
                throw new AssertionError("Inner match must not hide failed outer orientations or reorder errors");
            }
            assertions++;
            try {
                try (var inner = StructureDetectScanContext.begin()) {
                    StructureDetectScanContext.record(true);
                    throw new IllegalArgumentException("simulated pattern failure");
                }
            } catch (IllegalArgumentException expected) {
                if (outer.finish(failures) != failures) throw new AssertionError("Exceptional inner scan leaked state");
                assertions++;
            }
            StructureDetectScanContext.record(true);
            if (!outer.finish(failures).isEmpty()) throw new AssertionError("Outer scope must be restored after nested scan");
            assertions++;
        }
        try (var independent = StructureDetectScanContext.begin()) {
            StructureDetectScanContext.record(false);
            if (independent.finish(failures) != failures) throw new AssertionError("Completed scopes must not contaminate later scans");
            assertions++;
        }
        return assertions;
    }
}
