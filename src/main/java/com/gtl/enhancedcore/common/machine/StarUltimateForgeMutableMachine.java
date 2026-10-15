package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * 恒星终极物质锻造工厂（{@code gtceu:star_ultimate_material_forge_factory}，GTLCore 注册）。
 *
 * <p>用户 2026-10-12 指定：加 <b>ADD 跨配方线程</b> 128 条 + Ω 叠加。
 * <b>不接入本模组的跨配方类型并行（IV 隔离订单）</b>——见 {@link AddThreadMutableMachine}。
 *
 * <p><b>并行 50000 必须保留</b>：改造前该机的并行由 {@code getRecipeModifier} 里的
 * {@code accurateParallel(..., 50000)} 提供（上游是 1000，本项目 mixin 提到 50000）。
 * 但 ADD 的 {@code MutableRecipesLogic} 只读 {@code getMaxParallel()}、
 * <b>不经过</b> recipeModifier，若不在机器类里补回，改造后并行会掉成并行仓的值
 * （2026-10-12 用户发现该回归）。故此处显式声明 50000。
 */
public final class StarUltimateForgeMutableMachine extends AddThreadMutableMachine {

    /** 改造前由 recipeModifier 提供的固定并行（本项目定制值，勿改）。 */
    public static final int PARALLEL = 50000;

    public StarUltimateForgeMutableMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, "star_ultimate_material_forge_factory", args);
    }

    /** 保留改造前的 50000 并行。 */
    @Override
    protected int parallelBaseline() {
        return PARALLEL;
    }
}
