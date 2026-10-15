package com.gtl.enhancedcore.client.crafting;

import java.util.HashMap;

/** Counts the empty spacing entries inserted directly by AE2CT's compact layout too. */
public final class CraftingTreeLayoutMap<K, V> extends HashMap<K, V> {
    private final CraftingTreeBudget budget;
    public CraftingTreeLayoutMap(CraftingTreeBudget budget) { this.budget = budget; }
    @Override public boolean containsKey(Object key) {
        if (budget.layoutActive()) budget.checkpoint();
        return super.containsKey(key);
    }
    @Override public V put(K key, V value) {
        budget.layoutCell();
        return super.put(key, value);
    }
}
