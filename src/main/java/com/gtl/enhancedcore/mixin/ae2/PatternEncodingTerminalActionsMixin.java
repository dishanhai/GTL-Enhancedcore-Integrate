package com.gtl.enhancedcore.mixin.ae2;

import appeng.menu.me.items.PatternEncodingTermMenu;
import org.spongepowered.asm.mixin.Mixin;

/** Attaches the plugin's postApply policy to the common ordinary/wireless terminal menu. */
@Mixin(value = PatternEncodingTermMenu.class, remap = false, priority = 100)
public abstract class PatternEncodingTerminalActionsMixin {}
