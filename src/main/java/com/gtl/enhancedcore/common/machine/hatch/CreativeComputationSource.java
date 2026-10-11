package com.gtl.enhancedcore.common.machine.hatch;

import org.gtlcore.gtlcore.api.machine.computation.ComputationLedger;
import org.gtlcore.gtlcore.api.machine.computation.ComputationSource;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** A physical source shared by every wired, wireless, cloud and direct recipe route. */
public final class CreativeComputationSource implements ComputationSource {

    private final ComputationLedger ledger = new ComputationLedger();
    private final LongSupplier tick;
    private final LongSupplier capacity;
    private final BooleanSupplier online;

    public CreativeComputationSource(LongSupplier tick, LongSupplier capacity, BooleanSupplier online) {
        this.tick = Objects.requireNonNull(tick);
        this.capacity = Objects.requireNonNull(capacity);
        this.online = Objects.requireNonNull(online);
    }

    @Override
    public long gtlcore$computationCapacity() {
        return online.getAsBoolean() ? Math.max(0, capacity.getAsLong()) : 0;
    }

    @Override
    public long gtlcore$availableComputation() {
        ledger.advance(tick.getAsLong());
        return ledger.remaining(gtlcore$computationCapacity());
    }

    @Override
    public boolean gtlcore$canBridgeComputation() {
        return online.getAsBoolean();
    }

    @Override
    public Receipt gtlcore$withdrawComputation(long amount) {
        if (amount <= 0 || amount > gtlcore$availableComputation()) return null;
        ledger.debit(amount, 0);
        return new Receipt(amount, () -> ledger.refund(amount, 0));
    }
}
