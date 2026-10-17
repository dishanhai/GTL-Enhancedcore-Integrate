package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MultiblockPartMachine.class, remap = false)
public interface MultiblockPartControllerPositionsAccessor {
    @Accessor("controllerPositions")
    Set<BlockPos> enhancedcore$getControllerPositions();
}
