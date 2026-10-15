package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import java.math.BigInteger;
import java.util.List;

/** Connects controllers which pay EU directly to the same grid source used by recipe handlers. */
public final class GridEnergyInputs {
    private GridEnergyInputs() {}

    public static GridEnergyRecipeInput find(WorkableElectricMultiblockMachine machine) {
        GridEnergyRecipeInput best = null;
        BigInteger available = BigInteger.ZERO;
        for (var direction : List.of(IO.IN, IO.BOTH)) {
            var inputs = machine.getCapabilitiesProxy().get(direction, EURecipeCapability.CAP);
            if (inputs != null) for (var input : inputs)
                if (input instanceof GridEnergyRecipeInput grid && grid.gridEnergyConnected()) {
                    BigInteger candidate = grid.gridEnergyAvailable();
                    if (best == null || candidate.compareTo(available) > 0) {
                        best = grid;
                        available = candidate;
                    }
                }
        }
        return best;
    }

    public static long stored(WorkableElectricMultiblockMachine machine) {
        var grid = find(machine);
        if (grid != null) return GridRecipePayment.availableAsLong(grid.gridEnergyAvailable());
        var energy = machine.getEnergyContainer();
        return energy == null ? 0 : Math.max(0, energy.getEnergyStored());
    }

    public static long power(IEnergyContainer energy) {
        if (energy instanceof GridEnergyRecipeInput grid && grid.gridEnergyConnected()) return Long.MAX_VALUE;
        return RecipePowerBudget.power(energy.getInputVoltage(), energy.getInputAmperage());
    }

    public static long remove(IEnergyContainer energy, GridEnergyRecipeInput grid, long amount) {
        if (amount == 0) return 0;
        return grid == null ? energy.removeEnergy(amount) : grid.gridEnergyPay(amount) ? amount : 0;
    }

    /** Refund to the captured payment source, even if another hatch now advertises more energy. */
    public static long refund(IEnergyContainer energy, GridEnergyRecipeInput grid, long amount) {
        return grid == null ? energy.addEnergy(amount)
                : grid.gridEnergyTransfer(BigInteger.valueOf(amount)) ? amount : 0;
    }
}
