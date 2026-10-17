package com.gtl.enhancedcore.common.performance;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;

/** Small server-lifetime resume hints. No strong controller, geometry or world references. */
public final class DismantleResumeLayouts<K, G> {
    private static final int LIMIT = 32;
    private final ArrayDeque<Entry<K, G>> entries = new ArrayDeque<>();

    public void remember(K controller, G geometry, int facing, int upwards, DismantleLayoutPlanner.Layout layout) {
        forget(controller);
        entries.addLast(new Entry<>(new WeakReference<>(controller), new WeakReference<>(geometry), facing, upwards,
                copy(layout)));
        while (entries.size() > LIMIT) entries.removeFirst();
    }

    public DismantleLayoutPlanner.Layout find(K controller, G geometry, int facing, int upwards) {
        entries.removeIf(entry -> entry.controller.get() == null || entry.geometry.get() == null);
        for (var entry : entries) {
            if (entry.controller.get() == controller && entry.geometry.get() == geometry
                    && entry.facing == facing && entry.upwards == upwards) return copy(entry.layout);
        }
        return null;
    }

    public void forget(K controller) {
        entries.removeIf(entry -> entry.controller.get() == null || entry.geometry.get() == null
                || entry.controller.get() == controller);
    }

    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }

    private static DismantleLayoutPlanner.Layout copy(DismantleLayoutPlanner.Layout source) {
        return new DismantleLayoutPlanner.Layout(source.minZ(), source.repetitions().clone(), source.flipped(), source.score());
    }

    private record Entry<K, G>(WeakReference<K> controller, WeakReference<G> geometry, int facing, int upwards,
                               DismantleLayoutPlanner.Layout layout) {}
}
