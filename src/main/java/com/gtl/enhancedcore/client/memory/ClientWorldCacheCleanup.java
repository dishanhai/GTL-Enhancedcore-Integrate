package com.gtl.enhancedcore.client.memory;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Optional integrations register callbacks without this class resolving their types. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class ClientWorldCacheCleanup {
    private static final List<Consumer<Level>> CLEANERS = new ArrayList<>();
    private ClientWorldCacheCleanup() {}

    public static void register(Consumer<Level> cleaner) { CLEANERS.add(cleaner); }

    /** Called only for a world being left; each callback checks exact cached world identity. */
    public static void release(Level departed) {
        if (departed == null || !departed.isClientSide) return;
        for (Consumer<Level> cleaner : CLEANERS) cleaner.accept(departed);
    }

    @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide) release(level);
    }

    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) release(event.getPlayer().level());
    }

    @SubscribeEvent public static void cloned(ClientPlayerNetworkEvent.Clone event) {
        if (event.getOldPlayer() != null && event.getNewPlayer() != null
                && event.getOldPlayer().level() != event.getNewPlayer().level()) {
            release(event.getOldPlayer().level());
        }
    }
}
