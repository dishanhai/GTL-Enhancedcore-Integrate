package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import org.gtlcore.gtlcore.api.machine.computation.ComputationAmounts;
import org.gtlcore.gtlcore.api.machine.computation.ComputationProgress;

/** Routes only the active native order's research progress; GTLCore still owns all receipts. */
public final class IvComputationContext implements AutoCloseable {
    private static final ThreadLocal<IvComputationContext> CURRENT = new ThreadLocal<>();
    private final IvComputationContext previous;
    private final IvJob job;
    private final RecipeLogic logic;
    private final GTRecipe recipe;
    private final int oldProgress;

    private IvComputationContext(IvJob job, RecipeLogic logic, GTRecipe recipe) {
        this.previous = CURRENT.get();
        this.job = job;
        this.logic = logic;
        this.recipe = recipe;
        this.oldProgress = logic.getProgress();
        CURRENT.set(this);
    }

    public static IvComputationContext begin(IvJob job, RecipeLogic logic, GTRecipe recipe) {
        return new IvComputationContext(job, logic, recipe);
    }

    public static long completed(ComputationProgress progress, GTRecipe recipe) {
        var context = current(progress, recipe);
        return context == null ? progress.gtlcore$completedComputation(recipe)
                : context.job.nativeComputation.completed(ComputationAmounts.total(recipe));
    }

    public static void advance(ComputationProgress progress, GTRecipe recipe, long amount) {
        var context = current(progress, recipe);
        if (context == null) {
            progress.gtlcore$advanceComputation(recipe, amount);
            return;
        }
        long total = ComputationAmounts.total(recipe);
        context.job.nativeComputation.advance(total, amount);
    }

    private static IvComputationContext current(ComputationProgress progress, GTRecipe recipe) {
        var context = CURRENT.get();
        return context != null && (Object) context.logic == progress && context.recipe == recipe
                && recipe.data.getBoolean("duration_is_total_cwu") ? context : null;
    }

    @Override
    public void close() {
        // The machine's progress is a GUI projection, never a second order's research ledger.
        logic.setProgress(oldProgress);
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
