package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.Map;
import java.util.Locale;

import com.gtl.enhancedcore.common.machine.*;

/** Machine builders, invoked only during the GTCEu registration event. */
public final class WirelessMachineRegistration {
    private WirelessMachineRegistration() {}

    private static final int[] RESONATOR_TIERS = {
            GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV, GTValues.IV,
            GTValues.LuV, GTValues.ZPM, GTValues.UV, GTValues.UHV, GTValues.UEV,
            GTValues.UIV, GTValues.UXV, GTValues.OpV, GTValues.MAX
    };

    private static final int[] CHARGER_TIERS = {
            GTValues.LV, GTValues.MV
    };

    public static MachineDefinition[] registerCrystalResonators() {
        MachineDefinition[] defs = new MachineDefinition[GTValues.V.length];
        for (int tier : RESONATOR_TIERS) {
            String tierLower = GTValues.VN[tier].toLowerCase();
            String tierName = GTValues.VN[tier];
            long generation = EndCrystalResonatorMachine.generationForTier(tier);
            long voltage = GTValues.V[tier];
            long amperage = EndCrystalResonatorMachine.amperageForTier(tier);
            long capacity = EndCrystalResonatorMachine.capacityForTier(tier);

            MachineDefinition def = GTLEnhancedcore.REGISTRATE
                    .machine(tierLower + "_crystal_resonator",
                            holder -> new EndCrystalResonatorMachine(holder, tier))
                    .langValue(tierName + "-末晶谐振器")
                    .rotationState(RotationState.NON_Y_AXIS)
                    .tier(tier)
                    .tooltipBuilder(MachineTooltips.create("crystal_resonator", Map.of(
                            "tooltip.gtl_enhancedcore.crystal_resonator.rate", new Object[]{generation},
                            "tooltip.gtl_enhancedcore.crystal_resonator.detail", new Object[]{voltage, amperage},
                            "tooltip.gtl_enhancedcore.crystal_resonator.buffer", new Object[]{capacity})))
                    .register();
            defs[tier] = def;
            GTLEnhancedcore.LOGGER.debug("Registered {}-crystal_resonator (id: {})", tierName, def.getId());
        }
        return defs;
    }

    public static MachineDefinition[] registerWirelessChargers() {
        MachineDefinition[] defs = new MachineDefinition[GTValues.V.length];
        for (int tier : CHARGER_TIERS) {
            String tierLower = GTValues.VN[tier].toLowerCase(Locale.ROOT);
            String tierName = GTValues.VN[tier];

            MachineDefinition def = GTLEnhancedcore.REGISTRATE
                    .machine(tierLower + "_wireless_charger",
                            holder -> new WirelessChargerMachine(holder, tier))
                    .langValue(tier == GTValues.LV ? "低压无线充能器" : "中压无线充能器")
                    .rotationState(RotationState.NON_Y_AXIS)
                    .tier(tier)
                    .renderer(() -> new com.gtl.enhancedcore.client.renderer.machine.WirelessChargerRenderer(tier))
                    .hasTESR(true)
                    .tooltipBuilder(MachineTooltips.create("wireless_charger", Map.of(
                            "tooltip.gtl_enhancedcore.wireless_charger.power", new Object[]{GTValues.VN[tier]},
                            "tooltip.gtl_enhancedcore.wireless_charger.scan", new Object[]{20})))
                    .register();
            defs[tier] = def;
            GTLEnhancedcore.LOGGER.debug("Registered {}-wireless_charger (id: {})", tierName, def.getId());
        }
        return defs;
    }
}
