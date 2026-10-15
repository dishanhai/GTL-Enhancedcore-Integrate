package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.HydrothermalLeachingComplexMachine;
import com.gtl.enhancedcore.common.structure.HydrothermalLeachingComplexStructure;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;

import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.requiredBlockBlock;

public final class HydrothermalLeachingRegistration {
    private HydrothermalLeachingRegistration() {}

    public static GTRecipeType[] recipeTypes() {
        return new GTRecipeType[]{GTLRecipeTypes.DIGESTION_TREATMENT, GTLRecipeTypes.DISSOLUTION_TREATMENT};
    }

    public static MultiblockMachineDefinition register() {
        var types = recipeTypes();
        return GTLEnhancedcore.REGISTRATE
                .multiblock("hydrothermal_leaching_complex", HydrothermalLeachingComplexMachine::new)
                .langValue("热压浸解联合体")
                .tier(GTValues.UEV)
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtlcore:iridium_casing"))
                .recipeTypes(types)
                .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH,
                        GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .pattern(HydrothermalLeachingComplexStructure::create)
                .tooltipBuilder(MachineTooltips.create("hydrothermal_leaching_complex", Map.of(
                        "tooltip.gtl_enhancedcore.hydrothermal_leaching_complex.threads",
                        new Object[]{HydrothermalLeachingComplexMachine.CROSS_RECIPE_THREADS}), types))
                .workableCasingRenderer(new ResourceLocation("gtlcore", "block/casings/iridium_casing"),
                        GTLEnhancedcore.id("block/multiblock/hydrothermal_leaching_complex"))
                .register();
    }
}
