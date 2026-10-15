package com.gtl.enhancedcore.client.memory;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

/** JEI keeps recipe transfer buttons in its runtime; their departed menu must be detached. */
public final class JeiRecipeTransferButtonMemory {
    public interface Access {
        void enhancedcore$releaseDepartedMenu(Level departed);
        boolean enhancedcore$references(Level level);
    }

    private static final Set<Access> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());

    static { ClientWorldCacheCleanup.register(JeiRecipeTransferButtonMemory::release); }

    private JeiRecipeTransferButtonMemory() {}

    public static void register(Access button) {
        synchronized (INSTANCES) { INSTANCES.add(button); }
    }

    private static void release(Level departed) {
        synchronized (INSTANCES) {
            for (Access button : INSTANCES) button.enhancedcore$releaseDepartedMenu(departed);
        }
    }

    public static int countReferencing(Level level) {
        if (level == null) return 0;
        synchronized (INSTANCES) {
            int count = 0;
            for (Access button : INSTANCES) if (button.enhancedcore$references(level)) count++;
            return count;
        }
    }
}
