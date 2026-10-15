package com.gtl.enhancedcore.mixin.ae2ct.client;

import appeng.menu.me.crafting.CraftConfirmMenu;
import com.gtl.enhancedcore.client.crafting.CraftingTreeScreenLifecycle;
import com.gtl.enhancedcore.client.crafting.CraftingTreeWidgetControl;
import com.neuvillette.ae2ct.api.ICraftingPlanSummary;
import com.neuvillette.ae2ct.gui.CraftingTreeWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.neuvillette.ae2ct.gui.CraftingTreeScreen", remap = false)
public abstract class CraftingTreeScreenMemoryMixin implements CraftingTreeScreenLifecycle {
    @Shadow private CraftingTreeWidget craftingTreeWidget;

    @Override public void enhancedcore$releaseTree() {
        if (craftingTreeWidget != null) ((CraftingTreeWidgetControl)craftingTreeWidget).enhancedcore$releaseTree();
    }

    @Override public void enhancedcore$resumeTree() {
        if (craftingTreeWidget != null) {
            CraftingTreeWidgetControl widget = (CraftingTreeWidgetControl)craftingTreeWidget;
            if (widget.enhancedcore$released()) enhancedcore$rebuild(widget.enhancedcore$missingOnly());
        }
    }

    @Unique private void enhancedcore$rebuild(boolean missingOnly) {
        // getMenu() is mapped by the compiler; no private field from an AE2 superclass is shadowed.
        CraftConfirmMenu menu = (CraftConfirmMenu)((appeng.client.gui.AEBaseScreen<?>)(Object)this).getMenu();
        if (menu.getPlan() != null) {
            ((CraftingTreeWidgetControl)craftingTreeWidget).enhancedcore$rebuildTree(
                    ((ICraftingPlanSummary)menu.getPlan()).getJob(), menu.getPlan().getEntries(), missingOnly);
        }
    }

    @Inject(method = "showMissingOnly", at = @At("HEAD"), cancellable = true, remap = false,
            require = 1, allow = 1)
    private void enhancedcore$replaceBuild(CallbackInfo ci) {
        if (craftingTreeWidget != null) {
            enhancedcore$rebuild(!((CraftingTreeWidgetControl)craftingTreeWidget).enhancedcore$missingOnly());
        }
        ci.cancel();
    }
}
