package com.gtl.enhancedcore.common.data.machines.multiblock.PlasmaMachineToolStructure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gtl.enhancedcore.common.structure.StructurePatterns;

/** Historical 3x3x3 placeholder; registered IV machines use IVProcessingStructures. */
public final class PlasmaMachineToolStructure {
    private PlasmaMachineToolStructure() {}

    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/legacy/plasma_machine_tool_placeholder.pattern.gz");
    }
}
