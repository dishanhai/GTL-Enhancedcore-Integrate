package com.gtl.enhancedcore.integration.ae2.remote;

/** Identity-bound lease between one source menu and the next menu that consumes its remote host. */
public final class RemoteMenuLease<T> {
    private final T source;
    private final long deadline;
    private T opened;
    private boolean claimed;

    public RemoteMenuLease(T source, long deadline) { this.source = source; this.deadline = deadline; }

    public boolean claim(T current, T inventory, long now) {
        if (opened != null) return current == opened;
        if (now > deadline || current != source && current != inventory) return false;
        claimed = true;
        return true;
    }

    public boolean observe(T current, T inventory, long now) {
        if (opened != null) return current == opened;
        if (now > deadline) return false;
        if (current == source || current == inventory) return true;
        if (!claimed) return false;
        opened = current;
        return true;
    }
}
