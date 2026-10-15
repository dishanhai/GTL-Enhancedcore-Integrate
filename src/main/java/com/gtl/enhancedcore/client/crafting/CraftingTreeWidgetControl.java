package com.gtl.enhancedcore.client.crafting;

import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.neuvillette.ae2ct.api.RecipeHelper;
import java.util.List;

public interface CraftingTreeWidgetControl {
    void enhancedcore$releaseTree();
    boolean enhancedcore$released();
    boolean enhancedcore$missingOnly();
    void enhancedcore$rebuildTree(RecipeHelper data, List<CraftingPlanSummaryEntry> entries, boolean missingOnly);
}
