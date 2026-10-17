package com.gtl.enhancedcore.common.util;

import java.util.List;

/** Per-scan orientation outcomes used to discard failed rotations after a valid match. */
public final class StructureDetectScanContext {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private StructureDetectScanContext() {}

    public static Scope begin() {
        Scope scope = new Scope(CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }

    public static void record(boolean matched) {
        Scope scope = CURRENT.get();
        if (scope != null && matched) scope.anyMatched = true;
    }

    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private boolean anyMatched;
        private boolean closed;

        private Scope(Scope previous) {
            this.previous = previous;
        }

        public <T> List<T> finish(List<T> failures) {
            return anyMatched ? List.of() : failures;
        }

        @Override
        public void close() {
            if (closed) return;
            if (CURRENT.get() != this) throw new IllegalStateException("Structure scan scopes closed out of order");
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
            closed = true;
        }
    }
}
