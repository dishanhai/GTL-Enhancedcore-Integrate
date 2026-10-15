package com.gtl.enhancedcore.client.copy;

import com.gtl.enhancedcore.GTLEnhancedcore;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 取鼠标下方的流体。
 *
 * <p>流体与物品不同：悬停流体时 Forge 不会触发 {@code ItemTooltipEvent}，
 * 所以必须直接从 JEI 的配方界面查询。JEI 是可选依赖（本项目 compileOnly），
 * 因此这里全部走反射 + 加载检查，未装 JEI 时安静返回 null。
 */
final class CopyHoverLookup {
    /** JEI 官方通用流体 ingredient 类型。 */
    private static final String FLUID_TYPE_CLASS = "mezz.jei.api.forge.ForgeTypes";
    private static final String RECIPES_GUI_CLASS = "mezz.jei.gui.recipes.RecipesGui";

    private CopyHoverLookup() {}

    private static Boolean jeiLoaded;

    private static boolean jeiPresent() {
        if (jeiLoaded == null) {
            jeiLoaded = ModList.get() != null && ModList.get().isLoaded("jei");
        }
        return jeiLoaded;
    }

    @Nullable
    static FluidStack fluidUnderMouse(@Nullable Screen screen) {
        if (screen == null || !jeiPresent() || !RECIPES_GUI_CLASS.equals(screen.getClass().getName())) {
            return null;
        }
        try {
            Class<?> forgeTypes = Class.forName(FLUID_TYPE_CLASS);
            Object fluidType = forgeTypes.getField("FLUID_STACK").get(null);
            if (!(fluidType instanceof IIngredientType<?> type)) return null;
            var method = RecipesGui.class.getMethod("getIngredientUnderMouse", IIngredientType.class);
            Object result = method.invoke(screen, type);
            if (result instanceof java.util.Optional<?> optional
                    && optional.orElse(null) instanceof FluidStack stack && !stack.isEmpty()) {
                return stack;
            }
            return null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            GTLEnhancedcore.LOGGER.debug("JEI fluid lookup unavailable", failure);
            return null;
        }
    }
}
