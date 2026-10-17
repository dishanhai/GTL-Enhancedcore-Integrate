package com.gtl.enhancedcore.client.config;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.copy.CopyKeysClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
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
        event.enqueueWork(CopyKeysConfig::initialize);
        event.enqueueWork(CopyKeysClient::register);
        event.enqueueWork(PreviewLayerConfig::initialize);
    }

    /** 复制 ID / 复制配方 的按键注册（默认 0 与 .）。 */
    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        CopyKeysClient.registerKeyMappings(event);
    }
}
