package com.gtl.enhancedcore.common.data.machine;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gtl.enhancedcore.common.recipe.ExtremeReactorFuel;
import com.gtl.enhancedcore.common.recipe.ExtremeReactorValues;
import java.util.function.Consumer;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses the advanced hyper reactor's fuel units and duration with the requested fixed wireless output. */
public final class ExtremeReactionRecipeLoader {
    private ExtremeReactionRecipeLoader() {}

    /** Register the fixed fuel cycle used by the reactor. */
    public static void registerRecipes(Consumer<FinishedRecipe> provider) {
        var fuel = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(ExtremeReactorFuel.FLUID_ID));
        if (fuel == null || fuel == Fluids.EMPTY) {
            throw new IllegalStateException("Missing extreme reaction fuel " + ExtremeReactorFuel.FLUID_ID);
        }
        GTLEnhancedcoreRecipeTypes.EXTREME_REACTION
                .recipeBuilder(GTLEnhancedcore.id("extreme_reaction/concentration_mixing_hyper_fuel_2"))
                .inputFluids(FluidIngredient.of(ExtremeReactorValues.fuelAmountMb(), fuel))
                .duration(ExtremeReactorValues.durationTicks())
                .EUt(-ExtremeReactorValues.generationEut())
                .save(provider);
    }
}
