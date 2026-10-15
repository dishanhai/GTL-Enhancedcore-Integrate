package com.gtl.enhancedcore.common.integration;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** JER 1.4.0.247 suppliers need a world only while creating a display entity. */
public final class JerMobWorldLaziness {
    public static final String MARKER_MIXIN = "com.gtl.enhancedcore.mixin.jer.client.JerMobWorldMemoryMixin";
    private static final String TARGET = "jeresources/util/MobTableBuilder";
    private static final String LEVEL = "Lnet/minecraft/world/level/Level;";

    private JerMobWorldLaziness() {}

    public static void postApply(String target, ClassNode node, String mixin) {
        if (!MARKER_MIXIN.equals(mixin)) return;
        if (!TARGET.replace('/', '.').equals(target)) {
            throw new IllegalStateException("Unexpected JER world-memory target: " + target);
        }
        int reads = 0;
        int writes = 0;
        int constructorWrites = 0;
        int addReads = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof FieldInsnNode field) || !TARGET.equals(field.owner)
                        || !"level".equals(field.name) || !LEVEL.equals(field.desc)) continue;
                InsnList replacement = new InsnList();
                if (field.getOpcode() == Opcodes.GETFIELD) {
                    // Keep supplier/entity/colour handling intact, including ATL's merged suppliers.
                    replacement.add(new InsnNode(Opcodes.POP));
                    replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "jeresources/compatibility/CompatBase", "getLevel", "()" + LEVEL, false));
                    reads++;
                    if ("add".equals(method.name)) addReads++;
                } else if (field.getOpcode() == Opcodes.PUTFIELD) {
                    // Both references occupy one slot; discard the transient receiver and world.
                    // The unused field stays null even when ATL updates it on a level transition.
                    replacement.add(new InsnNode(Opcodes.POP2));
                    writes++;
                    if ("<init>".equals(method.name)) constructorWrites++;
                } else {
                    throw new IllegalStateException("Unexpected JER level field opcode: " + field.getOpcode());
                }
                method.instructions.insertBefore(field, replacement);
                method.instructions.remove(field);
            }
        }
        // Vanilla JER: constructor + three reads. ATL adds its update and two supplier reads.
        if (constructorWrites != 1 || addReads != 1
                || !((reads == 3 && writes == 1) || (reads == 5 && writes == 2))) {
            throw new IllegalStateException("JER world field contract changed: reads=" + reads
                    + ", writes=" + writes + ", constructorWrites=" + constructorWrites + ", addReads=" + addReads);
        }
    }
}
