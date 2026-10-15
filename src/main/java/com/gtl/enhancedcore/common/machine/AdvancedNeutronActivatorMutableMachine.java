package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * 进阶中子活化器（{@code gtceu:advanced_neutron_activator}，GTLCore 注册）。
 *
 * <p>用户 2026-10-14 指定：改为 <b>ADD 类机器</b> + 128 条 ADD 跨配方线程。
 * <b>不接入本模组的跨配方类型并行（IV 隔离订单）</b>——见 {@link AddThreadMutableMachine}。
 *
 * <p>上游该机是普通 {@code WorkableElectricMultiblockMachine}：既没有跨配方线程，也不实现 ADD 的
 * {@code IThreadModifierMachine}（故也无法叠加天球分歧引擎 Ω）。改为 ADD 可变机器后两者同时具备，
 * 线程口径 = 128 + Ω。
 *
 * <p>并行沿用 ADD 基类（并行仓当前并行），与上游 {@code GTLRecipeModifiers.getHatchParallel(machine)}
 * 语义一致，因此<b>不需要</b> {@code parallelBaseline()}。
 */
public final class AdvancedNeutronActivatorMutableMachine extends AddThreadMutableMachine {

    public AdvancedNeutronActivatorMutableMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, "advanced_neutron_activator", args);
    }
}
