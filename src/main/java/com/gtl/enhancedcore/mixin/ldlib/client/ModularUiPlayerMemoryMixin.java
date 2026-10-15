package com.gtl.enhancedcore.mixin.ldlib.client;

import com.gtl.enhancedcore.client.memory.ModularUiPlayerMemory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.lowdragmc.lowdraglib.gui.modular.ModularUI", remap = false)
public abstract class ModularUiPlayerMemoryMixin implements ModularUiPlayerMemory.DepartedPlayerAccess {
    @Shadow @Final @Mutable public Player entityPlayer;

    @Inject(method = "<init>(Lcom/lowdragmc/lowdraglib/gui/widget/WidgetGroup;"
            + "Lcom/lowdragmc/lowdraglib/gui/modular/IUIHolder;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private void enhancedcore$registerUi(WidgetGroup widgets, IUIHolder holder, Player player, CallbackInfo ci) {
        ModularUiPlayerMemory.register(this);
    }

    @Override
    public void enhancedcore$releaseDepartedPlayer(Level departed) {
        if (entityPlayer != null && entityPlayer.level() == departed) entityPlayer = null;
    }
}
