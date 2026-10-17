package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Exact contracts against actual installed dependency bytecode; does not initialize Minecraft. */
final class RemoteProviderMixinRegression {
    private static final String MIXIN = "com/gtl/enhancedcore/mixin/";
    private static final String CI = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        try (ZipFile ae = jar("appliedenergistics2-forge-15.4.10.jar");
             ZipFile gt = jar("gtceu-1.20.1-1.4.4.jar");
             ZipFile ld = jar("ldlib-forge-1.20.1-1.0.33.b.jar")) {
            lookup(ae, "appeng/menu/locator/BlockEntityLocator", "locate",
                    "(Lnet/minecraft/world/entity/player/Player;Ljava/lang/Class;)Ljava/lang/Object;",
                    "net/minecraft/world/level/Level", "m_7702_", "ae2/client/RemoteProviderLocatorMixin");
            lookup(ae, "appeng/menu/locator/PartLocator", "locate",
                    "(Lnet/minecraft/world/entity/player/Player;Ljava/lang/Class;)Ljava/lang/Object;",
                    "appeng/api/parts/PartHelper", "getPart", "ae2/client/RemoteProviderPartLocatorMixin");
            lookup(gt, "com/gregtechceu/gtceu/api/gui/factory/MachineUIFactory", "readHolderFromSyncData",
                    "(Lnet/minecraft/network/FriendlyByteBuf;)Lcom/gregtechceu/gtceu/api/machine/MetaMachine;",
                    "net/minecraft/world/level/Level", "m_7702_", "gtceu/client/RemoteMachineUiHolderMixin");

            var provider = read(ae, "appeng/helpers/patternprovider/PatternProviderLogic");
            check(provider.fields.stream().filter(f -> f.name.equals("host")
                    && f.desc.equals("Lappeng/helpers/patternprovider/PatternProviderLogicHost;")).count() == 1,
                    "provider accessor resolves the actual host field");
            var accessor = own("ae2/remote/PatternProviderHostAccessor");
            target(accessor, "appeng/helpers/patternprovider/PatternProviderLogic");
            var getter = method(accessor, "enhancedcore$getHost", "()Lappeng/helpers/patternprovider/PatternProviderLogicHost;");
            check("host".equals(value(annotation(getter, "Lorg/spongepowered/asm/mixin/gen/Accessor;"), "value")),
                    "compiled accessor targets host");

            var aeBase = read(ae, "appeng/blockentity/AEBaseBlockEntity");
            check(calls(method(aeBase, "m_5995_", "()Lnet/minecraft/nbt/CompoundTag;"),
                    "appeng/blockentity/AEBaseBlockEntity", "writeToStream") == 1,
                    "AE update tag uses ordinary visual sync stream");
            check(calls(method(aeBase, "m_142466_", "(Lnet/minecraft/nbt/CompoundTag;)V"),
                    "appeng/blockentity/AEBaseBlockEntity", "readUpdateData") == 1,
                    "AE load accepts its own ordinary update tag");
            var ldm = read(ld, "com/lowdragmc/lowdraglib/core/mixins/BlockEntityMixin");
            check(calls(named(ldm, "injectGetUpdateTag"),
                    "com/lowdragmc/lowdraglib/networking/s2c/SPacketManagedPayload", "of") == 1,
                    "LDLib writes a complete initial managed payload into BE update tag");
            check(calls(named(ldm, "injectLoad"),
                    "com/lowdragmc/lowdraglib/networking/s2c/SPacketManagedPayload", "processPacket") == 1,
                    "LDLib reads managed payload through normal BE load");
            check(read(gt, "com/gregtechceu/gtceu/api/machine/IMachineBlockEntity").interfaces.contains(
                    "com/lowdragmc/lowdraglib/syncdata/blockentity/IAsyncAutoSyncBlockEntity"),
                    "GT holder participates in LDLib auto-sync");
            check(read(ld, "com/lowdragmc/lowdraglib/syncdata/blockentity/IAsyncAutoSyncBlockEntity").interfaces.contains(
                    "com/lowdragmc/lowdraglib/syncdata/blockentity/IAutoSyncBlockEntity"),
                    "GT async auto-sync inherits managed update-tag support");
        }

        optionalHook("extendedae_plus-1.5.4.1.jar",
                "com/extendedae_plus/network/crafting/CraftingMonitorOpenProviderC2SPacket$ProviderUIHelper",
                "openProviderUI", "(Lappeng/helpers/patternprovider/PatternProviderLogic;Lappeng/api/crafting/IPatternDetails;Lnet/minecraft/server/level/ServerPlayer;)V",
                "ae2/remote/PlusProviderRemoteOpenMixin", "enhancedcore$prepareRemoteProvider");
        optionalHook("extendedae_plus_gtladd-1.0.3.jar",
                "com/extendedae_plus_gtladd/network/CraftingMonitorOpenGTMProviderC2SPacket",
                "gtmOpenUI", "(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;Lnet/minecraft/server/level/ServerPlayer;Lappeng/api/crafting/IPatternDetails;)V",
                "ae2/remote/PlusGtmProviderRemoteOpenMixin", "enhancedcore$prepareRemoteAssembly");

