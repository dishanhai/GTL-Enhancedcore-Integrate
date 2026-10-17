package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Actual dependency ABI: every opted-in controller must reach the canonical display callback. */
final class IvDisplayUpstreamRegression {
    private static final String BASE = "com/gregtechceu/gtceu/api/machine/multiblock/WorkableElectricMultiblockMachine";
    private static final String PANEL = "com/lowdragmc/lowdraglib/gui/widget/ComponentPanelWidget";
    private static final String ADD = "com/gtladd/gtladditions/";
    private static final String CORE = "org/gtlcore/gtlcore/common/data/machines/";
    private static final String UI_DESC = "()Lcom/lowdragmc/lowdraglib/gui/widget/Widget;";
    private static int assertions;

    static int run() throws IOException {
        assertions = 0;
        try (Dependencies dependencies = new Dependencies()) {
            panelCallbackContract(dependencies);
            var expected = new HashSet<String>();
            IvMachineScope.NATIVE_IDS.forEach(id -> expected.add("gtceu:" + id));
            IvMachineScope.FOREIGN_NATIVE_IDS.forEach((namespace, ids) ->
                    ids.forEach(id -> expected.add(namespace + ":" + id)));
            var suppliers = new HashMap<String, String>();
            for (String registry : List.of("MultiBlockMachineA", "MultiBlockMachineB",
                    "AdvancedMultiBlockMachineA", "AdditionalMultiBlockMachine"))
                collectSuppliers(dependencies, dependencies.read(CORE + registry), "gtceu", expected, suppliers);
            collectSuppliers(dependencies, dependencies.read(ADD + "common/machine/multiblock/MultiBlockMachine"),
                    "gtladditions", expected, suppliers);
            check(suppliers.keySet().equals(expected),
                    "every IV whitelist ID requires an audited real registration supplier; missing="
                            + difference(expected, suppliers.keySet()));
            for (var supplier : suppliers.entrySet())
                check(reachesBaseUi(dependencies, supplier.getValue(), new HashSet<>()),
                        supplier.getKey() + " must reach the canonical base UI callback: " + supplier.getValue());

            // ADD replaces some GTLCore factories after registration. Audit every actual mutable
            // supplier lambda as well, so a new override cannot silently bypass the shared panel.
            var mutable = dependencies.read(ADD + "common/modify/MutableMultiBlockModify");
            int mutableFactories = 0;
            for (var method : mutable.methods) {
                if (!method.name.startsWith("setOtherMutable$lambda$")
                        && !method.name.startsWith("setParallelHatchMutable$lambda$")) continue;
                if (!method.desc.contains("IMachineBlockEntity;")) continue;
                var supplier = allocatedMachine(dependencies, method);
                check(supplier != null, "ADD mutable supplier must expose an audited machine allocation: " + method.name);
                check(reachesBaseUi(dependencies, supplier, new HashSet<>()),
                        "ADD mutable factory must reach shared display callback: " + method.name + " -> " + supplier);
                mutableFactories++;
            }
            check(mutableFactories > 0, "actual ADD mutable supplier overrides were inspected");

            var addDisplay = method(dependencies.read(ADD + "api/machine/multiblock/GTLAddWorkableElectricMultipleRecipesMachine"),
                    "addDisplayText", "(Ljava/util/List;)V");
            check(!calls(addDisplay, BASE, "addDisplayText"),
                    "regression fixture exercises ADD's independent display override, not only base-super paths");
            check(calls(addDisplay, null, "addMachineModeDisplay"),
                    "ADD original recipe-mode row is generated before the final canonical callback");
        }
        return assertions;
    }

