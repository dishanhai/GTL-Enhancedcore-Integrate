package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Pins the real ADD supplier, furnace checks and recipe-chain ABI required by IV integration. */
final class FuxiIsolationUpstreamRegression {
    private static int assertions;
    private static final String ADD = "com/gtladd/gtladditions/";

    static int run() throws IOException {
        var dependencies = JsonParser.parseString(Files.readString(Path.of("gradle/local-dependencies.json")))
                .getAsJsonObject();
        String name = dependencies.entrySet().stream().flatMap(entry -> entry.getValue().getAsJsonArray().asList().stream())
                .map(element -> element.getAsString()).filter(value -> value.startsWith("gtladditions-"))
                .findFirst().orElseThrow();
        try (ZipFile jar = new ZipFile(Path.of("libs", name).toFile())) {
            var machines = read(jar, ADD + "common/machine/multiblock/MultiBlockMachine");
            var supplier = method(machines, "_init_$lambda$41");
            check(calls(supplier, ADD + "api/machine/multiblock/GTLAddCoilWorkableElectricParallelHatchMultipleRecipesMachine", "<init>"),
                    "Fuxi retains the upstream ADD coil/parallel-hatch machine supplier");
            var pattern = method(machines, "_init_$lambda$42");
            check(calls(pattern, "com/gregtechceu/gtceu/api/pattern/Predicates", "heatingCoils"),
                    "upstream furnace shape validates uniform registered heating coils");
            Set<String> abilities = fields(pattern, "com/gregtechceu/gtceu/api/machine/multiblock/PartAbility");
            check(abilities.containsAll(Set.of("IMPORT_ITEMS", "EXPORT_ITEMS", "IMPORT_FLUIDS", "EXPORT_FLUIDS",
                            "INPUT_LASER", "PARALLEL_HATCH")),
                    "upstream material ports expose all four material capabilities without replacing power limits");
            check(calls(pattern, ADD + "api/machine/GTLAddPartAbility", "getTHREAD_MODIFIER"),
                    "upstream furnace already permits the Omega modifier");

            var registration = method(machines, "<clinit>");
            boolean inFuxi = false;
            Set<String> types = new HashSet<>();
            boolean hatchModifier = false;
            for (var instruction : registration.instructions) {
                if (instruction instanceof LdcInsnNode constant
                        && "fuxi_bagua_heaven_forging_furnace".equals(constant.cst)) inFuxi = true;
                if (!inFuxi) continue;
                if (instruction instanceof FieldInsnNode field) {
                    if (field.name.equals("FUXI_BAGUA_HEAVEN_FORGING_FURNACE")) break;
                    if (field.name.equals("MOLECULAR_DECONSTRUCTION") || field.name.equals("ULTIMATE_MATERIAL_FORGE_RECIPES"))
                        types.add(field.name);
                    if (field.owner.equals("com/gregtechceu/gtceu/common/data/GTRecipeModifiers")
                            && field.name.equals("PARALLEL_HATCH")) hatchModifier = true;
                }
                if (instruction instanceof MethodInsnNode call && Set.of("getSTELLAR_LGNITION", "getCHAOTIC_ALCHEMY").contains(call.name))
                    types.add(call.name.substring(3));
            }
            check(types.equals(Set.of("STELLAR_LGNITION", "CHAOTIC_ALCHEMY", "MOLECULAR_DECONSTRUCTION", "ULTIMATE_MATERIAL_FORGE_RECIPES")),
                    "Fuxi keeps all four native recipe types for cross-type isolation");
            check(hatchModifier, "Fuxi native modifier chain remains PARALLEL_HATCH");

            var coil = read(jar, ADD + "api/machine/multiblock/GTLAddCoilWorkableElectricMultipleRecipesMultiblockMachine");
            check(calls(method(coil, "createRecipeLogic"), ADD + "api/machine/EBFChecks", "getEBF_CHECK"),
                    "the original ADD coil machine supplies the EBF temperature callback");
            var logic = read(jar, ADD + "api/machine/logic/GTLAddMultipleRecipesLogic");
            check(method(logic, "getRecipeCheck").desc.equals("()Ljava/util/function/BiPredicate;"),
                    "IV invoker matches the current ADD protected callback signature");
        }
        return assertions;
    }

    private static ClassNode read(ZipFile jar, String name) throws IOException {
        var entry = jar.getEntry(name + ".class");
        if (entry == null) throw new IOException("Missing ADD class: " + name);
        try (var input = jar.getInputStream(entry)) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }

    private static Set<String> fields(MethodNode method, String owner) {
        Set<String> result = new HashSet<>();
        for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals(owner)) result.add(field.name);
        return result;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
