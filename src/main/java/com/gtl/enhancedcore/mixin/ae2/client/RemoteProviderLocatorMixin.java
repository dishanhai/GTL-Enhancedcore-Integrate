package com.gtl.enhancedcore.mixin.ae2.client;

import com.gtl.enhancedcore.client.ae2.RemoteProviderHosts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "appeng.menu.locator.BlockEntityLocator", remap = false)
public abstract class RemoteProviderLocatorMixin {
    @Redirect(method = "locate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;", remap = true), require = 1)
    private BlockEntity enhancedcore$remoteHost(Level level, BlockPos pos) {
        return RemoteProviderHosts.find(level, pos);
    }
}
