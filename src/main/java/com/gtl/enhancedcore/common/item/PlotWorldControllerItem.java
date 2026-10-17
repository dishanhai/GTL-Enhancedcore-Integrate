package com.gtl.enhancedcore.common.item;

import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

/** All capabilities and world ownership come from the server's personal-world snapshot. */
public final class PlotWorldControllerItem extends Item {
    public PlotWorldControllerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override public Component getName(ItemStack stack) {
        Boolean dedicated = environment();
        return Component.translatable(dedicated == null ? "item.gtl_enhancedcore.plot_world_controller"
                : dedicated ? "item.gtl_enhancedcore.plot_world_controller.server_name"
                : "item.gtl_enhancedcore.plot_world_controller.single_name");
    }

    private static Boolean environment() {
        Boolean dedicated = PlotControllerEnvironment.dedicated();
        if (dedicated != null) return dedicated;
        // Dedicated processes do not receive their own S2C display snapshot.
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.isDedicatedServer();
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.success(stack);
        if (!player.getCooldowns().isOnCooldown(this)) {
            player.getCooldowns().addCooldown(this, 10);
            PersonalPlotNetwork.open(serverPlayer);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        Boolean dedicated = level != null && !level.isClientSide() && level.getServer() != null
                ? level.getServer().isDedicatedServer() : environment();
        tooltip.add(Component.translatable(dedicated == null ? "item.gtl_enhancedcore.plot_world_controller.usage"
                : dedicated ? "item.gtl_enhancedcore.plot_world_controller.server_usage"
                : "item.gtl_enhancedcore.plot_world_controller.single_usage").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.gtl_enhancedcore.plot_world_controller.limit").withStyle(ChatFormatting.YELLOW));
        if (dedicated != null) tooltip.add(Component.translatable(dedicated
                ? "item.gtl_enhancedcore.plot_world_controller.server" : "item.gtl_enhancedcore.plot_world_controller.restart")
                .withStyle(ChatFormatting.GRAY));
        if (Boolean.TRUE.equals(dedicated)) tooltip.add(Component.translatable("item.gtl_enhancedcore.plot_world_controller.instant")
                .withStyle(ChatFormatting.GRAY));
        if (Boolean.TRUE.equals(dedicated)) tooltip.add(Component.translatable("item.gtl_enhancedcore.plot_world_controller.capacity")
                .withStyle(ChatFormatting.YELLOW));
    }
}
