package com.gtl.enhancedcore;

import com.gtl.enhancedcore.integration.ae2.remote.RemoteProviderRange;
import com.gtl.enhancedcore.integration.ae2.remote.RemoteMenuLease;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

public final class RemoteProviderRangeRegression {
    private RemoteProviderRangeRegression() {}
    public static int run() throws Exception {
        check(RemoteProviderRange.allows(true, 4096.0 * 4096.0), "old 4096-block distance remains valid");
        check(RemoteProviderRange.allows(true, 20000.0 * 20000.0), "inclusive 20000-block distance");
        check(!RemoteProviderRange.allows(true, 20000.01 * 20000.01), "above 20000 blocks rejected");
        check(!RemoteProviderRange.allows(false, 0), "other dimensions rejected even at identical coordinates");
        check(!RemoteProviderRange.allows(true, Double.NaN), "invalid distance rejected");
        check(!RemoteProviderRange.allows(true, Double.POSITIVE_INFINITY), "infinite distance rejected");
        check(!RemoteProviderRange.allows(true, -1), "negative squared distance rejected");
        check(RemoteProviderRange.allows(true, 0), "zero distance valid");
        checkLookup("appliedenergistics2-forge-15.4.10.jar", "appeng/menu/locator/BlockEntityLocator",
                "locate", "net/minecraft/world/level/Level", "m_7702_", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;");
        checkLookup("appliedenergistics2-forge-15.4.10.jar", "appeng/menu/locator/PartLocator",
                "locate", "appeng/api/parts/PartHelper", "getPart", "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Lappeng/api/parts/IPart;");
        checkLookup("gtceu-1.20.1-1.4.4.jar", "com/gregtechceu/gtceu/api/gui/factory/MachineUIFactory",
                "readHolderFromSyncData", "net/minecraft/world/level/Level", "m_7702_", "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;");
        Object source = new Object(), inventory = new Object(), provider = new Object(), unrelated = new Object();
        var lease = new RemoteMenuLease<>(source, 100L);
        check(lease.observe(inventory, inventory, 1), "closing old menu cannot discard pending snapshot");
        check(lease.claim(inventory, inventory, 2), "native lookup can claim while old menu is closed");
        check(lease.observe(provider, inventory, 3), "only a claimed snapshot binds the next menu");
        check(lease.claim(provider, inventory, 101), "bound host remains valid after pending deadline");
        check(!lease.observe(inventory, inventory, 102), "closing bound menu releases host");
        check(!lease.claim(unrelated, inventory, 103), "another menu cannot take bound host");
        var unused = new RemoteMenuLease<>(source, 100L);
        check(!unused.observe(unrelated, inventory, 1), "unclaimed snapshot cannot bind an unrelated next menu");
        check(!unused.claim(unrelated, inventory, 2), "lookup from unrelated menu cannot consume snapshot");
        var expired = new RemoteMenuLease<>(source, 100L);
        check(!expired.claim(source, inventory, 101), "unopened snapshot expires");
        check(!expired.observe(source, inventory, 101), "unopened expired host clears even on source menu");
        return 21;
    }

    private static void checkLookup(String artifact, String owner, String method, String callOwner,
                                    String callName, String descriptor) throws Exception {
        try (ZipFile jar = new ZipFile(Path.of("libs", artifact).toFile())) {
            ClassNode node = new ClassNode();
            try (var input = jar.getInputStream(jar.getEntry(owner + ".class"))) {
                new ClassReader(input).accept(node, 0);
            }
            int calls = 0;
            for (var target : node.methods) {
                if (!target.name.equals(method)) continue;
                for (var instruction : target.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(callOwner)
                            && call.name.equals(callName) && call.desc.equals(descriptor)) calls++;
                }
            }
            check(calls == 1, "remote host lookup must match exactly once in " + owner + "." + method);
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
