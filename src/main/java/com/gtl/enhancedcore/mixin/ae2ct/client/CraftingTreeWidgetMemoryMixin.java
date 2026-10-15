package com.gtl.enhancedcore.mixin.ae2ct.client;

import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.gtl.enhancedcore.client.crafting.CraftingTreeBudget;
import com.gtl.enhancedcore.client.crafting.CraftingTreeBuildControl;
import com.gtl.enhancedcore.client.crafting.CraftingTreeJobs;
import com.gtl.enhancedcore.client.crafting.CraftingTreeWidgetControl;
import com.neuvillette.ae2ct.api.CraftingTreeHelper;
import com.neuvillette.ae2ct.api.RecipeHelper;
import com.neuvillette.ae2ct.api.ScreenshotHelper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.neuvillette.ae2ct.gui.CraftingTreeWidget", remap = false)
public abstract class CraftingTreeWidgetMemoryMixin implements CraftingTreeWidgetControl {
    @Shadow private RecipeHelper data;
    @Shadow private CraftingTreeHelper helper;
    @Shadow private CompletableFuture<CraftingTreeHelper.NodeManager> future;
    @Shadow private CraftingTreeHelper.NodeManager _nodeManager;
    @Shadow private CraftingTreeHelper.Node selectedNode;
    @Shadow protected boolean isMissingOnly;
    @Unique private CraftingTreeJobs.Job<CraftingTreeHelper.NodeManager> enhancedcore$job;
    @Unique private CraftingTreeBudget enhancedcore$treeBudget;
    @Unique private boolean enhancedcore$released;

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"),
            remap = false, require = 1, allow = 1)
    private CompletableFuture<CraftingTreeHelper.NodeManager> enhancedcore$singleBuild(Supplier<?> ignored,
            appeng.client.gui.AEBaseScreen<?> screen, RecipeHelper recipe, List<CraftingPlanSummaryEntry> entries,
            boolean missingOnly) {
        return enhancedcore$submit(helper, missingOnly);
    }

    @Unique private CompletableFuture<CraftingTreeHelper.NodeManager> enhancedcore$submit(
            CraftingTreeHelper buildHelper, boolean missingOnly) {
        CraftingTreeBuildControl control = (CraftingTreeBuildControl)buildHelper;
        enhancedcore$treeBudget = control.enhancedcore$budget();
        enhancedcore$job = CraftingTreeJobs.submit(() -> buildHelper.build(missingOnly),
                enhancedcore$treeBudget::cancel, control::enhancedcore$clearBuildInputs);
        enhancedcore$released = false;
        return enhancedcore$job.future();
    }

    @Override public void enhancedcore$releaseTree() {
        enhancedcore$released = true;
        if (enhancedcore$job != null) enhancedcore$job.cancel();
        enhancedcore$job = null;
        // Cancel does not drop the result of an already completed CompletableFuture.
        if (future != null) future.obtrudeValue(null);
        future = CompletableFuture.completedFuture(null);
        _nodeManager = null;
        selectedNode = null;
        helper = null;
        data = null;
    }

    @Override public boolean enhancedcore$released() { return enhancedcore$released; }
    @Override public boolean enhancedcore$missingOnly() { return isMissingOnly; }

    @Override public void enhancedcore$rebuildTree(RecipeHelper recipe, List<CraftingPlanSummaryEntry> entries,
            boolean missingOnly) {
        enhancedcore$releaseTree();
        data = recipe;
        isMissingOnly = missingOnly;
        helper = new CraftingTreeHelper(recipe, entries);
        future = enhancedcore$submit(helper, missingOnly);
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false, require = 1, allow = 1)
    private void enhancedcore$drawStopMessage(GuiGraphics graphics, int offsetX, int offsetY,
            int mouseX, int mouseY, CallbackInfo ci) {
        if (enhancedcore$released) { ci.cancel(); return; }
        // Upstream returns after pushPose while building, leaking one pose per rendered frame.
        if (!future.isDone()) { ci.cancel(); return; }
        CraftingTreeBudget.Reason reason = enhancedcore$treeBudget.reason();
        if (reason != null) {
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("gtl_enhancedcore.crafting_tree.stopped"), 20, 35, 0xffe080, false);
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("gtl_enhancedcore.crafting_tree.reason." + reason.name().toLowerCase(java.util.Locale.ROOT)),
                    20, 50, 0xffffff, false);
            ci.cancel();
        }
    }

    @Inject(method = {"screenShot", "keyPressed"}, at = @At("HEAD"), cancellable = true,
            remap = false, require = 2, allow = 2)
    private void enhancedcore$skipStoppedActions(CallbackInfo ci) {
        if (enhancedcore$released || enhancedcore$treeBudget.reason() != null) ci.cancel();
    }

    @Redirect(method = "screenShot", at = @At(value = "INVOKE",
            target = "Lcom/neuvillette/ae2ct/api/ScreenshotHelper;Screenshot(Lcom/neuvillette/ae2ct/api/CraftingTreeHelper$NodeManager;Lnet/minecraft/world/entity/player/Player;)V"),
            remap = false, require = 1, allow = 1)
    private void enhancedcore$boundScreenshot(CraftingTreeHelper.NodeManager manager,
            net.minecraft.world.entity.player.Player player) {
        if (CraftingTreeBudget.screenshotFits(manager.max_x, manager.max_y)) {
            ScreenshotHelper.Screenshot(manager, player);
        } else {
            player.displayClientMessage(Component.translatable("gtl_enhancedcore.crafting_tree.screenshot_too_large"), false);
        }
    }
}
