package com.gtl.enhancedcore.common.recipe.iv;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Selector identity belongs to the accepted pattern, not the buffer's fallback circuit. */
public final class IvPatternAdmission {
    private IvPatternAdmission() {}

    public static <T> boolean allowsType(Set<T> declared, T candidate) {
        return declared.isEmpty() || declared.contains(candidate);
    }

    public static <T> boolean compatibleTypes(Set<T> declared, Set<T> supported) {
        return declared.isEmpty() || declared.stream().anyMatch(supported::contains);
    }

    public static <K> Map<K, Long> circuits(Map<K, Long> available, Map<K, Long> declared,
                                           Predicate<K> circuit) {
        Map<K, Long> result = new LinkedHashMap<>();
        (declared.isEmpty() ? available : declared).forEach((key, amount) -> {
            if (amount > 0 && circuit.test(key)) result.put(key, 1L);
        });
        return result;
    }

    /** A virtual wrapper must carry exactly one item or fluid, and never a consumable supply. */
    public static <K> Map<K, Long> token(K item, K fluid, Predicate<K> circuit) {
        if ((item == null) == (fluid == null)) throw new IllegalArgumentException("虚拟原料必须只包含一种物品或流体");
        K payload = item == null ? fluid : item;
        return Map.of(payload, circuit.test(payload) ? 1L : Long.MAX_VALUE);
    }

    public static <K> Map<K, Long> virtualStock(Map<K, Long> available, Map<K, Long> accepted,
                                              Predicate<K> circuit) {
        Map<K, Long> result = new LinkedHashMap<>(available);
        if (accepted.keySet().stream().anyMatch(circuit)) result.keySet().removeIf(circuit);
        result.putAll(accepted);
        return result;
    }
}
