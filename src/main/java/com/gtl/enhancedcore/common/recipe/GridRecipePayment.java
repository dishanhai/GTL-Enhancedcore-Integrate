package com.gtl.enhancedcore.common.recipe;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** A grid recipe pays its full EU request once; simulation never changes the balance. */
public final class GridRecipePayment {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private GridRecipePayment() {}

    public static boolean pay(List<Long> amounts, boolean simulate, Supplier<BigInteger> balance,
                              Predicate<BigInteger> transfer) {
        BigInteger required = BigInteger.ZERO;
        for (long amount : amounts) {
            if (amount < 0) throw new IllegalArgumentException("Negative grid recipe input");
            required = required.add(BigInteger.valueOf(amount));
        }
        return pay(required, simulate, balance, transfer);
    }

    public static boolean pay(BigInteger required, boolean simulate, Supplier<BigInteger> balance,
                              Predicate<BigInteger> transfer) {
        if (required.signum() < 0) throw new IllegalArgumentException("Negative grid recipe input");
        if (required.signum() == 0) return true;
        if (balance.get().compareTo(required) < 0) return false;
        return simulate || transfer.test(required.negate());
    }

    /** Physical-EU schedulers accept long values; the grid ledger itself stays arbitrary precision. */
    public static long availableAsLong(BigInteger balance) {
        return balance.max(BigInteger.ZERO).min(LONG_MAX).longValueExact();
    }
}
