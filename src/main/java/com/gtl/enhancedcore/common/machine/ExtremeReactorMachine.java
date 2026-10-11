package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtl.enhancedcore.common.recipe.ExtremeReactorValues;
import com.gtl.enhancedcore.common.recipe.WirelessGenerationBuffer;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** UXV fuel generator. Standard recipe progress supplies EU to its own wireless output handler. */
public class ExtremeReactorMachine extends WorkableElectricMultiblockMachine implements IMachineLife {
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            ExtremeReactorMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);
    private static final String PENDING_EU_KEY = "extremeReactorPendingEU";

    @Persisted
    @DescSynced
    @Nullable
    private UUID userId;
    @DescSynced
    private String pendingEU = "0";
    @DescSynced
    private long generationEUt;

    private final WirelessGenerationBuffer generatedEnergy = new WirelessGenerationBuffer();
    @Nullable
    private TickableSubscription gridSubscription;

    public ExtremeReactorMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        // WorkableMultiblockMachine includes controller-owned recipe traits when the structure forms.
        // No wired energy container or output hatch is required to match an EU tick output.
        new GridOutputHandler(this);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public int getTier() {
        return GTValues.UXV;
    }

    @Override
    public long getMaxVoltage() {
        return ExtremeReactorValues.generationEut();
    }

    @Override
    public long getOverclockVoltage() {
        return ExtremeReactorValues.generationEut();
    }

    @Override
    public void onMachinePlaced(@Nullable LivingEntity player, ItemStack stack) {
        if (player != null && !isRemote()) {
            userId = player.getUUID();
            markDirty();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) gridSubscription = subscribeServerTick(gridSubscription, this::flushGridEnergy);
    }

    @Override
    public void onUnload() {
        if (gridSubscription != null) {
            gridSubscription.unsubscribe();
            gridSubscription = null;
        }
        // Leave the persisted buffer intact: a chunk may already have been saved before this callback.
        super.onUnload();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.putString(PENDING_EU_KEY, generatedEnergy.save());
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        generatedEnergy.load(tag.getString(PENDING_EU_KEY));
        pendingEU = generatedEnergy.save();
    }

    private void flushGridEnergy() {
        // Run independently of recipe status so a stopped reactor can settle its last partial second.
        if (!recipeLogic.isWorking()) generationEUt = 0;
        if (userId == null || getOffsetTimer() % ExtremeReactorValues.gridFlushInterval() != 0) return;
        if (generatedEnergy.flush(amount -> WirelessEnergyManager.addEUToGlobalEnergyMap(userId, amount, this))) {
            pendingEU = generatedEnergy.save();
            markDirty();
        }
    }

    public BigInteger getPendingEU() {
        return new BigInteger(pendingEU);
    }

    public long getGenerationEUt() {
        return recipeLogic.isWorking() ? generationEUt : 0;
    }

    @Nullable
    public UUID getUserId() {
        return userId;
    }

    // GTLCore injects these methods into the base machine. The configured fuel cycle must not be batched.
    public boolean supportsBatchProcessing() { return false; }
    public boolean canConfigureBatchProcessing() { return false; }
    public boolean isBatchEnabled() { return false; }

    @Override
    public boolean dampingWhenWaiting() {
        return false;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        // RecipeLogic.lastRecipe is persisted but not description-synced; clients use the actual EU output field.
        textList.add(Component.translatable("tooltip.gtl_enhancedcore.extreme_reactor.generation", format(getGenerationEUt()))
                .withStyle(ChatFormatting.GOLD));
        textList.add(Component.translatable("tooltip.gtl_enhancedcore.extreme_reactor.wireless"));
        if (getPendingEU().signum() > 0) {
            textList.add(Component.translatable("gtl_enhancedcore.extreme_reactor.pending", format(getPendingEU())));
        }
        if (userId == null) {
            textList.add(Component.translatable("gtl_enhancedcore.extreme_reactor.unbound")
                    .withStyle(ChatFormatting.RED));
        }
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(recipeLogic.isWorkingEnabled(), recipeLogic.isActive())
                .addMachineModeLine(getRecipeType())
                .addWorkingStatusLine()
                .addProgressLine(recipeLogic.getProgressPercent());
        getDefinition().getAdditionalDisplay().accept(this, textList);
        getParts().forEach(part -> part.addMultiText(textList));
    }

    private static String format(Number number) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(number);
    }

    /** Only committed EU outputs from a running recipe enter the persisted generation buffer. */
    private static final class GridOutputHandler extends NotifiableRecipeHandlerTrait<Long> {
        private final ExtremeReactorMachine reactor;

        private GridOutputHandler(ExtremeReactorMachine reactor) {
            super(reactor);
            this.reactor = reactor;
        }

        @Override
        public List<Long> handleRecipeInner(IO io, GTRecipe recipe, List<Long> left,
                                           @Nullable String slotName, boolean simulate) {
            if (io != IO.OUT || reactor.isRemote() || reactor.userId == null
                    || !reactor.isRecipeLogicAvailable()) return left;
            if (!reactor.generatedEnergy.accept(left, simulate)) return left;
            if (!simulate) {
                reactor.pendingEU = reactor.generatedEnergy.save();
                reactor.generationEUt = RecipeHelper.getOutputEUt(recipe);
                reactor.markDirty();
            }
            return null;
        }

        @Override
        public RecipeCapability<Long> getCapability() { return EURecipeCapability.CAP; }

        @Override
        public IO getHandlerIO() { return IO.OUT; }

        @Override
        public List<Object> getContents() { return List.of(); }

        @Override
        public double getTotalContentAmount() { return 0; }
    }
}
