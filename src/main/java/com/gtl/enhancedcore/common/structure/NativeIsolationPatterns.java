package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import net.minecraft.resources.ResourceLocation;

/** Add isolated material ports to the incubator's upstream geometry without changing shared source patterns. */
public final class NativeIsolationPatterns {
    private NativeIsolationPatterns() {}

    public static boolean targets(ResourceLocation id) {
        return id != null && id.getNamespace().equals("gtceu") && id.getPath().equals("huge_incubator");
    }

    public static BlockPattern restrict(BlockPattern original) {
        var access = (BlockPatternAccessor) original;
        var matches = access.gtlEnhancedcore$getBlockMatches().clone();
        var hatches = new IvNativeHatches();
        for (int z = 0; z < matches.length; z++) {
            matches[z] = matches[z].clone();
            for (int y = 0; y < matches[z].length; y++) {
                matches[z][y] = matches[z][y].clone();
                for (int x = 0; x < matches[z][y].length; x++) {
                    matches[z][y][x] = hatches.restrict(matches[z][y][x]);
                }
            }
        }
        return new BlockPattern(matches, original.structureDir, original.aisleRepetitions,
                access.enhanced$getCenterOffset().clone());
    }
}
