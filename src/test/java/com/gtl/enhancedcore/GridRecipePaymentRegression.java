package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.GridRecipePayment;
import com.gtl.enhancedcore.common.recipe.GridEnergyInputs;
import com.gtl.enhancedcore.common.recipe.GridEnergyRecipeInput;
import java.math.BigInteger;
import java.util.List;

final class GridRecipePaymentRegression {
    private static int assertions;

    static int run() {
        var grid = new Grid(new BigInteger("1000000000000000000000000000000"));
        long screenshotEUt = 4_323_455_640_262_410_240L;
        long localBuffer = 2_305_847_407_260_205_056L;
        check(screenshotEUt > localBuffer, "Reported super-parallel demand exceeds the hatch cache");
        BigInteger before = grid.balance;
        check(grid.pay(List.of(screenshotEUt), true), "A funded grid admits the reported recipe above cache and VA limits");
        check(grid.balance.equals(before) && grid.transfers == 0, "Admission does not withdraw EU");
        check(grid.pay(List.of(screenshotEUt), false), "The reported recipe really pays from the grid");
        check(grid.balance.equals(before.subtract(BigInteger.valueOf(screenshotEUt))) && grid.transfers == 1,
                "A super-parallel tick pays the exact full amount once");

        grid = new Grid(BigInteger.valueOf(9));
        check(!grid.pay(List.of(10L), true) && !grid.pay(List.of(10L), false), "Insufficient grid EU blocks simulation and execution");
        check(grid.balance.equals(BigInteger.valueOf(9)) && grid.transfers == 0, "A refused batch does not make a partial withdrawal");
        check(grid.pay(List.of(9L), false) && grid.balance.signum() == 0, "An exact balance pays successfully");
        check(!grid.pay(List.of(1L), false), "An exhausted grid cannot reuse a full local hatch buffer");

        grid = new Grid(BigInteger.valueOf(10));
        grid.rejectTransfer = true;
        check(grid.pay(List.of(10L), true), "Simulation sees sufficient energy before a debit race");
        check(!grid.pay(List.of(10L), false), "A failed actual debit is a failed recipe payment");
        check(grid.balance.equals(BigInteger.TEN) && grid.transfers == 1, "A failed debit cannot mutate the balance or retry against the cache");

        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2));
        check(GridRecipePayment.availableAsLong(large) == Long.MAX_VALUE, "A large grid balance saturates scheduler EU without wrapping");
        check(GridRecipePayment.availableAsLong(BigInteger.valueOf(-1)) == 0, "A negative ledger value cannot grant scheduler energy");
        grid = new Grid(large);
        check(grid.pay(List.of(Long.MAX_VALUE, Long.MAX_VALUE), true), "Multiple EU contents are summed beyond signed long without wrapping");
        check(grid.pay(List.of(Long.MAX_VALUE, Long.MAX_VALUE), false) && grid.balance.signum() == 0,
                "A large multi-content tick is charged in BigInteger exactly once");
        check(grid.pay(List.of(), false) && grid.pay(List.of(0L), false) && grid.transfers == 1,
                "Zero demand does not call the transfer API");

        grid = new Grid(before);
        var otherGrid = new Grid(BigInteger.TEN);
        check(GridEnergyInputs.remove(null, grid, screenshotEUt) == screenshotEUt,
                "An isolated order pays the reported amount without a local energy container");
        check(grid.balance.equals(before.subtract(BigInteger.valueOf(screenshotEUt))), "Isolated payment uses the same exact grid ledger");
        check(GridEnergyInputs.refund(null, grid, screenshotEUt) == screenshotEUt && grid.balance.equals(before),
                "An aborted isolated tick refunds its captured grid source");
        check(otherGrid.balance.equals(BigInteger.TEN), "The refund does not change another grid");
        grid.rejectTransfer = true;
        check(GridEnergyInputs.remove(null, grid, screenshotEUt) == 0 && grid.balance.equals(before),
                "Failed isolated payment grants no energy or progress");
        return assertions;
    }

    private static final class Grid implements GridEnergyRecipeInput {
        private BigInteger balance;
        private int transfers;
        private boolean rejectTransfer;

        private Grid(BigInteger balance) { this.balance = balance; }

        @Override public boolean gridEnergyConnected() { return true; }
        @Override public BigInteger gridEnergyAvailable() { return balance; }
        @Override public boolean gridEnergyTransfer(BigInteger delta) {
            transfers++;
            if (rejectTransfer || balance.add(delta).signum() < 0) return false;
            balance = balance.add(delta);
            return true;
        }

        private boolean pay(List<Long> amounts, boolean simulate) {
            return GridRecipePayment.pay(amounts, simulate, this::gridEnergyAvailable, this::gridEnergyTransfer);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
