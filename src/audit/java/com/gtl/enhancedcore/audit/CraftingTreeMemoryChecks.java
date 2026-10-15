package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.gtl.enhancedcore.client.crafting.CraftingTreeBudget;
import com.gtl.enhancedcore.client.crafting.CraftingTreeBuildControl;
import com.neuvillette.ae2ct.api.CraftingTreeHelper;
import com.neuvillette.ae2ct.api.RecipeHelper;
import com.neuvillette.ae2ct.gui.CraftingTreeWidget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in checks in an isolated client; never included in the production JAR. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class CraftingTreeMemoryChecks {
    private static boolean done;
    private CraftingTreeMemoryChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.craftingTreeAudit")
                || event.phase != TickEvent.Phase.END || Minecraft.getInstance().level == null) return;
        done = true;
        Thread thread = new Thread(CraftingTreeMemoryChecks::run, "CraftingTreeMemoryAudit");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        String result;
        try {
            Fixture ordinary = fixture(3, false);
            CraftingTreeHelper.NodeManager tree = ordinary.helper.build(false);
            require(tree != null && count(tree.root) == 7, "ordinary shared tree preserves seven nodes");
            require(tree.map.size() >= 7, "ordinary compact layout retained");
            require(((CraftingTreeBuildControl)ordinary.helper).enhancedcore$budget().reason() == null, "ordinary build below budget");

            Fixture huge = fixture(24, false);
            expect(huge.helper, CraftingTreeBudget.Reason.NODES);
            require(((CraftingTreeBuildControl)huge.helper).enhancedcore$budget().nodes() == CraftingTreeBudget.MAX_NODES,
                    "expanding shared DAG stops at exact node ceiling");
            Fixture deep = fixture(140, true);
            expect(deep.helper, CraftingTreeBudget.Reason.DEPTH);

            // A null screen is safe here: the worker must not dereference/capture a UI to build its snapshot.
            Fixture async = fixture(24, false);
            CraftingTreeWidget widget = new CraftingTreeWidget(null, async.data, async.entries, false);
            com.gtl.enhancedcore.client.crafting.CraftingTreeWidgetControl control =
                    (com.gtl.enhancedcore.client.crafting.CraftingTreeWidgetControl)widget;
            control.enhancedcore$releaseTree();
            require(control.enhancedcore$released(), "closing releases widget");
            Fixture small = fixture(3, false);
            control.enhancedcore$rebuildTree(small.data, small.entries, false);
            var field = CraftingTreeWidget.class.getDeclaredField("future"); field.setAccessible(true);
            var future = (CompletableFuture<?>)field.get(widget);
            require(future.get(10, java.util.concurrent.TimeUnit.SECONDS) != null, "return from settings rebuilds display");
            control.enhancedcore$releaseTree();
            require(field.get(widget) instanceof CompletableFuture<?> released && released.isDone() && released.join() == null,
                    "closing drops completed result");
            result = "{\"passed\":true,\"ordinaryNodes\":7,\"boundedNodes\":" + CraftingTreeBudget.MAX_NODES
                    + ",\"depthLimit\":" + CraftingTreeBudget.MAX_DEPTH + ",\"runtimeMixinApplied\":true}";
        } catch (Throwable failure) {
            failure.printStackTrace();
            result = "{\"passed\":false,\"error\":\"" + failure.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        }
        try {
            Path path = Path.of(System.getProperty("gtl.enhancedcore.craftingTreeAuditOutput", "crafting-tree-memory-checks.json"));
            Files.writeString(path, result, StandardCharsets.UTF_8);
        } catch (Exception failure) { failure.printStackTrace(); }
    }

    private static void expect(CraftingTreeHelper helper, CraftingTreeBudget.Reason reason) {
        try { helper.build(false); throw new AssertionError("expected " + reason); }
        catch (CraftingTreeBudget.Stopped stopped) { require(stopped.reason() == reason, "expected " + reason + " got " + stopped.reason()); }
    }
    private static int count(CraftingTreeHelper.Node node) {
        int total = 1;
        for (CraftingTreeHelper.Node child : node.subNodes) total += count(child);
        return total;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static Fixture fixture(int levels, boolean linear) {
        List<AEKey> keys = new ArrayList<>();
        List<RecipeHelper.Recipe> recipes = new ArrayList<>();
        List<CraftingPlanSummaryEntry> entries = new ArrayList<>();
        for (int i = 0; i < levels; i++) {
            CompoundTag tag = new CompoundTag(); tag.putInt("display_tree_fixture", i);
            AEKey key = AEItemKey.of(Items.STONE, tag); keys.add(key);
            entries.add(new CraftingPlanSummaryEntry(key, 0, 0, 1));
        }
        for (int i = 0; i < levels - 1; i++) {
            GenericStack child = new GenericStack(keys.get(i + 1), 1);
            recipes.add(new RecipeHelper.Recipe(linear ? List.of(child) : List.of(child, child),
                    List.of(new GenericStack(keys.get(i), 1)), 1L));
        }
        RecipeHelper data = new RecipeHelper(new GenericStack(keys.get(0), 1), recipes);
        return new Fixture(data, entries, new CraftingTreeHelper(data, entries));
    }
    private record Fixture(RecipeHelper data, List<CraftingPlanSummaryEntry> entries, CraftingTreeHelper helper) {}
}
