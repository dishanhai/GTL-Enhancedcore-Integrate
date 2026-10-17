package com.gtl.enhancedcore.common.machine.hatch;

/** Server-thread cell writes coalesce until a tick or an inventory persistence boundary. */
public final class DeferredCellSave {
    private final Runnable writer;
    private boolean dirty;
    private boolean flushing;

    public DeferredCellSave(Runnable writer) {
        this.writer = writer;
    }

    public void markDirty() {
        dirty = true;
    }

    public void flush() {
        if (!dirty || flushing) return;
        dirty = false;
        flushing = true;
        try {
            writer.run();
        } catch (RuntimeException | Error failure) {
            dirty = true;
            throw failure;
        } finally {
            flushing = false;
        }
    }
}
