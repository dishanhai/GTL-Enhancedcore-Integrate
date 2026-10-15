package com.gtl.enhancedcore.client.crafting;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class CraftingTreeMemoryEvents {
    private CraftingTreeMemoryEvents() {}

    @SubscribeEvent public static void closing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof CraftingTreeScreenLifecycle tree) tree.enhancedcore$releaseTree();
    }

    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof CraftingTreeScreenLifecycle tree) tree.enhancedcore$resumeTree();
    }
}
