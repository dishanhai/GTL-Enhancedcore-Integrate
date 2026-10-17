package com.gtl.enhancedcore.integration.gtmadvancedhatch;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** GTMAdvancedHatch 0.1.10 fix9 retains its last server in the energy statistics. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class EnergyStatServerMemory {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private EnergyStatServerMemory() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverStopped(ServerStoppedEvent event) {
        if (!ModList.get().isLoaded("gtmadvancedhatch")) return;
        try {
            VarHandle server = AccessHolder.SERVER;
            if (server != null) {
                // This event follows the final tick and world unload. Upstream publishes
                // the next server during LevelEvent.Load; never erase that replacement.
                server.compareAndSet(event.getServer(), (MinecraftServer) null);
            }
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
        }
    }

    private static VarHandle resolve() {
        try {
            Class<?> owner = Class.forName(
                    "com.xingmot.gtmadvancedhatch.integration.gtmt.newmonitor.EnergyStat",
                    false, EnergyStatServerMemory.class.getClassLoader());
            return MethodHandles.publicLookup().findStaticVarHandle(owner, "server", MinecraftServer.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnOnce(failure);
            return null;
        }
    }

    private static void warnOnce(Throwable failure) {
        if (WARNED.compareAndSet(false, true)) {
            GTLEnhancedcore.LOGGER.warn("[EnergyStatMemory] Could not release the stopped server reference", failure);
        }
    }

    private static final class AccessHolder {
        private static final VarHandle SERVER = resolve();
    }
}
