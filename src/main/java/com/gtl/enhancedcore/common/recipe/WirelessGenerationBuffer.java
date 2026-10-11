package com.gtl.enhancedcore.common.recipe;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Predicate;

/** Generated EU awaiting a successful grid deposit; simulations never change the balance. */
public final class WirelessGenerationBuffer {
    private BigInteger pendingEU = BigInteger.ZERO;

    /** Accept an entire output request, including totals larger than a long. */
    public boolean accept(List<Long> outputs, boolean simulate) {
        BigInteger generated = BigInteger.ZERO;
        for (Long output : outputs) {
            if (output == null || output < 0) return false;
            generated = generated.add(BigInteger.valueOf(output));
        }
        if (!simulate) pendingEU = pendingEU.add(generated);
        return true;
    }

    /** Only clear the amount accepted by the grid. A failed deposit leaves it available for retry. */
    public boolean flush(Predicate<BigInteger> deposit) {
        BigInteger amount = pendingEU;
        if (amount.signum() == 0 || !deposit.test(amount)) return false;
        pendingEU = pendingEU.subtract(amount);
        return true;
    }

    public BigInteger pendingEU() {
        return pendingEU;
    }

    public String save() {
        return pendingEU.toString();
    }

    public void load(String saved) {
        BigInteger restored = saved.isEmpty() ? BigInteger.ZERO : new BigInteger(saved);
        if (restored.signum() < 0) throw new IllegalArgumentException("Negative generated energy");
        pendingEU = restored;
    }
}
