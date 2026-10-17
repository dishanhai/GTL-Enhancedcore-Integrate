package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gtl.enhancedcore.common.util.ActiveBlockSnapshot;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Higher priority permits injection into GTLCore's merged overwrite; preserve its update flags. */
@Mixin(value = WorkableMultiblockMachine.class, remap = false, priority = 1100)
public abstract class WorkableActiveBlocksSnapshotMixin {
    @Redirect(method = "updateActiveBlocks(Z)V",
            at = @At(value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/longs/LongSet;iterator()Lit/unimi/dsi/fastutil/longs/LongIterator;",
                    remap = false),
            require = 1, expect = 1, allow = 1, remap = false)
    private LongIterator enhancedcore$activeBlockSnapshot(LongSet positions) {
        return ActiveBlockSnapshot.iterator(positions,
                ((WorkableMultiblockMachine) (Object) this).getPatternLock());
    }
}
