package com.gtl.enhancedcore.mixin.mc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reuses the server's existing storage lock; never opens a second access to the world. */
@Mixin(MinecraftServer.class)
public interface PersonalPlotServerAccessor {
    @Accessor("storageSource") LevelStorageSource.LevelStorageAccess enhancedcore$personalPlotStorage();
}
