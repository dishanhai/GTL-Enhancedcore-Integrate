package com.gtl.enhancedcore.mixin.jer.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Applied after ATL's supplier replacements; see JerMobWorldLaziness. */
@Pseudo
@Mixin(targets = "jeresources.util.MobTableBuilder", priority = 100, remap = false)
public abstract class JerMobWorldMemoryMixin {}
