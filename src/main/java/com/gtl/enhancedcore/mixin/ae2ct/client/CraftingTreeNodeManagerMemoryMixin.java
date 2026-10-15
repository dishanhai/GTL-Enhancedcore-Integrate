package com.gtl.enhancedcore.mixin.ae2ct.client;

import com.gtl.enhancedcore.client.crafting.CraftingTreeBuildControl;
import com.gtl.enhancedcore.client.crafting.CraftingTreeLayoutMap;
import com.neuvillette.ae2ct.api.CraftingTreeHelper;
import java.awt.Point;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.neuvillette.ae2ct.api.CraftingTreeHelper$NodeManager", remap = false)
public abstract class CraftingTreeNodeManagerMemoryMixin {
    @Shadow public Map<Point, CraftingTreeHelper.Node> map;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private void enhancedcore$boundSpacingMap(CraftingTreeHelper helper, CraftingTreeHelper.Node root,
            CallbackInfo ci) {
        map = new CraftingTreeLayoutMap<>(((CraftingTreeBuildControl)helper).enhancedcore$budget());
    }
}