        var json = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        for (String name : List.of("ae2.remote.PlusProviderRemoteOpenMixin", "ae2.remote.PlusGtmProviderRemoteOpenMixin",
                "ae2.remote.PatternProviderHostAccessor"))
            check(json.getAsJsonArray("mixins").asList().stream().anyMatch(v -> v.getAsString().equals(name)), "common registration " + name);
        for (String name : List.of("ae2.client.RemoteProviderLocatorMixin", "ae2.client.RemoteProviderPartLocatorMixin",
                "gtceu.client.RemoteMachineUiHolderMixin"))
            check(json.getAsJsonArray("client").asList().stream().anyMatch(v -> v.getAsString().equals(name)), "client registration " + name);
        return assertions;
    }

    private static void lookup(ZipFile jar, String target, String name, String desc, String owner,
                               String callName, String mixin) throws Exception {
        var original = method(read(jar, target), name, desc);
        check(calls(original, owner, callName) == 1, "exactly one lookup; default ordinal covers only expected call " + target);
        var hookClass = own(mixin);
        target(hookClass, target);
        var hook = hookClass.methods.stream().filter(m -> m.name.startsWith("enhancedcore$")).findFirst().orElseThrow();
        var redirect = annotation(hook, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
        check(Integer.valueOf(1).equals(value(redirect, "require")), "lookup redirect requires one match");
        @SuppressWarnings("unchecked") var selected = (List<String>) value(redirect, "method");
        check(selected.size() == 1 && (selected.getFirst().equals(name) || selected.getFirst().equals(name + desc)),
                "redirect selects actual target signature");
        var at = (AnnotationNode) value(redirect, "at");
        check("INVOKE".equals(value(at, "value")), "lookup redirect is at invocation");
        String invocation = (String) value(at, "target");
        check(invocation != null && invocation.startsWith("L" + owner + ";"), "redirect owner matches actual call owner");
        MethodInsnNode actual = null;
        for (var instruction : original.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(callName)) actual = call;
        check(invocation.replace(";getBlockEntity(", ";m_7702_(").equals("L" + actual.owner + ";" + actual.name + actual.desc),
                "compiled redirect's complete mapped invocation matches dependency bytecode");
        String handlerDescriptor = actual.getOpcode() == Opcodes.INVOKESTATIC ? actual.desc
                : "(L" + actual.owner + ";" + actual.desc.substring(1);
        check(hook.desc.equals(handlerDescriptor), "redirect handler receiver/parameters/return match actual invocation");
    }

    private static void optionalHook(String artifact, String target, String name, String desc,
                                      String mixin, String hookName) throws Exception {
        var candidates = new ArrayList<Path>();
        String configuredMods = System.getProperty("GTLE_AUDIT_MODS", System.getenv("GTLE_AUDIT_MODS"));
        if (configuredMods != null && !configuredMods.isBlank()) candidates.add(Path.of(configuredMods, artifact));
        candidates.add(Path.of("libs", artifact));
        Path found = candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
        var own = own(mixin);
        target(own, target);
        check(own.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;")),
                "optional Plus target is pseudo");
        var handler = method(own, hookName, desc.substring(0, desc.indexOf(')')) + CI + ")V");
        check((handler.access & Opcodes.ACC_STATIC) != 0, "static upstream hook uses static handler");
        var inject = annotation(handler, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        check(Boolean.TRUE.equals(value(inject, "cancellable")) && Integer.valueOf(1).equals(value(inject, "require")),
                "range/loaded-target guard is cancellable and mandatory");
        @SuppressWarnings("unchecked") var selected = (List<String>) value(inject, "method");
        check(selected.equals(List.of(name)), "server guard names actual method");
        @SuppressWarnings("unchecked") var at = (List<AnnotationNode>) value(inject, "at");
        check(at.size() == 1 && "HEAD".equals(value(at.getFirst(), "value")), "guard executes before opening remote UI");
        if (found == null) {
            System.out.println("Remote provider optional contract not exercised (artifact absent in GTLE_AUDIT_MODS/libs): " + artifact);
            return;
        }
        try (ZipFile jar = new ZipFile(found.toFile())) {
            var upstream = method(read(jar, target), name, desc);
            check((upstream.access & Opcodes.ACC_STATIC) != 0, "actual optional target method is static: " + artifact);
            check((upstream.access & Opcodes.ACC_PRIVATE) != 0, "actual optional helper is private: " + artifact);
        }
    }

    private static ZipFile jar(String name) throws Exception { return new ZipFile(Path.of("libs", name).toFile()); }
    private static ClassNode read(ZipFile jar, String name) throws Exception {
        try (InputStream input = jar.getInputStream(jar.getEntry(name + ".class"))) { return read(input); }
    }
    private static ClassNode own(String name) throws Exception {
        try (InputStream input = RemoteProviderMixinRegression.class.getClassLoader().getResourceAsStream(MIXIN + name + ".class")) {
            return read(input);
        }
    }
    private static ClassNode read(InputStream input) throws Exception {
        var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
    }
    private static MethodNode named(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static MethodNode method(ClassNode node, String name, String desc) {
        var matches = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).toList();
        check(matches.size() == 1, "method signature resolves exactly once: " + node.name + "." + name + desc);
        return matches.getFirst();
    }
    private static int calls(MethodNode method, String owner, String name) {
        int count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }
    private static AnnotationNode annotation(MethodNode node, String desc) {
        return node.visibleAnnotations.stream().filter(a -> a.desc.equals(desc)).findFirst().orElseThrow();
    }
    private static Object value(AnnotationNode node, String name) {
        for (int i = 0; i + 1 < node.values.size(); i += 2) if (name.equals(node.values.get(i))) return node.values.get(i + 1);
        return null;
    }
    private static void target(ClassNode node, String expected) {
        var mixin = node.invisibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        Object names = value(mixin, "targets"), types = value(mixin, "value");
        boolean found = names instanceof List<?> list && list.contains(expected.replace('/', '.'))
                || types instanceof List<?> typeList && typeList.stream().anyMatch(v -> v instanceof Type type && type.getInternalName().equals(expected));
        check(found, "compiled mixin declares actual upstream target " + expected);
        check(Boolean.FALSE.equals(value(mixin, "remap")), "mod target uses remap=false");
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
