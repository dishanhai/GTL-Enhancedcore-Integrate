package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricParallelHatchMultipleRecipesMachine;

/**
 * 临界增殖反应堆（UHV）：走 GTLAdditions 官方跨配方并行体系，非本模组 IV 隔离订单。
 *
 * <ul>
 *   <li>机器基类 {@link GTLAddWorkableElectricParallelHatchMultipleRecipesMachine}：并行由
 *       <b>并行控制仓</b>提供（{@code getMaxParallel()} 取并行仓当前并行），与用户指定的
 *       「支持并行控制仓」一致；不使用本模组的 IV 超级总成隔离。</li>
 *   <li>跨配方线程固定 {@value #CROSS_RECIPE_THREADS} 条（用户指定），经
 *       {@link GTLAddMultipleRecipesLogic#getMultipleThreads()} 生效。
 *       总并行 = 并行仓并行 × 跨配方线程。</li>
 *   <li>配方类型为 GTLCore 的裂变反应堆（{@code GTLRecipeTypes.FISSION_REACTOR_RECIPES}），
 *       在注册端用 {@code .recipeTypes(...)} 声明。</li>
 *   <li>仓室：维护仓、输入/输出仓、并行控制仓、激光靶仓、能源仓；仓室面基础方块为
 *       {@code gtlcore:fission_reactor_casing}（结构谓词见注册端）。</li>
 * </ul>
 */
public final class HighFluxBreederReactorMachine extends GTLAddWorkableElectricParallelHatchMultipleRecipesMachine {

    /** 跨配方线程（用户指定，勿改）。 */
    public static final int CROSS_RECIPE_THREADS = 512;

    public HighFluxBreederReactorMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new FixedThreadRecipeLogic(this);
    }

    /**
     * 512 条跨配方线程为基础，并**叠加**线程修改仓（Ω 天球分歧引擎）的附加线程。
     *
     * <p>ADD 原实现 {@code getMultipleThreads() = 128 + getAdditionalThread()}，
     * 本机按用户指定改为 512 基准；保持叠加语义（项目规则：Ω 只叠加、不覆盖）。
     */
    private static final class FixedThreadRecipeLogic extends GTLAddMultipleRecipesLogic {
        private FixedThreadRecipeLogic(GTLAddWorkableElectricParallelHatchMultipleRecipesMachine machine) {
            super(machine);
        }

        @Override
        public int getMultipleThreads() {
            long total = (long) CROSS_RECIPE_THREADS + Math.max(0, getMachine().getAdditionalThread());
            return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, total));
        }
    }
}
