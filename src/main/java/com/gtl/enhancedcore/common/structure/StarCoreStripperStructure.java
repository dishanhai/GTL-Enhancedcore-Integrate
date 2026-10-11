package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

/** Imported star_core_stripper.schem geometry; width x height x depth: 123 x 123 x 73. */
public final class StarCoreStripperStructure {
    private StarCoreStripperStructure() {}

    public static FactoryBlockPattern create() {
        return StructurePatterns.start("/data/gtl_enhancedcore/structures/star_core_stripper.pattern.gz");
    }
}
