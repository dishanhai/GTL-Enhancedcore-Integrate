package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;

/** 临界增殖反应堆结构（用户提供 high_flux_breeder_reactor.schem，宽 x 高 x 深：87 x 111 x 93）。 */
public final class HighFluxBreederReactorStructure {
    private HighFluxBreederReactorStructure() {}

    public static FactoryBlockPattern create() {
        return StructurePatterns.start(
                "/data/gtl_enhancedcore/structures/high_flux_breeder_reactor.pattern.gz");
    }
}
