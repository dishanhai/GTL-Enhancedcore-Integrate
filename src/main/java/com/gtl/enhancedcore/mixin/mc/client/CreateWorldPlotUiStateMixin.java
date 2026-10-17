package com.gtl.enhancedcore.mixin.mc.client;

import com.gtl.enhancedcore.client.plot.CreateWorldPlotState;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldCreationUiState.class)
public abstract class CreateWorldPlotUiStateMixin {
    @Inject(method = "setWorldType", at = @At("RETURN"))
    private void gtlEnhancedcore$applyConfirmed(WorldCreationUiState.WorldTypeEntry next, CallbackInfo ci) {
        CreateWorldPlotState.onWorldTypeChanged((WorldCreationUiState) (Object) this);
    }
}
