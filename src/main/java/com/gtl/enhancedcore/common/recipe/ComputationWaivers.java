package com.gtl.enhancedcore.common.recipe;

import java.util.HashMap;
import java.util.Map;

/** Consumer-specific waivers never alter a shared hatch or registered recipe. */
public final class ComputationWaivers {

    private ComputationWaivers() {}

    public static boolean engravingLaserPlant(String controllerId, boolean formed) {
        return formed && "gtceu:engraving_laser_plant".equals(controllerId);
    }

    public static <K, V> Map<K, V> withoutInput(Map<K, V> contents, K computation) {
        if (!contents.containsKey(computation)) return contents;
        var remaining = new HashMap<>(contents);
        remaining.remove(computation);
        return remaining;
    }
}
