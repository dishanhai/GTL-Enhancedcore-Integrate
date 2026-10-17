package com.gtl.enhancedcore.mixin.ae2.client;

import appeng.api.parts.IPart;
import com.gtl.enhancedcore.client.ae2.RemoteProviderHosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "appeng.menu.locator.PartLocator", remap = false)
public abstract class RemoteProviderPartLocatorMixin {
    @Redirect(method = "locate", at = @At(value = "INVOKE", target = "Lappeng/api/parts/PartHelper;getPart(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lappeng/api/parts/IPart;"), require = 1)
    private IPart enhancedcore$remotePart(BlockGetter level, BlockPos pos, Direction side) {
        return RemoteProviderHosts.findPart(level, pos, side);
    }
}
