package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.performance.DismantleLayoutPlanner;
import com.gtl.enhancedcore.common.performance.DismantleResumeLayouts;
import java.util.ArrayList;

final class DismantleResumeLayoutsRegression {
    private static int assertions;

    static int run() {
        assertions = 0;
        Object controller = new Object(), geometry = new Object();
        var saved = new DismantleResumeLayouts<Object, Object>();
        var layout = new DismantleLayoutPlanner.Layout(-3, new int[] {3, 1, 2}, true, 17);
        saved.remember(controller, geometry, 2, 1, layout);
        layout.repetitions()[0] = 99;
        var resumed = saved.find(controller, geometry, 2, 1);
        check(resumed != null && resumed.minZ() == -3 && resumed.repetitions()[0] == 3 && resumed.flipped(),
                "a cancelled job retains a detached original layout instead of inferring from missing blocks");
        resumed.repetitions()[1] = 42;
        check(saved.find(controller, geometry, 2, 1).repetitions()[1] == 1, "resume callers cannot mutate cached repetitions");
        check(saved.find(new Object(), geometry, 2, 1) == null, "another controller cannot acquire the layout");
        check(saved.find(controller, new Object(), 2, 1) == null, "replacement geometry invalidates the hint");
        check(saved.find(controller, geometry, 3, 1) == null && saved.find(controller, geometry, 2, 0) == null,
                "orientation changes cannot reuse old world coordinates");
        saved.forget(controller);
        check(saved.find(controller, geometry, 2, 1) == null, "completion retires the hint; a later build is checked afresh");
        // Retain fixture identities so this tests the capacity rather than garbage-collection timing.
        var fixtures = new ArrayList<Object>();
        for (int i = 0; i < 40; i++) {
            Object key = new Object(); fixtures.add(key);
            saved.remember(key, geometry, 0, 0, layout);
        }
        check(saved.size() == 32 && saved.find(fixtures.get(0), geometry, 0, 0) == null,
                "resume hints have a hard bound even when controllers remain loaded");
        saved.clear();
        check(saved.size() == 0, "world/server cleanup releases all resume hints");
        return assertions;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        assertions++;
    }
}
