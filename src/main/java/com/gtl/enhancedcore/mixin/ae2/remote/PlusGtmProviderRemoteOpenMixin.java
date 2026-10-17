package com.gtl.enhancedcore.mixin.ae2.remote;

import appeng.api.crafting.IPatternDetails;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.integration.ae2.remote.RemoteProviderOpen;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.extendedae_plus_gtladd.network.CraftingMonitorOpenGTMProviderC2SPacket", remap = false)
public abstract class PlusGtmProviderRemoteOpenMixin {
    @Inject(method = "gtmOpenUI", at = @At("HEAD"), cancellable = true, require = 1)
    private static void enhancedcore$prepareRemoteAssembly(MetaMachine machine, ServerPlayer player,
                                                         IPatternDetails details, CallbackInfo ci) {
        if (!RemoteProviderOpen.prepare(machine.getHolder().self(), player)) ci.cancel();
    }
}
