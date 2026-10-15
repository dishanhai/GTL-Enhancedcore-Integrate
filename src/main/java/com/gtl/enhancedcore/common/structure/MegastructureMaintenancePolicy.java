package com.gtl.enhancedcore.common.structure;

/** Only these two machines gain an optional maintenance port, with custom or upstream geometry. */
public final class MegastructureMaintenancePolicy {
    private MegastructureMaintenancePolicy() {}

    public static boolean matches(String namespace, String path) {
        return "gtceu".equals(namespace) && ("qft".equals(path) || "gravitation_shockburst".equals(path));
    }
}
