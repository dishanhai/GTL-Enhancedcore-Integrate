package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gtl.enhancedcore.common.util.StructureDetectScanContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.gtlcore.gtlcore.common.item.StructureDetectBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StructureDetectBehavior.class, remap = false)
public abstract class StructureDetectNormalModeMixin {

    @Inject(method = "onItemUseFirst", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void gtlEnhancedcore$removeMirrorToggle(ItemStack stack, UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        if (player == null) return;
        var tag = stack.getOrCreateTag();
        tag.putBoolean("isFlipped", false);
        var machine = MetaMachine.getMachine(context.getLevel(), context.getClickedPos());
        if (machine instanceof IMultiController) return;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.gtl_enhancedcore.structure_detect.normal_only"), true);
        }
        cir.setReturnValue(InteractionResult.PASS);
    }

    @WrapMethod(method = "check", remap = false)
    private java.util.List<?> gtlEnhancedcore$scopeNormalScan(IMultiController controller, BlockPattern pattern,
            boolean ignoredLegacyMode, Operation<java.util.List<?>> original) {
        var scope = StructureDetectScanContext.begin();
        try {
            return scope.finish(original.call(controller, pattern, false));
        } finally {
            scope.close();
        }
    }

    @Redirect(method = "check",
            at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/api/pattern/BlockPattern;checkPatternAt(Lcom/gregtechceu/gtceu/api/pattern/MultiblockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;ZZ)Z"),
            require = 1, remap = false)
    private boolean gtlEnhancedcore$checkNormalOrientationOnly(BlockPattern pattern, MultiblockState state,
            BlockPos origin, Direction direction, Direction upwards, boolean ignoredLegacyMode, boolean invert) {
        boolean matched = pattern.checkPatternAt(state, origin, direction, upwards, false, invert);
        StructureDetectScanContext.record(matched && !state.hasError());
        return matched;
    }
}
