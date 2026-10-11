package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.gui.TagFilterStockBusPaging;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Page coverage and the upstream bytecode limits that the capacity Mixin must replace. */
final class TagFilterStockBusRegression {
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        check(TagFilterStockBusPaging.SLOT_COUNT == 160, "Requested total capacity is 160");
        check(TagFilterStockBusPaging.PAGE_COUNT == 10, "There are ten pages");
        int[] counts = new int[10];
        var positions = new HashSet<TagFilterStockBusPaging.SlotPosition>();
        for (int slot = 0; slot < 160; slot++) {
            var position = TagFilterStockBusPaging.position(slot);
            check(position.page() >= 0 && position.page() < 10, "Every slot belongs to a valid page");
            check(position.column() >= 0 && position.column() < 8
                    && position.row() >= 0 && position.row() < 2, "The original 2x8 layout is preserved");
            check(positions.add(position), "No page aliases another stocking position");
            counts[position.page()]++;
        }
        for (int count : counts) check(count == 16, "Every page contains sixteen distinct positions");
        check(TagFilterStockBusPaging.position(15).equals(new TagFilterStockBusPaging.SlotPosition(0, 7, 1)),
                "Last first-page slot stays at the lower-right corner");
        check(TagFilterStockBusPaging.position(16).equals(new TagFilterStockBusPaging.SlotPosition(1, 0, 0)),
                "Page two starts after slot sixteen");
        check(TagFilterStockBusPaging.position(159).equals(new TagFilterStockBusPaging.SlotPosition(9, 7, 1)),
                "The last slot is reachable on page ten");
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 160, Integer.MAX_VALUE}) {
            try {
                TagFilterStockBusPaging.position(invalid);
                throw new AssertionError("Invalid stocking position accepted: " + invalid);
            } catch (IndexOutOfBoundsException expected) {
                assertions++;
            }
        }
        for (int page : new int[]{Integer.MIN_VALUE, -1, 0})
            check(TagFilterStockBusPaging.clampPage(page) == 0, "Previous-page requests stop at the first page");
        for (int page = 0; page < 10; page++)
            check(TagFilterStockBusPaging.clampPage(page) == page, "Every real page is selectable");
        for (int page : new int[]{9, 10, Integer.MAX_VALUE})
            check(TagFilterStockBusPaging.clampPage(page) == 9, "Next-page requests stop at the last page");
        upstreamLimits();
        return assertions;
    }

    private static void upstreamLimits() throws Exception {
        try (var jar = new ZipFile(Path.of("libs/gtlcore-1.2.3.2-fix3.jar").toFile())) {
            var type = new ClassNode();
            try (var input = jar.getInputStream(jar.getEntry(
                    "org/gtlcore/gtlcore/common/machine/multiblock/part/TagFilterMEStockBusPartMachine.class"))) {
                new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            check(limitCount(method(type, "createInventory")) == 1,
                    "Exactly one real inventory allocation limit is replaced");
            check(limitCount(method(type, "refreshList")) == 2,
                    "Both the normal cutoff and quantity-sort cutoff are replaced");
            check(type.methods.stream().noneMatch(method -> method.name.equals("createUIWidget")),
                    "The tag bus still inherits the base UI that its Mixin overrides");
        }
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static int limitCount(MethodNode method) {
        int count = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof IntInsnNode value && value.getOpcode() == Opcodes.BIPUSH
                    && value.operand == 16) count++;
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
