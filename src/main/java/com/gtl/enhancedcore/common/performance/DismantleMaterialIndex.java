package com.gtl.enhancedcore.common.performance;

import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;

/** Conservative material filter for fix4's block-type based dismantle candidates. */
public final class DismantleMaterialIndex {
    public static final int WORK_PER_ADVANCE = 8192;
    private Builder<TraceabilityPredicate, SimplePredicate, BlockInfo, Block> builder;
    private Snapshot<Block> result;

    public DismantleMaterialIndex(TraceabilityPredicate[][][] matches) {
        // Candidate suppliers may depend on machine/world state. Never reuse their blocks across jobs.
        builder = new Builder<>(matches, new PatternAdapter());
    }

    /** Supplier calls cannot be preempted; all surrounding reference traversal is bounded. */
    public void advance() {
        if (result != null) return;
        builder.advance();
        if (builder.done()) {
            result = builder.snapshot();
            builder = null;
        }
    }

    public boolean done() { return result != null; }
    public boolean mayContain(Block block) { return result == null || result.mayContain(block); }

    private static final class PatternAdapter implements Adapter<TraceabilityPredicate, SimplePredicate, BlockInfo, Block> {
        public boolean any(TraceabilityPredicate predicate) { return predicate.isAny(); }
        public int commonSize(TraceabilityPredicate predicate) { return predicate.common == null ? 0 : predicate.common.size(); }
        public int limitedSize(TraceabilityPredicate predicate) { return predicate.limited == null ? 0 : predicate.limited.size(); }
        public SimplePredicate common(TraceabilityPredicate predicate, int i) { return predicate.common.get(i); }
        public SimplePredicate limited(TraceabilityPredicate predicate, int i) { return predicate.limited.get(i); }
        public Supplier<BlockInfo[]> candidates(SimplePredicate predicate) { return predicate.candidates; }
        public boolean airOnly(SimplePredicate predicate) { return predicate == SimplePredicate.AIR; }
        public Block block(BlockInfo candidate) { return candidate.getBlockState().getBlock(); }
    }

    /** Detached per-job value: never contains predicates, suppliers, controllers or worlds. */
    public record Snapshot<B>(Set<B> blocks, boolean unknown) {
        public Snapshot {
            Set<B> detached = Collections.newSetFromMap(new IdentityHashMap<>());
            detached.addAll(blocks);
            blocks = Collections.unmodifiableSet(detached);
        }
        public boolean mayContain(B block) { return unknown || blocks.contains(block); }
    }

    public interface Adapter<P, S, C, B> {
        boolean any(P predicate);
        int commonSize(P predicate);
        int limitedSize(P predicate);
        S common(P predicate, int index);
        S limited(P predicate, int index);
        Supplier<C[]> candidates(S predicate);
        default boolean airOnly(S predicate) { return false; }
        B block(C candidate);
    }

    /** Pure incremental engine, also exercised without bootstrapping Minecraft registries. */
    public static final class Builder<P, S, C, B> {
        private P[][][] matches;
        private Adapter<P, S, C, B> adapter;
        private final Set<P> predicates = identitySet();
        private final Set<S> simples = identitySet();
        private final Set<Supplier<C[]>> suppliers = identitySet();
        private final Set<B> blocks = identitySet();
        private int aisle, row, cell, simpleIndex, commonSize, limitedSize, candidateIndex;
        private P predicate;
        private C[] candidates;
        private boolean unknown;
        private Snapshot<B> result;

        public Builder(P[][][] matches, Adapter<P, S, C, B> adapter) {
            this.matches = matches;
            this.adapter = adapter;
        }

        public void advance() {
            for (int work = 0; work < WORK_PER_ADVANCE && result == null; work++) step();
        }
        public boolean done() { return result != null; }
        public Snapshot<B> snapshot() {
            if (!done()) throw new IllegalStateException("Material index is incomplete");
            return result;
        }

        private void step() {
            if (candidates != null) {
                if (candidateIndex < candidates.length) {
                    C candidate = candidates[candidateIndex++];
                    if (candidate == null) unknown = true;
                    else {
                        B block = adapter.block(candidate);
                        if (block == null) unknown = true;
                        else blocks.add(block);
                    }
                } else candidates = null;
                return;
            }
            if (predicate != null) {
                if (simpleIndex < commonSize + limitedSize) {
                    int index = simpleIndex++;
                    S simple = index < commonSize ? adapter.common(predicate, index)
                            : adapter.limited(predicate, index - commonSize);
                    if (simple == null) { unknown = true; return; }
                    if (!simples.add(simple)) return;
                    Supplier<C[]> supplier = adapter.candidates(simple);
                    if (supplier == null) { if (!adapter.airOnly(simple)) unknown = true; return; }
                    if (!suppliers.add(supplier)) return;
                    try { candidates = supplier.get(); }
                    catch (RuntimeException failure) { unknown = true; return; }
                    if (candidates == null || candidates.length == 0) {
                        // GTCEu's singleton AIR fallback only accepts air, which dismantling always excludes.
                        if (!adapter.airOnly(simple)) unknown = true;
                        candidates = null;
                    }
                    candidateIndex = 0;
                } else predicate = null;
                return;
            }
            if (matches == null || aisle >= matches.length) { finish(); return; }
            P[][] rows = matches[aisle];
            if (rows == null) { unknown = true; aisle++; row = cell = 0; return; }
            if (row >= rows.length) { aisle++; row = cell = 0; return; }
            P[] cells = rows[row];
            if (cells == null) { unknown = true; row++; cell = 0; return; }
            if (cell >= cells.length) { row++; cell = 0; return; }
            P next = cells[cell++];
            if (next == null) { unknown = true; return; }
            if (!predicates.add(next) || adapter.any(next)) return;
            predicate = next;
            simpleIndex = 0;
            commonSize = adapter.commonSize(next);
            limitedSize = adapter.limitedSize(next);
            // No simple predicates authorize no blocks in fix4; unknown applies to no-candidate simples.
        }

        private void finish() {
            result = new Snapshot<>(blocks, unknown);
            matches = null;
            adapter = null;
            predicate = null;
            candidates = null;
            predicates.clear();
            simples.clear();
            suppliers.clear();
            blocks.clear();
        }

        private static <T> Set<T> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }
    }
}
