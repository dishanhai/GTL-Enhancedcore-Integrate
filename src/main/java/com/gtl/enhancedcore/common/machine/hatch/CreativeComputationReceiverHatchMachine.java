package com.gtl.enhancedcore.common.machine.hatch;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableComputationContainer;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;
import org.gtlcore.gtlcore.api.machine.computation.ComputationConnections;
import org.gtlcore.gtlcore.api.machine.computation.ComputationMath;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNetwork;
import org.gtlcore.gtlcore.api.machine.computation.ComputationNode;
import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * A computation reception hatch that supplies computation without an optical pipe.
 * GTLCore's long computation scheduler owns spending, refunds and research progress.
 */
public class CreativeComputationReceiverHatchMachine extends OpticalComputationHatchMachine {

    /** 固定值。 */
    public static final int MAX_CWU_PER_TICK = CreativeComputationPolicy.MAX_CWU_PER_TICK;

    public CreativeComputationReceiverHatchMachine(IMachineBlockEntity holder) {
        super(holder, false);
    }

    @Override
    protected NotifiableComputationContainer createComputationContainer(Object... args) {
        // The parent calls this while its constructor runs, before subclass fields are initialized.
        return new CreativeComputationContainer(this);
    }

    private static final class CreativeComputationContainer extends NotifiableComputationContainer
            implements ComputationNode {

        private final ComputationSource source;

        private CreativeComputationContainer(CreativeComputationReceiverHatchMachine machine) {
            super(machine, IO.IN, false);
            source = new CreativeComputationSource(() -> ComputationNetwork.tick(machine.getLevel()),
                    CreativeComputationPolicy::maxCwuPerTick, this::gtlcore$computationOnline);
        }

        @Override
        public List<IOpticalComputationProvider> gtlcore$computationLinks() {
            return List.of();
        }

        @Override
        public List<ComputationSource> gtlcore$localComputationSources() {
            return List.of(source);
        }

        @Override
        public boolean gtlcore$computationOnline() {
            return machine instanceof IMultiPart part && part.isFormed()
                    && ComputationConnections.loaded(machine);
        }

        @Override
        public int requestCWUt(int cwut, boolean simulate,
                               @NotNull Collection<IOpticalComputationProvider> seen) {
            seen.add(this);
            return ComputationNetwork.request(this, cwut, simulate);
        }

        @Override
        public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
            seen.add(this);
            return ComputationMath.toInt(source.gtlcore$computationCapacity());
        }

        @Override
        public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
            seen.add(this);
            return source.gtlcore$canBridgeComputation();
        }

        @Override
        public IOpticalComputationProvider getComputationProvider() {
            return this;
        }

    }
}
