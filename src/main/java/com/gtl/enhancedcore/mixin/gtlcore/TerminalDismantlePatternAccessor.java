package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AdvancedBlockPattern.class, remap = false)
public interface TerminalDismantlePatternAccessor {
    @Accessor("blockMatches") TraceabilityPredicate[][][] enhancedcore$matches();
    @Accessor("centerOffset") int[] enhancedcore$center();
    @Accessor("aisleRepetitions") int[][] enhancedcore$repetitions();
    @Invoker("setActualRelativeOffset") BlockPos enhancedcore$offset(int x, int y, int z,
            Direction facing, Direction upwards, boolean flipped);
    @Invoker("isBlockValid") boolean enhancedcore$valid(BlockState state, BlockPos pos,
            TraceabilityPredicate predicate, MultiblockState temporary);
    @Invoker("insertIntoNestedItemHandler") ItemStack enhancedcore$insertNested(Player player, ItemStack stack);
}
