package com.gtl.enhancedcore.common.event;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Keeps the explicitly retained joint-factory mapping without reviving retired machines. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class RetiredFactoryMappings {
    private RetiredFactoryMappings() {}

    @SubscribeEvent
    public static void remap(MissingMappingsEvent event) {
        var factory = GTLEnhancedcoreMachines.getIntegratedUniversalFactory();
        for (var mapping : event.getMappings(Registries.ITEM, GTLEnhancedcore.MOD_ID)) {
            if (factory != null && mapping.getKey().getPath().equals("integrated_universal_factory")) {
                mapping.remap(factory.getItem());
            }
        }
        for (var mapping : event.getMappings(Registries.BLOCK, GTLEnhancedcore.MOD_ID)) {
            if (factory != null && mapping.getKey().getPath().equals("integrated_universal_factory")) {
                mapping.remap(factory.getBlock());
            }
        }
        for (var mapping : event.getMappings(Registries.BLOCK_ENTITY_TYPE, GTLEnhancedcore.MOD_ID)) {
            if (factory != null && mapping.getKey().getPath().equals("integrated_universal_factory")) {
                mapping.remap(factory.getBlockEntityType());
            }
        }
    }
}
