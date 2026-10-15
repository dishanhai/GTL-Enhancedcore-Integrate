package com.gtl.enhancedcore.client.copy;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.config.CopyKeysConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@code /gtlenhancedcore copykeys reload|status} —— 手动重载/查看「复制 ID / 复制配方」开关。
 *
 * <p>整个类只存在于客户端（{@link Dist#CLIENT}）：这些开关只影响本地按键行为，
 * {@link CopyKeysConfig} 是纯客户端实现，因此不能放进 common 包（否则服务端会去加载客户端类）。
 * 配置本身也会自动热重载，此命令用于立即生效或排查。
 */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, value = Dist.CLIENT)
public final class CopyKeysCommand {
    private CopyKeysCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gtlenhancedcore")
                .then(Commands.literal("copykeys")
                        .then(Commands.literal("reload").executes(context -> {
                            try {
                                CopyKeysConfig.reload();
                                context.getSource().sendSuccess(() -> Component.translatable(
                                        "command.gtl_enhancedcore.copykeys.reloaded", state()), false);
                                return 1;
                            } catch (Exception failure) {
                                context.getSource().sendFailure(Component.translatable(
                                        "command.gtl_enhancedcore.copykeys.failed",
                                        String.valueOf(failure.getMessage())));
                                return 0;
                            }
                        }))
                        .then(Commands.literal("status").executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.translatable("command.gtl_enhancedcore.copykeys.status", state()),
                                    false);
                            return 1;
                        }))));
    }

    /** 「物品=开 流体=开 配方=开 实时生成=开」形参顺序与语言键一致。 */
    private static Object[] state() {
        return new Object[]{onOff(CopyKeysConfig.copyItemId()), onOff(CopyKeysConfig.copyFluidId()),
                onOff(CopyKeysConfig.copyRecipe()), onOff(CopyKeysConfig.liveGenerateRecipe())};
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "command.gtl_enhancedcore.on" : "command.gtl_enhancedcore.off");
    }
}
