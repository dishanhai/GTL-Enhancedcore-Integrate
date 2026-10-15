package com.gtl.enhancedcore.mixin.jade.client;

import com.gtl.enhancedcore.client.memory.ClientWorldCacheCleanup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.Accessor;

/** Jade's public setter also clears lastObject, clientHandler and the progress tracker. */
@Pseudo
@Mixin(targets = "snownee.jade.impl.ObjectDataCenter", remap = false)
public abstract class JadeObjectDataMemoryMixin {
    @Shadow private static Accessor<?> accessor;
    @Shadow public static void set(Accessor<?> value) { throw new AssertionError(); }

    @Inject(method = "<clinit>", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private static void enhancedcore$registerWorldCleanup(CallbackInfo ci) {
        ClientWorldCacheCleanup.register(departed -> {
            Accessor<?> cached = accessor;
            if (cached != null && cached.getLevel() == departed) set(null);
        });
    }
}
