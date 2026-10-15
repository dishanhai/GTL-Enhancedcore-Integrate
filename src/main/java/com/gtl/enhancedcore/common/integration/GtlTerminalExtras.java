package com.gtl.enhancedcore.common.integration;

import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Disables GTLCore's extra terminal controls while preserving other mods' upload features. */
public final class GtlTerminalExtras {
    public static final String MENU_MIXIN =
            "com.gtl.enhancedcore.mixin.ae2.PatternEncodingTerminalActionsMixin";
    public static final String CLIENT_MIXIN =
            "com.gtl.enhancedcore.mixin.ae2.client.PatternEncodingTerminalExtrasMixin";

    private static final String UPSTREAM = "org.gtlcore.gtlcore.mixin.ae2.gui.";
    private static final String MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String CI = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String CIR = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";
    private static final String GRAPHICS = "Lnet/minecraft/client/gui/GuiGraphics;";

    private GtlTerminalExtras() {}

    public static void postApply(String target, ClassNode node, String mixin) {
        if (MENU_MIXIN.equals(mixin)) {
            if (!"appeng.menu.me.items.PatternEncodingTermMenu".equals(target)) {
                throw new IllegalStateException("Unexpected pattern terminal menu target: " + target);
            }
            disable(node, "PatternEncodingTermMenuMixin", Map.of(
                    "gTLCore$modifyPatter", "(Ljava/lang/Integer;)V",
                    "gTLCore$quickUploadPattern", "()V",
                    "gTLCore$undoQuickUploadPattern", "()V",
                    "gTLCore$selectQuickUploadTarget", "(I)V"));
        } else if (CLIENT_MIXIN.equals(mixin)) {
            switch (target) {
                case "appeng.client.gui.AEBaseScreen" -> disable(node, "PatternEncodingTermScreenMixin", Map.of(
                        "gtlcore$initPatternQuickUploadButton", "(" + CI + ")V",
                        "gtlcore$updatePatternQuickUploadButton", "(" + CI + ")V",
                        "gtlcore$handlePatternQuickUploadClick", "(DDI" + CIR + ")V",
                        "gtlcore$handlePatternQuickUploadSelectionDrag", "(DDIDD" + CIR + ")V",
                        "gtlcore$handlePatternQuickUploadSelectionRelease", "(DDI" + CIR + ")V",
                        "gtlcore$handlePatternQuickUploadSelectionScroll", "(DDD" + CIR + ")V",
                        "gtlcore$renderPatternQuickUploadSelection", "(" + GRAPHICS + "IIF" + CI + ")V",
                        "gtlcore$suppressPatternQuickUploadBaseTooltips", "(" + GRAPHICS + "II" + CI + ")V"));
                case "appeng.client.gui.me.items.ProcessingEncodingPanel" -> disable(node, "ProcessingEncodingPanelMixin", Map.of(
                        "init", "(Lappeng/client/gui/me/items/PatternEncodingTermScreen;Lappeng/client/gui/WidgetContainer;" + CI + ")V",
                        "setVisibleHooks", "(Z" + CI + ")V"));
                case "net.minecraft.client.gui.screens.Screen" -> disable(node, "PatternQuickUploadScreenLifecycleMixin", Map.of(
                        "gtlcore$handlePatternQuickUploadSelectionKey", "(III" + CIR + ")V",
                        "gtlcore$closePatternQuickUploadSelection", "(" + CI + ")V"));
                default -> throw new IllegalStateException("Unexpected pattern terminal screen target: " + target);
            }
        }
    }

    private static void disable(ClassNode node, String upstreamMixin, Map<String, String> methods) {
        String owner = UPSTREAM + upstreamMixin;
        // Mixin renames injection handlers. Match their original suffix AND the recorded source,
        // never a broad substring that could disable AE2 or another mod's methods.
        for (var expected : methods.entrySet()) {
            MethodNode found = find(node, owner, expected.getKey(), expected.getValue());
            clear(found);
            found.instructions.add(new InsnNode(Opcodes.RETURN));
        }
    }

    private static MethodNode find(ClassNode node, String owner, String name, String descriptor) {
        MethodNode found = null;
        for (MethodNode method : node.methods) {
            if ((method.name.equals(name) || method.name.endsWith("$" + name))
                    && method.desc.equals(descriptor) && ownedBy(method, owner)) {
                if (found != null) throw new IllegalStateException("Ambiguous terminal hook: " + node.name + "." + name);
                found = method;
            }
        }
        if (found == null) throw new IllegalStateException("Terminal hook changed: " + node.name + "." + name + descriptor);
        return found;
    }

    private static void clear(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        method.maxStack = 0;
        method.maxLocals = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type argument : Type.getArgumentTypes(method.desc)) method.maxLocals += argument.getSize();
    }

    private static boolean ownedBy(MethodNode method, String mixin) {
        List<AnnotationNode> annotations = method.visibleAnnotations;
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (!MERGED.equals(annotation.desc) || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if ("mixin".equals(annotation.values.get(i)) && mixin.equals(annotation.values.get(i + 1))) return true;
            }
        }
        return false;
    }
}
