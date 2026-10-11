package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.machine.StarCoreStripperMachine;
import com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gtl.enhancedcore.common.util.TooltipPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/** Supplied geometry, UHV acquisition, port limits and shared scheduling at the requested 128 threads. */
public final class StarCoreStripperRegression {
    private static final Path JAVA = Path.of("src/main/java/com/gtl/enhancedcore");
    private static int assertions;

    public static int run() throws Exception {
        assertions = 0;
        geometry();
        acquisition();
        portsAndRegistration();
        scheduling();
        return assertions;
    }

    private static void geometry() throws Exception {
        var source = Path.of("SchemTool/input/star_core_stripper.schem");
        check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)))
                        .equals("aeb22dbc5ffcb25986c82c4536ee336b7d303f5559fe24894b24e9ceb9d85f41"),
                "The supplied original blueprint remains unchanged");
        var aisles = new ArrayList<String[]>();
        try (var input = Files.newInputStream(Path.of(
                "src/main/resources/data/gtl_enhancedcore/structures/star_core_stripper.pattern.gz"))) {
            StructureData.read(input).forEachAisle(aisles::add);
        }
        check(aisles.size() == 73 && aisles.stream().allMatch(rows -> rows.length == 123
                        && Arrays.stream(rows).allMatch(row -> row.length() == 123)),
                "The blueprint is 123 wide, 123 high and 73 deep");
        int controllers = 0, ports = 0;
        for (int z = 0; z < aisles.size(); z++) {
            for (int y = 0; y < aisles.get(z).length; y++) {
                var row = aisles.get(z)[y];
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) == 'C') {
                        controllers++;
                        check(x == 61 && y == 111 && z == 70, "Interior controller keeps its original height");
                    } else if (row.charAt(x) == 'X') {
                        ports++;
                        check(z == 70 && x >= 59 && x <= 63 && y >= 109 && y <= 113,
                                "Every port is inside the supplied controller panel");
                    }
                }
            }
        }
        check(controllers == 1 && ports == 24, "One controller and exactly 24 marked ports");
        for (int y = 109; y <= 113; y++) for (int x = 59; x <= 63; x++) {
            check(aisles.get(70)[y].charAt(x) == (x == 61 && y == 111 ? 'C' : 'X'),
                    "The 5x5 controller panel has no missing tile");
        }
    }

    private static void acquisition() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of(
                "src/main/resources/data/gtl_enhancedcore/equipment_recipes.json"))) {
            var recipe = EquipmentRecipeCatalog.read(reader).stream()
                    .filter(value -> value.output().equals("star_core_stripper")).findFirst().orElseThrow();
            check(recipe.tier() == EquipmentRecipeCatalog.TIERS.indexOf("uhv")
                            && recipe.method().equals("assembly_line"),
                    "Acquisition is gated at UHV on the assembly line");
            check(recipe.ingredients().stream().anyMatch(value ->
                            value.id().equals("gtceu:large_fragment_world_collection_machine") && value.count() == 4),
                    "The upgrade consumes four original large fragment collectors");
            check(recipe.ingredients().stream().anyMatch(value -> value.tag()
                            && value.id().equals("gtceu:circuits/uhv") && value.count() == 16),
                    "Sixteen UHV circuits gate the acquisition stage");
            check(recipe.duration() == 4800 && recipe.fluids().stream().anyMatch(value ->
                            value.id().equals("gtceu:soldering_alloy") && value.amount() == 18432),
                    "Acquisition retains the reviewed 240-second assembly cost");
        }
        var page = TooltipPolicy.page("star_core_stripper");
        for (String key : Set.of("star_core_stripper.cross_recipe", "star_core_stripper.capacity",
                "star_core_stripper.power_hatches")) {
            check(page.summary().contains("tooltip.gtl_enhancedcore." + key),
                    "The default tooltip exposes the ADD mode and hatch restrictions: " + key);
        }
        check(page.summary().stream().noneMatch(key -> key.contains("iv_")),
                "The star core tooltip does not claim isolated-order features");
    }

    private static void portsAndRegistration() throws Exception {
        String structures = source("common/data/machines/multiblock/GTLStructures.java");
        int from = structures.indexOf("public static BlockPattern starCoreStripper(");
        int to = structures.indexOf("public static BlockPattern", from + 1);
        String ports = structures.substring(from, to);
        var abilities = Pattern.compile("PartAbility\\.([A-Z_]+)").matcher(ports).results()
                .map(result -> result.group(1)).collect(java.util.stream.Collectors.toSet());
        check(abilities.equals(Set.of("MAINTENANCE", "INPUT_ENERGY", "INPUT_LASER", "PARALLEL_HATCH",
                        "IMPORT_ITEMS", "EXPORT_ITEMS", "IMPORT_FLUIDS", "EXPORT_FLUIDS")),
                "Only the requested machine and material abilities are accepted");
        for (var limit : java.util.Map.of("MAINTENANCE", 1, "INPUT_ENERGY", 2,
                "INPUT_LASER", 1, "PARALLEL_HATCH", 1).entrySet()) {
            check(Pattern.compile("PartAbility\\." + limit.getKey()
                    + "\\).*?setMaxGlobalLimited\\(" + limit.getValue() + "\\)").matcher(ports).find(),
                    "The independent global hatch limit is preserved: " + limit.getKey());
        }
        check(ports.contains(".where('X', block(\"gtlcore:iridium_casing\")")
                        && ports.contains("PartAbility.IMPORT_ITEMS") && ports.contains("PartAbility.EXPORT_ITEMS")
                        && ports.contains("PartAbility.IMPORT_FLUIDS") && ports.contains("PartAbility.EXPORT_FLUIDS"),
                "Unused ports use iridium casing and standard item/fluid IO abilities");
        check(!ports.contains("THREAD_MODIFIER") && !ports.contains("MUFFLER"),
                "Omega and muffler hatches are not accepted");
        String registration = source("common/registration/SpecialMachineRegistration.java");
        from = registration.indexOf("public static MultiblockMachineDefinition registerStarCoreStripper(");
        to = registration.indexOf("public static MultiblockMachineDefinition", from + 1);
        String definition = registration.substring(from, to);
        check(definition.contains("StarCoreStripperMachine::new")
                        && definition.contains("GTLRecipeTypes.FRAGMENT_WORLD_COLLECTION")
                        && definition.contains("GTRecipeModifiers.PARALLEL_HATCH"),
                "The correct recipe engine, fragment type and functional parallel-hatch modifier are registered");
        check(definition.contains("block/casings/iridium_casing")
                        && definition.contains("block/multiblock/gcym/large_extractor"),
                "The iridium shell uses the original fragment collector's real front overlay");
        check(!source("common/recipe/iv/IvBuffers.java").contains("\"gtl_enhancedcore:star_core_stripper\""),
                "The star core is not an isolated-order controller");
        String machine = source("common/machine/StarCoreStripperMachine.java");
        // The ADD engine keeps its fixed thread count and original processing rates.
        check(machine.contains("extends GTLAddWorkableElectricParallelHatchMultipleRecipesMachine")
                        && machine.contains("return CROSS_RECIPE_THREADS;")
                        && machine.contains("extends GTLAddMultipleRecipesLogic")
                        && machine.contains("logic.setReduction(ENERGY_MULTIPLIER,")
                        && machine.contains("DURATION_MULTIPLIER);"),
                "The ADD engine keeps fixed cross-recipe threads and upstream processing rates");
        check(StarCoreStripperMachine.ENERGY_MULTIPLIER == 256.0
                        && StarCoreStripperMachine.DURATION_MULTIPLIER == 0.25,
                "The upgrade preserves the original collector's energy and duration rates");
        check(StarCoreStripperMachine.CROSS_RECIPE_THREADS == 128,
                "The fixed cross-recipe thread count stays 128");
    }

    private static void scheduling() {
        check(sourceUnchecked("common/machine/StarCoreStripperMachine.java")
                        .contains("public int getMultipleThreads()"),
                "The ADD logic owns the cross-recipe thread limit");
    }

    private static String sourceUnchecked(String relative) {
        try {
            return source(relative);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static String source(String relative) throws Exception {
        return Files.readString(JAVA.resolve(relative));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
