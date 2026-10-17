package com.gtl.enhancedcore.integration.ae2.remote;

public final class RemoteProviderRange {
    public static final double MAX_DISTANCE = 20000.0;
    private RemoteProviderRange() {}

    public static boolean allows(boolean sameLevel, double distanceSquared) {
        return sameLevel && Double.isFinite(distanceSquared) && distanceSquared >= 0
                && distanceSquared <= MAX_DISTANCE * MAX_DISTANCE;
    }
}
