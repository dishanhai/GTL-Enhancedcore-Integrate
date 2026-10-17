package com.gtl.enhancedcore.common.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Applies only pre-reviewed native cells to the caller's already copied predicate arrays. */
public final class NativeHiddenStructureCells {
    private static final String RESOURCE = "/data/gtl_enhancedcore/structures/hollow/native.json";
    private static final JsonObject MACHINES = readManifest();
    private static final TraceabilityPredicate REMOVED_CELL = Predicates.any();

    private NativeHiddenStructureCells() {}

    private static JsonObject readManifest() {
        try (var input = NativeHiddenStructureCells.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Missing native hollow manifest");
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("machines");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** No world edits or runtime geometry search; repeated application to any() is harmless. */
    public static void apply(ResourceLocation id, BlockPattern original, TraceabilityPredicate[][][] matches) {
        if (id == null || !MACHINES.has(id.toString())) return;
        JsonObject spec = MACHINES.getAsJsonObject(id.toString());
        if (spec.getAsJsonArray("cells").isEmpty()) return;
        var size = spec.getAsJsonArray("size");
        int width = size.get(0).getAsInt(), height = size.get(1).getAsInt(), depth = size.get(2).getAsInt();
        if (matches.length != depth || original.aisleRepetitions.length != depth)
            throw changed(id, "depth/repetitions");
        for (int z = 0; z < depth; z++) {
            if (matches[z].length != height || original.aisleRepetitions[z][0] != 1
                    || original.aisleRepetitions[z][1] != 1) throw changed(id, "height/repetitions");
            for (var row : matches[z]) if (row.length != width) throw changed(id, "width");
        }
        // Validate every target before mutating any cell, so failure leaves this copy unchanged.
        for (var element : spec.getAsJsonArray("cells")) {
            var cell = element.getAsJsonObject();
            var pos = cell.getAsJsonArray("position");
            int x = pos.get(0).getAsInt(), y = pos.get(1).getAsInt(), z = pos.get(2).getAsInt();
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth)
                throw changed(id, "cell bounds");
            var predicate = matches[z][y][x];
            if (predicate == null || predicate.isController) throw changed(id, "controller/null cell");
            if (predicate == REMOVED_CELL) continue;
            if (predicate.isAny()) throw changed(id, "target predicate unexpectedly any");
            if (predicate.common.size() != 1 || !predicate.limited.isEmpty())
                throw changed(id, "plain fixed-block predicate shape");
            Set<String> expected = new HashSet<>();
            for (var block : cell.getAsJsonArray("expectedBlocks")) expected.add(block.getAsString());
            Set<String> actual = new HashSet<>();
            for (var simple : Stream.concat(predicate.common.stream(), predicate.limited.stream()).toList()) {
                if (simple.candidates == null) throw changed(id, "missing candidate supplier");
                var candidates = simple.candidates.get();
                if (candidates == null || candidates.length == 0) throw changed(id, "empty candidate supplier");
                for (var candidate : candidates) {
                    if (candidate == null || candidate.hasBlockEntity()) throw changed(id, "non-solid candidate");
                    var blockId = ForgeRegistries.BLOCKS.getKey(candidate.getBlockState().getBlock());
                    if (blockId == null) throw changed(id, "unregistered candidate");
                    actual.add(blockId.toString());
                }
            }
            if (!actual.equals(expected)) throw changed(id, "candidate blocks " + actual + " != " + expected);
        }
        for (var element : spec.getAsJsonArray("cells")) {
            var pos = element.getAsJsonObject().getAsJsonArray("position");
            matches[pos.get(2).getAsInt()][pos.get(1).getAsInt()][pos.get(0).getAsInt()] = REMOVED_CELL;
        }
    }

    private static IllegalStateException changed(ResourceLocation id, String detail) {
        return new IllegalStateException("Native structure changed upstream; review hidden cells for " + id + ": " + detail);
    }
}
