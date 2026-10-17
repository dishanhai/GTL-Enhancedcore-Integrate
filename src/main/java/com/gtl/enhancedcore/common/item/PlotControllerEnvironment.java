package com.gtl.enhancedcore.common.item;

/** Display-only client context supplied by the current server; it never authorizes an action. */
public final class PlotControllerEnvironment {
    private static volatile Boolean dedicated;
    private PlotControllerEnvironment() {}
    public static void accept(boolean isDedicated) { dedicated = isDedicated; }
    public static void clear() { dedicated = null; }
    public static Boolean dedicated() { return dedicated; }
}
