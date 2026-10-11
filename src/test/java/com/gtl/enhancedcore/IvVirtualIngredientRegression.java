package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.IvPatternAdmission;
import com.gtl.enhancedcore.common.recipe.iv.LongAllocation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Exercises circuit selection and allocation together, including unmarked AE processing patterns. */
public final class IvVirtualIngredientRegression {
    private static final Predicate<String> CIRCUIT = key -> key.startsWith("circuit:");
    private static int assertions;

    public static int run() {
        check(IvPatternAdmission.compatibleTypes(Set.of(), Set.of("laser", "assembler")),
                "an ordinary AE pattern without upload metadata is published");
        check(IvPatternAdmission.allowsType(Set.of(), "assembler"),
                "unmarked virtual-circuit patterns can search the controller's supported types");
        check(IvPatternAdmission.compatibleTypes(Set.of("laser", "assembler"), Set.of("assembler")),
                "a supported declared type is retained");
        check(!IvPatternAdmission.compatibleTypes(Set.of("laser"), Set.of("assembler")),
                "marked patterns cannot bypass the machine's type boundary");
        check(!IvPatternAdmission.allowsType(Set.of("laser"), "assembler"),
                "type metadata still constrains admission");

        for (int configuration : new int[]{0, 1, 21, 29, 32}) {
            String selected = "circuit:" + configuration;
            var token = IvPatternAdmission.token(selected, null, CIRCUIT);
            check(token.equals(Map.of(selected, 1L)), "virtual circuit retains its exact configuration: " + configuration);
            Map<String, Long> available = Map.of("circuit:5", 1L, "lens", 1L, "water", 100L);
            var accepted = IvPatternAdmission.circuits(available, token, CIRCUIT);
            var virtual = IvPatternAdmission.virtualStock(available, accepted, CIRCUIT);
            check(virtual.containsKey(selected) && !virtual.containsKey("circuit:5"),
                    "explicit virtual selector replaces the fallback instead of adding to it");
            var needs = List.of(new LongAllocation.Need<String>("wafer"::equals, 128L, true),
                    new LongAllocation.Need<String>(selected::equals, 1L, false));
            var plan = LongAllocation.plan(List.of(new LongAllocation.Supply<>("wafer", 128L, true),
                    new LongAllocation.Supply<>(selected, virtual.get(selected), false)), needs);
            check(plan != null && plan.equals(Map.of("wafer", 128L)),
                    "a smart-multiplied dispatch consumes the wafer but preserves the virtual circuit");
            check(LongAllocation.plan(List.of(new LongAllocation.Supply<>("wafer", 128L, true),
                    new LongAllocation.Supply<>("circuit:5", 1L, false)), needs) == null,
                    "another configuration cannot satisfy the accepted circuit");
            var afterSlotEdit = IvPatternAdmission.virtualStock(Map.of("circuit:2", 1L), accepted, CIRCUIT);
            check(afterSlotEdit.equals(token), "editing the slot or shared circuit does not change an accepted order");
        }

        var fallback = IvPatternAdmission.circuits(Map.of("circuit:3", Long.MAX_VALUE, "lens", 1L), Map.of(), CIRCUIT);
        check(fallback.equals(Map.of("circuit:3", 1L)), "a pattern without an explicit circuit retains the configured fallback");
        check(IvPatternAdmission.virtualStock(Map.of("circuit:3", 1L), Map.of(), CIRCUIT)
                        .equals(Map.of("circuit:3", 1L)),
                "a shared physical circuit remains a catalyst when the pattern supplies no selector");
        check(IvPatternAdmission.virtualStock(Map.of("circuit:3", 1L), Map.of("mold", Long.MAX_VALUE), CIRCUIT)
                        .containsKey("circuit:3"),
                "a virtual mold does not remove a shared physical circuit");
        var selectors = IvPatternAdmission.circuits(Map.of("circuit:5", 1L),
                Map.of("circuit:1", 1L, "circuit:2", 1L), CIRCUIT);
        check(selectors.size() == 2 && !selectors.containsKey("circuit:5"),
                "all explicitly declared circuit identities survive upstream's first-circuit cache");
        check(IvPatternAdmission.token("mold", null, CIRCUIT).equals(Map.of("mold", Long.MAX_VALUE)),
                "non-circuit item tokens keep reusable catalyst capacity");
        check(IvPatternAdmission.token(null, "acid", CIRCUIT).equals(Map.of("acid", Long.MAX_VALUE)),
                "fluid tokens keep their payload identity");
        check(rejected(() -> IvPatternAdmission.token(null, null, CIRCUIT)), "an empty wrapper grants no virtual stock");
        check(rejected(() -> IvPatternAdmission.token("mold", "acid", CIRCUIT)),
                "a malformed two-payload wrapper cannot grant two supplies");
        check(LongAllocation.plan(List.of(new LongAllocation.Supply<>("wafer", Long.MAX_VALUE, false)),
                List.of(new LongAllocation.Need<String>("wafer"::equals, 1L, true))) == null,
                "virtual ingredients cannot pay for consumed materials");
        check(LongAllocation.plan(List.of(new LongAllocation.Supply<>("wafer", 127L, true),
                        new LongAllocation.Supply<>("circuit:1", 1L, false)),
                List.of(new LongAllocation.Need<String>("wafer"::equals, 128L, true),
                        new LongAllocation.Need<String>("circuit:1"::equals, 1L, false))) == null,
                "a virtual circuit cannot increase the physical-material processing budget");
        return assertions;
    }

    private static boolean rejected(Runnable action) {
        try { action.run(); return false; }
        catch (IllegalArgumentException expected) { return true; }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
