package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.config.ForeignMachineMixinPolicy;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/** Verify the shipped server-side lookup cannot enter GT's synchronous controller chunk loader. */
final class IvLoadedControllerRegression {
    static int run() throws Exception {
        int assertions = 0;
        String owner = "com/gtl/enhancedcore/common/recipe/iv/IvBuffers";
        ClassNode buffers = CellPersistenceRegression.compiled(owner);
        for (String name : new String[]{"isolated", "controller", "refresh", "rejectBind"}) {
            var method = CellPersistenceRegression.method(buffers, name);
            for (var instruction : method.instructions) if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                if (call.name.equals("getControllers")) throw new AssertionError(name + " must not force-load controllers");
            }
            assertions++;
        }
        var lookup = CellPersistenceRegression.method(buffers, "loadedControllers");
        if (CellPersistenceRegression.calls(lookup, "net/minecraft/server/level/ServerChunkCache", "getChunkNow") != 1)
            throw new AssertionError("server controller lookup must use getChunkNow");
        assertions++;
        for (String name : new String[]{"controller", "refresh"}) {
            if (CellPersistenceRegression.calls(CellPersistenceRegression.method(buffers, name), owner, "controllersLoaded") != 1)
                throw new AssertionError(name + " must retain ownership/reject partial controller sets while chunks are absent");
            assertions++;
        }
        try (JarFile jar = new JarFile(Path.of("libs/gtceu-1.20.1-1.4.4.jar").toFile())) {
            ClassNode part = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(
                    "com/gregtechceu/gtceu/api/machine/multiblock/part/MultiblockPartMachine.class"))).accept(part, 0);
            if (part.fields.stream().noneMatch(f -> f.name.equals("controllerPositions") && f.desc.equals("Ljava/util/Set;")))
                throw new AssertionError("actual GTCEu accessor target changed");
            assertions++;
        }
        if (!ForeignMachineMixinPolicy.shouldApply(new MachineOverrideConfig.Settings(false, false),
                "gtceu.MultiblockPartControllerPositionsAccessor")) throw new AssertionError("IV bridge accessor is always enabled");
        return assertions + 1;
    }
}