    private static void panelCallbackContract(Dependencies dependencies) throws IOException {
        var ui = method(dependencies.read(BASE), "createUIWidget", UI_DESC);
        int constructors = 0, suppliers = 0, virtualDisplayCallbacks = 0;
        for (var instruction : ui.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(PANEL)) {
                if (call.name.equals("<init>") && call.desc.equals("(IILjava/util/function/Consumer;)V")) constructors++;
                if (call.name.equals("textSupplier") && call.desc.equals(
                        "(Ljava/util/function/Consumer;)L" + PANEL + ";")) suppliers++;
            }
            if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                for (Object argument : dynamic.bsmArgs)
                    if (argument instanceof Handle handle && handle.getOwner().equals(BASE)
                            && handle.getName().equals("addDisplayText")
                            && handle.getDesc().equals("(Ljava/util/List;)V")
                            && handle.getTag() == Opcodes.H_INVOKEVIRTUAL) virtualDisplayCallbacks++;
            }
        }
        check(constructors == 1, "base UI contains one component-panel constructor Consumer to wrap");
        check(suppliers == 1, "base UI contains one textSupplier Consumer to wrap");
        check(virtualDisplayCallbacks == 2,
                "both panel consumers dynamically dispatch the real subclass addDisplayText");
    }

    private static void collectSuppliers(Dependencies dependencies, ClassNode registry, String namespace,
                                         Set<String> expected, Map<String, String> suppliers) throws IOException {
        for (var method : registry.methods) {
            String active = null;
            for (var instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode constant && constant.cst instanceof String path
                        && expected.contains(namespace + ":" + path)) active = namespace + ":" + path;
                if (active == null || !(instruction instanceof InvokeDynamicInsnNode dynamic)
                        || !dynamic.desc.endsWith("Ljava/util/function/Function;")) continue;
                for (Object argument : dynamic.bsmArgs) {
                    if (!(argument instanceof Handle handle)
                            || !handle.getDesc().contains("IMachineBlockEntity;")) continue;
                    String supplier = handle.getTag() == Opcodes.H_NEWINVOKESPECIAL ? handle.getOwner()
                            : allocatedMachine(dependencies, method(dependencies.read(handle.getOwner()),
                                    handle.getName(), handle.getDesc()));
                    check(supplier != null, "registration supplier must resolve: " + active);
                    check(suppliers.put(active, supplier) == null, "unique audited registration for " + active);
                    active = null;
                    break;
                }
            }
        }
    }

    private static String allocatedMachine(Dependencies dependencies, MethodNode method) throws IOException {
        for (var instruction : method.instructions)
            if (instruction instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW
                    && isMachine(dependencies, allocation.desc, new HashSet<>())) return allocation.desc;
        return null;
    }

    private static boolean isMachine(Dependencies dependencies, String name, Set<String> visiting) throws IOException {
        if (name == null || !visiting.add(name)) return false;
        if (name.equals(BASE)) return true;
        if (!name.startsWith("com/gregtechceu/") && !name.startsWith(ADD)
                && !name.startsWith("org/gtlcore/")) return false;
        return isMachine(dependencies, dependencies.read(name).superName, visiting);
    }

    private static boolean reachesBaseUi(Dependencies dependencies, String name, Set<String> visiting) throws IOException {
        if (name == null || !visiting.add(name)) return false;
        if (name.equals(BASE)) return true;
        var node = dependencies.read(name);
        var override = node.methods.stream().filter(m -> m.name.equals("createUIWidget") && m.desc.equals(UI_DESC))
                .findFirst().orElse(null);
        if (override == null) return reachesBaseUi(dependencies, node.superName, visiting);
        for (var instruction : override.instructions)
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.name.equals("createUIWidget") && call.desc.equals(UI_DESC)
                    && reachesBaseUi(dependencies, call.owner, visiting)) return true;
        return false;
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing upstream method " + node.name + "." + name + descriptor));
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && (owner == null || call.owner.equals(owner))
                    && call.name.equals(name)) return true;
        return false;
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        var missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }

    private static final class Dependencies implements AutoCloseable {
        private final List<ZipFile> jars = new ArrayList<>();
        private final Map<String, ClassNode> classes = new HashMap<>();

        Dependencies() throws IOException {
            var config = JsonParser.parseString(Files.readString(Path.of("gradle/local-dependencies.json"))).getAsJsonObject();
            for (var entry : config.entrySet()) for (var value : entry.getValue().getAsJsonArray()) {
                String name = value.getAsString();
                if (name.startsWith("gtceu-") || name.startsWith("gtlcore-") || name.startsWith("gtladditions-"))
                    jars.add(new ZipFile(Path.of("libs", name).toFile()));
            }
        }

        ClassNode read(String name) throws IOException {
            var cached = classes.get(name);
            if (cached != null) return cached;
            for (var jar : jars) {
                var entry = jar.getEntry(name + ".class");
                if (entry == null) continue;
                try (var input = jar.getInputStream(entry)) {
                    var node = new ClassNode();
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(name, node);
                    return node;
                }
            }
            throw new IOException("Missing audited dependency class: " + name);
        }

        @Override public void close() throws IOException {
            for (var jar : jars) jar.close();
        }
    }
}
