package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Checks the actual ADD override: a hook on the parent cannot intercept this method. */
public final class IvBufferDispatchRegression {
    private static final String ADD = "com/gtladd/gtladditions/common/machine/multiblock/part/MESuperPatternBufferPartMachine";
    private static final String CORE = "org/gtlcore/gtlcore/common/machine/multiblock/part/ae/MEPatternBufferPartMachine";
    private static final String BUFFERS = "com/gtl/enhancedcore/common/recipe/iv/IvBuffers";
    private static final String DISPATCH = "pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z";
    private static int assertions;

    public static int run() throws IOException {
        var dependencies = JsonParser.parseString(Files.readString(Path.of("gradle/local-dependencies.json"))).getAsJsonObject();
        String addonJar = dependencies.entrySet().stream().flatMap(entry -> entry.getValue().getAsJsonArray().asList().stream())
                .map(element -> element.getAsString()).filter(name -> name.startsWith("gtladditions-")).findFirst().orElseThrow();
        ClassNode addon;
        try (ZipFile jar = new ZipFile(Path.of("libs", addonJar).toFile());
             InputStream source = jar.getInputStream(jar.getEntry(ADD + ".class"))) {
            addon = read(source);
        }
        check(addon.superName.equals(CORE), "ADD's buffer extends the hooked GTLCore buffer");
        var override = addon.methods.stream().filter(method -> (method.name + method.desc).equals(DISPATCH)).findFirst().orElseThrow();
        check((override.access & Opcodes.ACC_STATIC) == 0, "dispatch is a virtual instance method");
        check(calls(override, CORE + "Base", "pushPattern") == 0 && calls(override, CORE, "pushPattern") == 0,
                "the actual ADD override bypasses the parent dispatch hook");
        check(calls(override, CORE + "Base$InternalSlot", "pushPattern") == 1,
                "unintercepted dispatch would put the materials into upstream stock");

        ClassNode mixin;
        try (InputStream source = IvBufferDispatchRegression.class.getClassLoader().getResourceAsStream(
                "com/gtl/enhancedcore/mixin/gtlcore/IvSuperBufferMixin.class")) {
            mixin = read(source);
        }
        AnnotationNode target = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        if (target == null) target = annotation(mixin.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        @SuppressWarnings("unchecked") var targets = (List<Type>) value(target, "value");
        check(targets.stream().anyMatch(type -> type.getInternalName().equals(ADD)), "hook targets the overriding class");
        var registered = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json")))
                .getAsJsonObject().getAsJsonArray("mixins").asList();
        check(registered.stream().anyMatch(element -> element.getAsString().equals("gtlcore.IvSuperBufferMixin")),
                "the override hook is registered for both client and dedicated server");
        var hooks = mixin.methods.stream().filter(IvBufferDispatchRegression::dispatchHook).toList();
        check(hooks.size() == 1, "exactly one hook intercepts ADD's own pushPattern");
        var hook = hooks.getFirst();
        var inject = inject(hook);
        check(Boolean.TRUE.equals(value(inject, "cancellable")), "the native stock path can be cancelled");
        check(Integer.valueOf(1).equals(value(inject, "require")) && Integer.valueOf(1).equals(value(inject, "allow")),
                "changed upstream dispatch signatures fail loudly");
        @SuppressWarnings("unchecked") var points = (List<AnnotationNode>) value(inject, "at");
        check(points.size() == 1 && "HEAD".equals(value(points.getFirst(), "value")),
                "isolation admission runs before upstream can accept any materials");
        check(calls(hook, BUFFERS, "isolated") == 1, "ordinary and FOA operation retain the upstream path");
        check(calls(hook, BUFFERS, "push") == 1, "isolation dispatch uses the shared guarded order admission");
        check(calls(hook, "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", "setReturnValue") == 1,
                "AE receives the actual accept/reject result of the isolated order");
        check(calls(hook, CORE + "Base$InternalSlot", "pushPattern") == 0,
                "an isolated dispatch cannot also deposit into upstream stock");
        return assertions;
    }

    private static boolean dispatchHook(MethodNode method) {
        var annotation = inject(method);
        if (annotation == null) return false;
        @SuppressWarnings("unchecked") var methods = (List<String>) value(annotation, "method");
        return methods != null && methods.contains(DISPATCH);
    }

    private static AnnotationNode inject(MethodNode method) {
        var result = annotation(method.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        return result == null ? annotation(method.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;") : result;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations == null ? null : annotations.stream().filter(node -> node.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }

    private static long calls(MethodNode method, String owner, String name) {
        long count = 0;
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }

    private static ClassNode read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing class file for dispatch regression");
        var node = new ClassNode();
        new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
