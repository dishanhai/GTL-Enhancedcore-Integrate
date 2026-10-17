package com.gtl.enhancedcore.integration.ftbbackups;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** FTB Backups 2 1.0.23 leaves its static server reference after its shutdown callback. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class FtbBackupsServerMemory {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private FtbBackupsServerMemory() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverStopped(ServerStoppedEvent event) {
        if (!ModList.get().isLoaded("ftbbackups2")) return;
        try {
            Access access = AccessHolder.INSTANCE;
            MinecraftServer departed = event.getServer();
            if (access == null || access.server().getVolatile() != departed
                    || !(boolean) access.shutdown().getVolatile()) return;

            // Upstream may time out waiting for a backup. Do not interrupt that future.
            CompletableFuture<?> pending = (CompletableFuture<?>) access.future().getVolatile();
            if (pending != null && !pending.isDone()) {
                WeakReference<MinecraftServer> reference = new WeakReference<>(departed);
                pending.whenComplete((ignored, failure) -> {
                    MinecraftServer stopped = reference.get();
                    if (stopped != null) release(access, stopped);
                });
            } else {
                release(access, departed);
            }
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
        }
    }

    private static void release(Access access, MinecraftServer departed) {
        try {
            if ((boolean) access.shutdown().getVolatile()) {
                // A backup completion may race the next world's startup publication.
                access.server().compareAndSet(departed, (MinecraftServer) null);
            }
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
        }
    }

    private static Access resolve() {
        try {
            ClassLoader loader = FtbBackupsServerMemory.class.getClassLoader();
            Class<?> owner = Class.forName("net.creeperhost.ftbbackups.FTBBackups", false, loader);
            Class<?> handler = Class.forName("net.creeperhost.ftbbackups.BackupHandler", false, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            return new Access(
                    lookup.findStaticVarHandle(owner, "minecraftServer", MinecraftServer.class),
                    lookup.findStaticVarHandle(owner, "isShutdown", boolean.class),
                    lookup.findStaticVarHandle(handler, "currentFuture", CompletableFuture.class));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warnOnce(failure);
            return null;
        }
    }

    private static void warnOnce(Throwable failure) {
        if (WARNED.compareAndSet(false, true)) {
            GTLEnhancedcore.LOGGER.warn("[FtbBackupsMemory] Could not release the stopped server reference", failure);
        }
    }

    private record Access(VarHandle server, VarHandle shutdown, VarHandle future) {}

    /** Resolve only after the optional mod is present and has already run its lifecycle. */
    private static final class AccessHolder {
        private static final Access INSTANCE = resolve();
    }
}
