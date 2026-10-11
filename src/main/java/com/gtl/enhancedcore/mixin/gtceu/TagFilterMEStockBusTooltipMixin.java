package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MachineDefinition.class, remap = false)
public abstract class TagFilterMEStockBusTooltipMixin {
    @Inject(method = "getTooltipBuilder", at = @At("RETURN"), cancellable = true, require = 1)
    private void enhancedcore$stockCapacity(CallbackInfoReturnable<BiConsumer<ItemStack, List<Component>>> cir) {
        var id = ((MachineDefinition) (Object) this).getId();
        if (id != null && id.getNamespace().equals("gtceu")
                && id.getPath().equals("tag_filter_me_stock_bus_part_machine")) {
            cir.setReturnValue(MachineTooltips.modify(cir.getReturnValue(), "tag_stock_bus"));
        }
    }
}
