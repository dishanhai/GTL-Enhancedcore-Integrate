package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.block.LampBlock;
import com.gtl.enhancedcore.common.registration.MachineRegistrationSupport;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import net.minecraft.world.level.block.Block;

/** Supplied 41 x 41 x 41 blueprint; MIN_Z conversion (40-x, y, 40-z), C=(20,20,40). */
public final class ExtremeReactorStructure {
    private ExtremeReactorStructure() {}

    public static BlockPattern create(MultiblockMachineDefinition definition) {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/extreme_reactor.pattern.gz")
                .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                .where('X', block("gtlcore:supercritical_turbine_casing")
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1).setPreviewCount(1)))
                .where('A', block("gtlcore:uxv_hermetic_casing"))
                .where('B', block("gtlcore:ultimate_stellar_containment_casing"))
                .where('D', block("gtlcore:fusion_coil_mk2"))
                .where('E', block("gtlcore:supercritical_turbine_casing"))
                .where('F', block("gtlcore:iridium_casing"))
                .where('G', block("gtceu:tungstensteel_pipe_casing"))
                .where('I', block("kubejs:laser_cooling_casing"))
                .where('J', block("kubejs:force_field_glass"))
                .where('K', block("gtceu:white_lamp"))
                .where('L', block("gtceu:cyan_lamp"))
                .where('N', block("gtceu:orange_lamp"))
                .where(' ', Predicates.any())
                .build();
    }

    private static TraceabilityPredicate block(String id) {
        Block block = MachineRegistrationSupport.requiredBlockBlock(id);
        return block instanceof LampBlock ? LampPlacement.invertedStructureLamp(block) : Predicates.blocks(block);
    }
}
