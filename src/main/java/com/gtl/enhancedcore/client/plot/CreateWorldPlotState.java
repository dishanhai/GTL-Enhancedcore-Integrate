package com.gtl.enhancedcore.client.plot;

import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plot.PlotWorldRegistration;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.ResourceLocation;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;

/** Client-only draft state for the new-world screen. It never writes a server config. */
public final class CreateWorldPlotState {
    public static final ResourceLocation SKYBLOCK = new ResourceLocation("skyblockbuilder", "skyblock");
    private static final Map<WorldCreationUiState, PlotSettings> DRAFT =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** True only after the player confirms the plot editor. Selecting Skyblock alone must not replace its generator. */
    private static final Map<WorldCreationUiState, Boolean> ENABLED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CreateWorldPlotState() {}

    public static boolean isSkyblock(WorldCreationUiState state) {
        var holder = state.getWorldType().preset();
        return holder != null && holder.unwrapKey().map(k -> k.location().equals(SKYBLOCK)).orElse(false);
    }

    public static PlotSettings get(WorldCreationUiState state) {
        return DRAFT.computeIfAbsent(state, ignored -> PlotSettings.defaults());
    }

    public static void put(WorldCreationUiState state, PlotSettings settings) {
        DRAFT.put(state, settings);
        ENABLED.put(state, Boolean.TRUE);
        if (isSkyblock(state)) apply(state);
    }

    public static void onWorldTypeChanged(WorldCreationUiState state) {
        if (isSkyblock(state) && Boolean.TRUE.equals(ENABLED.get(state))
                && !(state.getSettings().selectedDimensions().overworld() instanceof PlotChunkGenerator)) apply(state);
    }

    public static void apply(WorldCreationUiState state) {
        state.updateDimensions((access, dimensions) -> PlotWorldRegistration.withPlotOverworld(access, dimensions, get(state)));
    }
}
