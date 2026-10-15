package com.gtl.enhancedcore.client.memory;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

/** Reusable recipe UIs remain registered; their departed player references do not. */
public final class ModularUiPlayerMemory {
    public interface DepartedPlayerAccess {
        void enhancedcore$releaseDepartedPlayer(Level departed);
    }

    private static final Set<DepartedPlayerAccess> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());

    static { ClientWorldCacheCleanup.register(ModularUiPlayerMemory::release); }

    private ModularUiPlayerMemory() {}

    public static void register(DepartedPlayerAccess ui) {
        synchronized (INSTANCES) { INSTANCES.add(ui); }
    }

    private static void release(Level departed) {
        synchronized (INSTANCES) {
            for (DepartedPlayerAccess ui : INSTANCES) ui.enhancedcore$releaseDepartedPlayer(departed);
        }
    }
}
