package com.gtl.enhancedcore.mixin.ae2ct.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.crafting.CraftingTreeBudget;
import com.gtl.enhancedcore.client.crafting.CraftingTreeBuildControl;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.neuvillette.ae2ct.api.CraftingTreeHelper;
import com.neuvillette.ae2ct.api.RecipeHelper;
import java.awt.Point;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "com.neuvillette.ae2ct.api.CraftingTreeHelper", remap = false, priority = 1200)
public abstract class CraftingTreeHelperMemoryMixin implements CraftingTreeBuildControl {
    @Shadow private RecipeHelper recipeHelper;
    @Shadow private List<?> entries;
    @Shadow private Map<AEKey, ?> cache;
    @Shadow private Map<AEKey, ?> amountCache;
    @Shadow private Map<Point, ?> nodesMap;
    @Unique private final CraftingTreeBudget enhancedcore$treeBudget = new CraftingTreeBudget();

    @Override public CraftingTreeBudget enhancedcore$budget() { return enhancedcore$treeBudget; }

    @Override public void enhancedcore$clearBuildInputs() {
        recipeHelper = null;
        entries = List.of();
        cache.clear();
        amountCache.clear();
        nodesMap.clear();
    }

    @WrapMethod(method = "build", remap = false)
    private CraftingTreeHelper.NodeManager enhancedcore$boundBuild(boolean missingOnly,
            Operation<CraftingTreeHelper.NodeManager> original) {
        try {
            enhancedcore$treeBudget.begin();
            return original.call(missingOnly);
        } catch (CraftingTreeBudget.Stopped stopped) {
            if (stopped.reason() != CraftingTreeBudget.Reason.CANCELLED) {
                GTLEnhancedcore.LOGGER.warn("AE2CT display tree stopped: {}, nodes={}, layoutCells={}",
                        stopped.reason(), enhancedcore$treeBudget.nodes(), enhancedcore$treeBudget.layoutCells());
            }
            throw stopped;
        } catch (RuntimeException failure) {
            enhancedcore$treeBudget.fail();
            GTLEnhancedcore.LOGGER.warn("AE2CT display tree failed", failure);
            throw failure;
        } finally {
            enhancedcore$treeBudget.finish();
            enhancedcore$clearBuildInputs();
        }
    }

    @WrapMethod(method = "buildNode", remap = false)
    private CraftingTreeHelper.Node enhancedcore$boundNode(GenericStack stack, Long amount,
            List<GenericStack> inputs, long times, long outputAmount, CraftingTreeHelper.Node parent,
            Operation<CraftingTreeHelper.Node> original) {
        enhancedcore$treeBudget.enterNode();
        try {
            return original.call(stack, amount, inputs, times, outputAmount, parent);
        } finally {
            enhancedcore$treeBudget.leaveNode();
        }
    }

    @WrapMethod(method = "buildNodePosition", remap = false)
    private void enhancedcore$boundLayout(CraftingTreeHelper.Node root, CraftingTreeHelper.NodeManager manager,
            Operation<Void> original) {
        enhancedcore$treeBudget.beginLayout();
        try {
            original.call(root, manager);
        } finally {
            enhancedcore$treeBudget.finish();
        }
    }
}
