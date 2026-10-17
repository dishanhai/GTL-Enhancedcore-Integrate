package com.gtl.enhancedcore.integration.gtmadvancedhatch;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.GTLEnhancedcore;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Releases temporary third-party contexts only after the server has completely stopped. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class StoppedServerContexts {
    private static final AtomicBoolean WIRELESS_WARNED = new AtomicBoolean();
    private static final AtomicBoolean PATTERN_WARNED = new AtomicBoolean();
    private static final AtomicBoolean LOOKUP_WARNED = new AtomicBoolean();

    private StoppedServerContexts() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverStopped(ServerStoppedEvent event) {
        MinecraftServer departed = event.getServer();
        if (ModList.get().isLoaded("gtmthings")) releaseWireless(departed);
        releasePatternContexts(departed);
        if (ModList.get().isLoaded("gtlcore")) releaseLookupContexts(departed);
    }

    private static boolean belongsTo(Level level, MinecraftServer departed) {
        return level instanceof ServerLevel serverLevel && serverLevel.getServer() == departed;
    }

    private static void releaseWireless(MinecraftServer departed) {
        try {
            WirelessAccess access = WirelessHolder.ACCESS;
            if (access == null) return;
            Object current = access.instance().getVolatile();
            if (current != null && belongsTo((ServerLevel) access.level().getVolatile(current), departed)) {
                // stopServer has already saved DimensionDataStorage. Never change balances or
                // the SavedData itself; the next Level.Load publishes its own instance.
                access.instance().compareAndSet(current, null);
            }
        } catch (RuntimeException | LinkageError failure) {
            warn(WIRELESS_WARNED, "wireless saved-data context", failure);
        }
    }

    private static void releasePatternContexts(MinecraftServer departed) {
        try {
            VarHandle state = PatternHolder.STATE;
            if (state == null) return;
            releasePattern(state, MultiblockState.UNLOAD_ERROR, departed);
            releasePattern(state, MultiblockState.UNINIT_ERROR, departed);
        } catch (RuntimeException | LinkageError failure) {
            warn(PATTERN_WARNED, "pattern error context", failure);
        }
    }

    private static void releasePattern(VarHandle state, PatternError error, MinecraftServer departed) {
        MultiblockState current = (MultiblockState) state.getVolatile(error);
        if (current != null && belongsTo(current.getWorld(), departed)) {
            // PatternError.setWorldState is a plain assignment in GTCEu 1.4.4.
            // CAS preserves any newer context; the next setError binds it again.
            state.compareAndSet(error, current, null);
        }
    }

    private static void releaseLookupContexts(MinecraftServer departed) {
        try {
            VarHandle machine = LookupHolder.MACHINE;
            if (machine == null) return;
            for (GTRecipeType type : GTRegistries.RECIPE_TYPES) {
                GTRecipeLookup lookup = type.getLookup();
                Object current = machine.getVolatile(lookup);
                if (current instanceof MetaMachine owner && belongsTo(owner.getLevel(), departed)) {
                    // GTLCore fix4 binds this temporary holder at the start of every
                    // prepareRecipeFind. Recipe definitions and lookup trees stay intact.
                    machine.compareAndSet(lookup, current, null);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            warn(LOOKUP_WARNED, "recipe lookup machine context", failure);
        }
    }

    private static WirelessAccess resolveWireless() {
        try {
            Class<?> owner = Class.forName("com.hepdd.gtmthings.data.WirelessEnergySavaedData",
                    false, StoppedServerContexts.class.getClassLoader());
            return new WirelessAccess(
                    MethodHandles.publicLookup().findStaticVarHandle(owner, "INSTANCE", owner),
                    MethodHandles.privateLookupIn(owner, MethodHandles.lookup())
                            .findVarHandle(owner, "serverLevel", ServerLevel.class));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(WIRELESS_WARNED, "wireless saved-data context", failure);
            return null;
        }
    }

    private static VarHandle resolveField(Class<?> owner, String name, Class<?> type, AtomicBoolean warned,
                                          String context) {
        try {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findVarHandle(owner, name, type);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(warned, context, failure);
            return null;
        }
    }

    private static void warn(AtomicBoolean warned, String context, Throwable failure) {
        if (warned.compareAndSet(false, true)) {
            GTLEnhancedcore.LOGGER.warn("[StoppedServerContexts] Could not release " + context, failure);
        }
    }

    private record WirelessAccess(VarHandle instance, VarHandle level) {}

    private static final class WirelessHolder {
        private static final WirelessAccess ACCESS = resolveWireless();
    }

    private static final class PatternHolder {
        private static final VarHandle STATE = resolveField(PatternError.class, "worldState", MultiblockState.class,
                PATTERN_WARNED, "pattern error context");
    }

    private static final class LookupHolder {
        private static final VarHandle MACHINE = resolveField(GTRecipeLookup.class, "gtlCore$machine",
                IRecipeCapabilityHolder.class, LOOKUP_WARNED, "recipe lookup machine context");
    }
}
