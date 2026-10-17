package com.gtl.enhancedcore.mixin.mc.client;

import com.gtl.enhancedcore.client.plot.CreateWorldPlotScreen;
import com.gtl.enhancedcore.client.plot.CreateWorldPlotState;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the plot-world editor to the Game tab and keeps it in sync with preset changes. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldPlotGameTabMixin extends GridLayoutTab {
    private CreateWorldScreen gtlEnhancedcore$owner;

    protected CreateWorldPlotGameTabMixin(Component title) { super(title); }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtlEnhancedcore$addPlotButton(CreateWorldScreen owner, CallbackInfo ci) {
        gtlEnhancedcore$owner = owner;
        Button button = Button.builder(Component.translatable("gtl_enhancedcore.create_world_plot.button"),
                ignored -> gtlEnhancedcore$owner.getMinecraft().setScreen(new CreateWorldPlotScreen(gtlEnhancedcore$owner)))
                .width(210).build();
        // GameTab currently has four single-column rows; row 4 is directly below Allow Cheats.
        // Adding to the layout (instead of absolute coordinates) keeps tab resize/reflow stable.
        this.layout.addChild(button, 4, 0);
        gtlEnhancedcore$owner.getUiState().addListener(state -> {
            boolean enabled = CreateWorldPlotState.isSkyblock(state);
            button.active = enabled;
            button.visible = enabled;
            CreateWorldPlotState.onWorldTypeChanged(state);
        });
        button.active = CreateWorldPlotState.isSkyblock(gtlEnhancedcore$owner.getUiState());
        button.visible = button.active;
    }
}
