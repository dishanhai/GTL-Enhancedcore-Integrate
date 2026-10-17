package com.gtl.enhancedcore.mixin.ae2.remote;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PatternProviderLogic.class, remap = false)
public interface PatternProviderHostAccessor {
    @Accessor("host") PatternProviderLogicHost enhancedcore$getHost();
}
