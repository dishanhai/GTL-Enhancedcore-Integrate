package com.gtl.enhancedcore.mixin.gtceu.client;

import com.gregtechceu.gtceu.api.gui.factory.MachineUIFactory;
import com.gtl.enhancedcore.client.ae2.RemoteProviderHosts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MachineUIFactory.class, remap = false)
public abstract class RemoteMachineUiHolderMixin {
    @Redirect(method = "readHolderFromSyncData(Lnet/minecraft/network/FriendlyByteBuf;)Lcom/gregtechceu/gtceu/api/machine/MetaMachine;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;", remap = true), require = 1)
    private BlockEntity enhancedcore$remoteMachine(Level level, BlockPos pos) {
        return RemoteProviderHosts.find(level, pos);
    }
}
