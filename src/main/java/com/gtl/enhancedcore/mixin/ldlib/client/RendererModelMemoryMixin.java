package com.gtl.enhancedcore.mixin.ldlib.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** LDLib's custom rebake return bypasses both ThreadLocal.remove calls. */
@Mixin(targets = "com.lowdragmc.lowdraglib.client.model.forge.LDLRendererModel$RendererBakedModel", remap = false)
public abstract class RendererModelMemoryMixin {
    @Shadow @Final public static ThreadLocal<ModelData> CURRENT_MODEL_DATA;
    @Shadow @Final public static ThreadLocal<RenderType> CURRENT_RENDER_TYPE;

    @WrapMethod(method = "getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;"
            + "Lnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;"
            + "Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;", remap = false)
    private List<BakedQuad> enhancedcore$releaseRenderContext(BlockState state, Direction side, RandomSource random,
            ModelData data, RenderType renderType, Operation<List<BakedQuad>> original) {
        ModelData previousData = CURRENT_MODEL_DATA.get();
        RenderType previousType = CURRENT_RENDER_TYPE.get();
        try {
            return original.call(state, side, random, data, renderType);
        } finally {
            if (previousData == null) CURRENT_MODEL_DATA.remove();
            else CURRENT_MODEL_DATA.set(previousData);
            if (previousType == null) CURRENT_RENDER_TYPE.remove();
            else CURRENT_RENDER_TYPE.set(previousType);
        }
    }

    @WrapMethod(method = "getParticleIcon(Lnet/minecraftforge/client/model/data/ModelData;)"
            + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;", remap = false)
    private TextureAtlasSprite enhancedcore$releaseParticleContext(ModelData data,
            Operation<TextureAtlasSprite> original) {
        ModelData previousData = CURRENT_MODEL_DATA.get();
        try {
            return original.call(data);
        } finally {
            if (previousData == null) CURRENT_MODEL_DATA.remove();
            else CURRENT_MODEL_DATA.set(previousData);
        }
    }
}
