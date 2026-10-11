package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AEItemConfigWidget;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputBusPartMachine;
import com.gtl.enhancedcore.common.gui.PagedTagFilterStockBusWidget;
import com.gtl.enhancedcore.common.gui.TagFilterStockBusPaging;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockBusPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = TagFilterMEStockBusPartMachine.class, remap = false)
public abstract class TagFilterMEStockBusMixin extends MEInputBusPartMachine {
    protected TagFilterMEStockBusMixin(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @ModifyConstant(method = "createInventory", constant = @Constant(intValue = 16), require = 1, allow = 1)
    private int enhancedcore$slotCapacity(int original) {
        return TagFilterStockBusPaging.SLOT_COUNT;
    }

    // Both the unsorted cutoff and the amount-sorted limit must match the real inventory capacity.
    @ModifyConstant(method = "refreshList", constant = @Constant(intValue = 16), require = 2, allow = 2)
    private int enhancedcore$pullCapacity(int original) {
        return TagFilterStockBusPaging.SLOT_COUNT;
    }

    @Override public Widget createUIWidget() {
        var root = (WidgetGroup) super.createUIWidget();
        var inventory = root.getWidgetsByType(AEItemConfigWidget.class).get(0);
        var position = inventory.getSelfPosition();
        root.removeWidget(inventory);
        root.addWidget(new PagedTagFilterStockBusWidget(position.x, position.y, inventory));
        return root;
    }
}
