package com.gtl.enhancedcore.common.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferRegistry;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates;

import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.*;

/** 109 x 73 x 145; only the 24 diamond-ore marks are hatch slots. */
public final class HydrothermalLeachingComplexStructure {
    private static final String ROOT = "/data/gtl_enhancedcore/structures/hydrothermal_leaching_complex";
    private static final JsonObject BINDINGS = readBindings();

    private HydrothermalLeachingComplexStructure() {}

    private static JsonObject readBindings() {
        try (var input = HydrothermalLeachingComplexStructure.class.getResourceAsStream(ROOT + ".json")) {
            if (input == null) throw new IOException("Missing hydrothermal structure manifest");
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("bindings");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    public static BlockPattern create(MultiblockMachineDefinition definition) {
        var builder = StructurePatterns.start(ROOT + ".pattern.gz")
                .where('C', Predicates.controller(Predicates.blocks(definition.get())))
                .where('X', requiredBlock("gtlcore:iridium_casing")
                        .or(Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.INPUT_LASER).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(Predicates.abilities(PartAbility.PARALLEL_HATCH).setMaxGlobalLimited(1).setPreviewCount(1))
                        .or(IvBufferRegistry.materialPorts(-1)))
                .where(' ', Predicates.any());
        for (var entry : BINDINGS.entrySet()) {
            var binding = entry.getValue().getAsJsonObject();
            if (!binding.get("mode").getAsString().equals("literal")) continue;
            builder.where(entry.getKey().charAt(0), literal(binding));
        }
        return builder.build();
    }

    private static TraceabilityPredicate literal(JsonObject binding) {
        var block = requiredBlockBlock(binding.get("block").getAsString());
        BlockState state = block.defaultBlockState();
        for (var entry : binding.getAsJsonObject("properties").entrySet()) {
            var property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IllegalStateException("Unknown structure property: " + entry.getKey());
            state = withProperty(state, property, entry.getValue().getAsString());
        }
        BlockState preview = state;
        // Runtime active/powered may change. Lamp placement retains the reviewed item NBT.
        return new TraceabilityPredicate(world -> world.getBlockState().is(block),
                StableBlockCandidates.mark(() -> new BlockInfo[]{LampPlacement.info(preview)}));
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow(
                () -> new IllegalStateException("Invalid structure property " + property.getName() + "=" + value)));
    }
}
