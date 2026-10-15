package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.google.common.collect.ImmutableTable;
import com.gtl.enhancedcore.client.memory.ClientWorldCacheCleanup;
import com.gtl.enhancedcore.client.memory.JeiRecipeTransferButtonMemory;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mezz.jei.library.plugins.vanilla.anvil.AnvilHelper;
import mezz.jei.library.recipes.RecipeTransferManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import snownee.jade.impl.BlockAccessorImpl;
import snownee.jade.impl.ObjectDataCenter;

/** Seeds the player UI caches in an opt-in disposable client, then verifies real transitions. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class ClientWorldCacheChecks {
    private static final boolean ENABLED = Boolean.getBoolean("gtl.enhancedcore.clientCacheAudit");
    private static WeakReference<Level> seeded = new WeakReference<>(null);
    private static final List<Map<String, Object>> CHECKS = new ArrayList<>();
    // The probe menu has no player, world, slots or callbacks. Keeping its manager alive tests the
    // weak negative cache without giving this audit an independent world retention path.
    private static RecipeTransferManager transferProbe;
    private static WeakReference<AbstractContainerMenu> transferMenu;
    private static boolean transferChecked;
    private static Object transferButton;
    private static java.lang.reflect.Method updateTransferButton;
    private ClientWorldCacheChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        checkTransferProbe();
        if (mc.level == null || mc.player == null || seeded.get() == mc.level) return;
        try {
            AnvilMenu menu = AnvilHelper.getFakeAnvilMenu();
            GrindstoneMenu grindstone = fakeGrindstone();
            var level = mc.level;
            var pos = mc.player.blockPosition();
            var accessor = new BlockAccessorImpl.Builder().level(level).player(mc.player)
                    .serverData(new CompoundTag()).serverConnected(false).showDetails(false)
                    .hit(new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
                    .blockState(level.getBlockState(pos)).blockEntity(() -> level.getBlockEntity(pos)).build();
            ObjectDataCenter.set(accessor);
            // A no-world cleanup request must preserve the active world's caches.
            ClientWorldCacheCleanup.release(null);
            boolean preserved = cachedMenu() == menu && ObjectDataCenter.get() == accessor;
            boolean grindstonePreserved = cachedGrindstone() == grindstone && grindstone != null;
            seedTransferButton();
            record("seed", level, preserved, preserved, grindstonePreserved, seedTransferProbe(),
                    JeiRecipeTransferButtonMemory.countReferencing(level), null);
            seeded = new WeakReference<>(level);
        } catch (Throwable failure) { record("seed", mc.level, false, false, false, false, -1, failure.toString()); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void clone(ClientPlayerNetworkEvent.Clone event) {
        if (!ENABLED || event.getOldPlayer() == null || event.getNewPlayer() == null
                || event.getOldPlayer().level() == event.getNewPlayer().level()) return;
        Level old = event.getOldPlayer().level();
        try {
            AnvilMenu menu = cachedMenu();
            boolean anvilClear = menu == null || menu.slots.stream().noneMatch(slot ->
                    slot.container instanceof Inventory inventory && inventory.player.level() == old);
            boolean jadeClear = ObjectDataCenter.get() == null || ObjectDataCenter.get().getLevel() != old;
            GrindstoneMenu grindstone = cachedGrindstone();
            boolean grindstoneClear = grindstone == null || grindstone.slots.stream().noneMatch(slot ->
                    slot.container instanceof Inventory inventory && inventory.player.level() == old);
            int transferButtons = JeiRecipeTransferButtonMemory.countReferencing(old);
            record("world_left", old, anvilClear, jadeClear, grindstoneClear, transferButtons == 0,
                    transferButtons, null);
        } catch (Throwable failure) { record("world_left", old, false, false, false, false, -1, failure.toString()); }
    }

    /** Dimension travel unloads the client level without necessarily cloning the player. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void unloaded(LevelEvent.Unload event) {
        if (!ENABLED || !(event.getLevel() instanceof Level old) || !old.isClientSide) return;
        try {
            recordWorldLeft(old);
        } catch (Throwable failure) {
            record("world_left", old, false, false, false, false, -1, failure.toString());
        }
    }

    private static void recordWorldLeft(Level old) throws Exception {
        AnvilMenu menu = cachedMenu();
        boolean anvilClear = menu == null || menu.slots.stream().noneMatch(slot ->
                slot.container instanceof Inventory inventory && inventory.player.level() == old);
        boolean jadeClear = ObjectDataCenter.get() == null || ObjectDataCenter.get().getLevel() != old;
        GrindstoneMenu grindstone = cachedGrindstone();
        boolean grindstoneClear = grindstone == null || grindstone.slots.stream().noneMatch(slot ->
                slot.container instanceof Inventory inventory && inventory.player.level() == old);
        int transferButtons = JeiRecipeTransferButtonMemory.countReferencing(old);
        record("world_left", old, anvilClear, jadeClear, grindstoneClear, transferButtons == 0,
                transferButtons, null);
    }

    private static AnvilMenu cachedMenu() throws Exception {
        var field = AnvilHelper.class.getDeclaredField("ANVIL_MENU"); field.setAccessible(true);
        return (AnvilMenu)field.get(null);
    }
    private static GrindstoneMenu cachedGrindstone() throws Exception {
        var type = Class.forName("mezz.jei.library.plugins.vanilla.grindstone.GrindstoneHelper");
        var field = type.getDeclaredField("GRINDSTONE_MENU"); field.setAccessible(true);
        return (GrindstoneMenu)field.get(null);
    }
    private static GrindstoneMenu fakeGrindstone() throws Exception {
        var type = Class.forName("mezz.jei.library.plugins.vanilla.grindstone.GrindstoneHelper");
        var method = type.getDeclaredMethod("getFakeGrindstoneMenu"); method.setAccessible(true);
        return (GrindstoneMenu)method.invoke(null);
    }
    private static boolean seedTransferProbe() throws Exception {
        if (transferProbe != null || transferChecked) return true;
        transferProbe = new RecipeTransferManager(ImmutableTable.of());
        AbstractContainerMenu menu = new UnsupportedMenu();
        var method = RecipeTransferManager.class.getDeclaredMethod("getMenuType", AbstractContainerMenu.class);
        method.setAccessible(true);
        var field = RecipeTransferManager.class.getDeclaredField("unsupportedContainers"); field.setAccessible(true);
        @SuppressWarnings("unchecked") Set<AbstractContainerMenu> cache = (Set<AbstractContainerMenu>)field.get(transferProbe);
        boolean preserved = method.invoke(transferProbe, menu) == null && cache.contains(menu)
                && method.invoke(transferProbe, menu) == null && cache.contains(menu);
        transferMenu = new WeakReference<>(menu);
        return preserved;
    }
    private static void seedTransferButton() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (transferButton == null) {
            Class<?> type = Class.forName("mezz.jei.gui.recipes.RecipeTransferButton");
            var constructor = type.getDeclaredConstructor(
                    Class.forName("mezz.jei.api.gui.drawable.IDrawable"),
                    Class.forName("mezz.jei.api.gui.IRecipeLayoutDrawable"), Runnable.class);
            constructor.setAccessible(true);
            transferButton = constructor.newInstance(new Object[] {null, null, (Runnable)() -> {}});
            updateTransferButton = type.getDeclaredMethod("update", AbstractContainerMenu.class, Player.class);
            updateTransferButton.setAccessible(true);
        }
        if (!(transferButton instanceof JeiRecipeTransferButtonMemory.Access access))
            throw new IllegalStateException("JEI transfer-button Mixin was not applied");
        updateTransferButton.invoke(transferButton, mc.player.containerMenu, null);
        if (!access.enhancedcore$references(mc.level))
            throw new IllegalStateException("JEI transfer button did not bind to the active world menu");
    }
    private static void checkTransferProbe() {
        if (transferMenu == null || transferChecked) return;
        transferChecked = true;
        System.gc();
        boolean released = transferMenu.get() == null;
        record("weak_transfer_gc", null, true, true, true, released, 0, null);
        transferProbe = null;
        transferMenu = null;
    }
    private static final class UnsupportedMenu extends AbstractContainerMenu {
        private UnsupportedMenu() { super(null, 0); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return true; }
    }
    private static void record(String phase, Level level, boolean anvil, boolean jade,
            boolean grindstone, boolean transferCache, int transferButtonReferences, String error) {
        try {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("phase", phase);
            row.put("dimension", level == null ? null : level.dimension().location().toString());
            row.put("anvil", anvil); row.put("jade", jade); row.put("error", error);
            row.put("grindstone", grindstone); row.put("transferCache", transferCache);
            row.put("transferButtonReferences", transferButtonReferences);
            row.put("clientThread", Minecraft.getInstance().isSameThread()); CHECKS.add(row);
            Path path = Path.of(System.getProperty("gtl.enhancedcore.clientCacheAuditOutput", "client-world-cache-checks.json"));
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "passed", CHECKS.stream().allMatch(check -> Boolean.TRUE.equals(check.get("anvil"))
                            && Boolean.TRUE.equals(check.get("jade")) && Boolean.TRUE.equals(check.get("grindstone"))
                            && Boolean.TRUE.equals(check.get("transferCache")) && Boolean.TRUE.equals(check.get("clientThread"))
                            && (!"seed".equals(check.get("phase"))
                                    || ((Number)check.getOrDefault("transferButtonReferences", -1)).intValue() > 0)
                            && (!"world_left".equals(check.get("phase"))
                                    || ((Number)check.getOrDefault("transferButtonReferences", -1)).intValue() == 0))
                            && CHECKS.stream().anyMatch(check -> "world_left".equals(check.get("phase"))),
                    "checks", CHECKS)));
        } catch (Exception failure) { failure.printStackTrace(); }
    }
}
