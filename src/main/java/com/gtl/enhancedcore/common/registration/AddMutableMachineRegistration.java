package com.gtl.enhancedcore.common.registration;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.AdvancedNeutronActivatorMutableMachine;
import com.gtl.enhancedcore.common.machine.AdvancedSpsCraftingMutableMachine;
import com.gtl.enhancedcore.common.machine.QftMutableMachine;
import com.gtl.enhancedcore.common.machine.StarUltimateForgeMutableMachine;
import com.gtl.enhancedcore.common.machine.SuperParticleColliderMutableMachine;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import org.gtlcore.gtlcore.common.data.machines.AdditionalMultiBlockMachine;
import org.gtlcore.gtlcore.common.data.machines.MultiBlockMachineA;

/**
 * 把第三方机器接入 GTLAdditions 官方「可变多配方」体系（用户 2026-10-06 指定 qft 用 ADD 的跨配方并行 + 512 线程）。
 *
 * <p>实现方式与 ADD 自身的 {@code MutableMultiBlockModify.setOtherMutable()} 一致：调用 GTCEu 公开 API
 * {@code MachineDefinition.setMachineSupplier(Function<IMachineBlockEntity, MetaMachine>)} 替换机器工厂。
 * 不改 GTLCore 源码、不替换注册 ID、不动结构。
 *
 * <p>执行时机：必须在 GTCEu 机器注册事件回调内调用（此时定义已构造完成且可安全写 supplier）；
 * 类字段的延迟读取同样放在回调内，避免 mod 构造期触发第三方静态初始化。
 */
public final class AddMutableMachineRegistration {

    private static boolean applied;

    private AddMutableMachineRegistration() {}

    /** 在 {@code bus.addGenericListener(MachineDefinition.class, ...)} 回调内调用一次。 */
    public static void init() {
        if (applied) return;
        applied = true;
        applyQft();
        applyThreadStacking();
    }

    private static void applyQft() {
        MultiblockMachineDefinition qft = org.gtlcore.gtlcore.common.data.machines.MultiBlockMachineA.QFT;
        if (qft == null) {
            GTLEnhancedcore.LOGGER.error("qft definition missing; ADD mutable 接入跳过");
            return;
        }
        qft.setMachineSupplier(holder -> (MetaMachine) new QftMutableMachine(holder));
        GTLEnhancedcore.LOGGER.info("qft 已接入 GTLAdditions 可变多配方逻辑（跨配方并行，线程 {}）",
                QftMutableMachine.CROSS_RECIPE_THREADS);
    }

    /**
     * 接入 <b>ADD 跨配方线程</b> 128 条（用户 2026-10-12 指定三台，2026-10-14 追加进阶中子活化器）。
     *
     * <p><b>不是</b>本模组的跨配方类型并行（IV 隔离订单）：这些机器不进
     * {@code IvMachineScope.NATIVE_IDS}，所以不会被 {@code IvNativeEngine} 接管、
     * 不会被替换结构仓室谓词、不会显示「支持跨配方类型并行」。
     *
     * <p>上游它们都是普通 {@code WorkableElectricMultiblockMachine}：既不产生跨配方线程，
     * 也不实现 ADD 的 {@code IThreadModifierMachine}（而 {@code ThreadPartMachine.addedToController()}
     * 只在控制器实现该接口时才交付 Ω 线程，否则 {@code threadMultiplier = 0}）。
     * 换成 ADD 可变机器后两点同时满足，线程口径为 128 + Ω（见 {@code AddThreadMutableMachine}）。
     */
    private static void applyThreadStacking() {
        applyAddThreads(MultiBlockMachineA.STAR_ULTIMATE_MATERIAL_FORGE_FACTORY, "star_ultimate_material_forge_factory",
                holder -> new StarUltimateForgeMutableMachine(holder));
        applyAddThreads(MultiBlockMachineA.SUPER_PARTICLE_COLLIDER, "super_particle_collider",
                holder -> new SuperParticleColliderMutableMachine(holder));
        applyAddThreads(MultiBlockMachineA.ADVANCED_SPS_CRAFTING, "advanced_sps_crafting",
                holder -> new AdvancedSpsCraftingMutableMachine(holder));
        // 进阶中子活化器由 GTLCore 的 AdditionalMultiBlockMachine 注册（不同于上面三台的 MultiBlockMachineA）。
        applyAddThreads(AdditionalMultiBlockMachine.ADVANCED_NEUTRON_ACTIVATOR, "advanced_neutron_activator",
                holder -> new AdvancedNeutronActivatorMutableMachine(holder));
    }

    private static void applyAddThreads(MultiblockMachineDefinition definition, String path,
                                        java.util.function.Function<
                                                com.gregtechceu.gtceu.api.machine.IMachineBlockEntity, MetaMachine> factory) {
        if (definition == null) {
            GTLEnhancedcore.LOGGER.error("{} definition missing; ADD 跨配方线程接入跳过", path);
            return;
        }
        definition.setMachineSupplier(factory::apply);
        GTLEnhancedcore.LOGGER.info("{} 已接入 ADD 跨配方线程：{} 条基准 + 天球分歧引擎叠加",
                path, NATIVE_THREADS);
    }

    /** 与 {@code IvMachineScope.NATIVE_CROSS_RECIPE_THREADS} 同源的日志常量。 */
    private static final int NATIVE_THREADS =
            com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.NATIVE_CROSS_RECIPE_THREADS;
}
