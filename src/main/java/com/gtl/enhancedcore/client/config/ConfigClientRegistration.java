package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** No common/server class references a Screen or other client implementation. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ConfigClientRegistration {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(EnhancedConfigScreen::new));
        event.enqueueWork(ClientTooltipConfig::initialize);
    }
}
