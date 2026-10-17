package com.gtl.enhancedcore.mixin.ae2.remote;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.gtl.enhancedcore.integration.ae2.remote.RemoteProviderOpen;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.extendedae_plus.network.crafting.CraftingMonitorOpenProviderC2SPacket$ProviderUIHelper", remap = false)
public abstract class PlusProviderRemoteOpenMixin {
    @Inject(method = "openProviderUI", at = @At("HEAD"), cancellable = true, require = 1)
    private static void enhancedcore$prepareRemoteProvider(PatternProviderLogic logic, IPatternDetails details,
                                                         ServerPlayer player, CallbackInfo ci) {
        var host = ((PatternProviderHostAccessor) logic).enhancedcore$getHost();
        if (host == null || !RemoteProviderOpen.prepare(host.getBlockEntity(), player)) ci.cancel();
    }
}
