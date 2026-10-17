package com.gtl.enhancedcore.client.plotinfo;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.item.PlotControllerEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Drop server-provided item presentation before connecting to a different environment. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class PlotControllerClientContext {
    private PlotControllerClientContext() {}
    @SubscribeEvent public static void connecting(ClientPlayerNetworkEvent.LoggingIn event) {
        PlotControllerEnvironment.clear();
    }
    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        PlotControllerEnvironment.clear();
    }
}
