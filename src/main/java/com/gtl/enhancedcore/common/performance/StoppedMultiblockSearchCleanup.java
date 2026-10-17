package com.gtl.enhancedcore.common.performance;

import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A departed level must not remain captured by GTCEu's periodic pattern-search task. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class StoppedMultiblockSearchCleanup {
    private StoppedMultiblockSearchCleanup() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void unloaded(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // This is GTCEu's existing shutdown API: it interrupts the departed world's executor and
        // clears its owner field. It leaves live levels, controller data and recipe ticks intact.
        // Empty levels obtain only an empty, non-dirty SavedData; no search thread is created.
        MultiblockWorldSavedData.getOrCreate(level).releaseExecutorService();
    }
}
