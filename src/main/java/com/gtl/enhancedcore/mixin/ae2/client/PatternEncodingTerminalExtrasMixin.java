package com.gtl.enhancedcore.mixin.ae2.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.items.ProcessingEncodingPanel;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;

/** Runs the plugin policy after GTLCore's button, mouse, overlay and visibility hooks merge. */
@Mixin(value = {AEBaseScreen.class, ProcessingEncodingPanel.class, Screen.class}, remap = false, priority = 100)
public abstract class PatternEncodingTerminalExtrasMixin {}
