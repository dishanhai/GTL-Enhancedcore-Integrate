package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.WirelessChargerMachine;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.mojang.authlib.GameProfile;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Real server tick and wireless-account checks, strictly confined to the disposable personal-world fixture. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class WirelessChargerRuntimeChecks {
    private static final Path FIXTURE = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/personal-world-runtime-20261017/audit-world")
            .toAbsolutePath().normalize();
    private static final UUID LV_OWNER = UUID.fromString("ad000001-0000-0000-0000-000000000001");
    private static final UUID MV_OWNER = UUID.fromString("ad000002-0000-0000-0000-000000000002");
    private static final BigInteger SUPPLY = BigInteger.valueOf(1_000_000_000L);
    private static final BlockPos LV_POS = new BlockPos(512, 160, 512);
    private static final BlockPos MV_POS = new BlockPos(514, 160, 512);
    private static final BlockPos LV_TARGET = new BlockPos(520, 160, 512);
    private static final BlockPos MV_TARGET = new BlockPos(522, 160, 512);
    private static final BlockPos HV_TARGET = new BlockPos(524, 160, 512);
    private static final Set<BlockPos> PLACED = new LinkedHashSet<>();
    private static boolean started, finished, ownsForcedChunk, accountsTouched;
    private static int ticks, state, wait, checks;
    private static ServerLevel world;
    private static WirelessChargerMachine lv, mv;
    private static IEnergyContainer lvTarget, mvTarget, hvTarget;
    private static BigInteger priorLv, priorMv, afterLv, afterMv;
    private WirelessChargerRuntimeChecks() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("gtl.personalPlot.audit") || finished || event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!FIXTURE.equals(root)) {
            finished = true;
            GTLEnhancedcore.LOGGER.warn("[WIRELESS_CHARGER_AUDIT] Refused non-fixture world {}", root);
            return;
        }
        Path parent = root.getParent();
        try {
            if (!started) {
                Path phase = parent.resolve("personal-audit-phase.txt");
                if (!Files.exists(phase) || !Files.readString(phase).trim().equals("1")) return;
                started = true;
                setup(server);
                return;
            }
            check(++ticks < 135, "Fixture completes within phase-one tick budget", false);
            if (state == 0 && lvTarget.getEnergyStored() > 0) {
                check(lv.isActive() && !mv.isActive(), "LV becomes active without a multiblock structure");
                check(lvTarget.getEnergyStored() == lvTarget.getEnergyCapacity(), "LV target filled to exact capacity");
                check(mvTarget.getEnergyStored() == 0 && hvTarget.getEnergyStored() == 0, "LV refuses MV and HV targets");
                BigInteger debit = fee(GTValues.LV).add(BigInteger.valueOf(lvTarget.getEnergyStored()));
                afterLv = WirelessEnergyManager.getUserEU(LV_OWNER);
                check(afterLv.equals(SUPPLY.subtract(debit)), "LV first batch debit equals twenty-tick coverage fee plus actual delivered EU");
                check(WirelessEnergyManager.getUserEU(MV_OWNER).equals(SUPPLY), "LV does not charge the independent MV account");
                lv.setWorkingEnabled(false);
                drainTargets();
                mv.setWorkingEnabled(true);
                state = 1;
            } else if (state == 1 && mvTarget.getEnergyStored() > 0) {
                check(!lv.isActive() && mv.isActive(), "MV becomes active while disabled LV stays inactive");
                check(lvTarget.getEnergyStored() == lvTarget.getEnergyCapacity()
                        && mvTarget.getEnergyStored() == mvTarget.getEnergyCapacity(), "MV fills both LV and MV targets");
                check(hvTarget.getEnergyStored() == 0, "MV refuses HV target");
                BigInteger debit = fee(GTValues.MV).add(BigInteger.valueOf(lvTarget.getEnergyStored()))
                        .add(BigInteger.valueOf(mvTarget.getEnergyStored()));
                afterMv = WirelessEnergyManager.getUserEU(MV_OWNER);
                check(afterMv.equals(SUPPLY.subtract(debit)), "MV first batch debit equals coverage fee plus both actual fills");
                check(WirelessEnergyManager.getUserEU(LV_OWNER).equals(afterLv), "Disabled LV account stops changing");
                mv.setWorkingEnabled(false);
                drainTargets();
                wait = 0;
                state = 2;
            } else if (state == 2 && ++wait >= 25) {
                check(!lv.isActive() && !mv.isActive(), "Both disabled machines stay inactive");
                check(lvTarget.getEnergyStored() == 0 && mvTarget.getEnergyStored() == 0 && hvTarget.getEnergyStored() == 0,
                        "Disabled machines supply no energy over twenty-five real ticks");
                check(WirelessEnergyManager.getUserEU(LV_OWNER).equals(afterLv)
                        && WirelessEnergyManager.getUserEU(MV_OWNER).equals(afterMv), "Disabled machines incur no further coverage fee");
                WirelessEnergyManager.setUserEU(LV_OWNER, BigInteger.ZERO);
                lv.setWorkingEnabled(true);
                wait = 0;
                state = 3;
            } else if (state == 3 && ++wait >= 25) {
                check(!lv.isActive() && lv.getDiagnostic() != null, "Empty wireless account leaves LV inactive with a diagnostic");
                check(lvTarget.getEnergyStored() == 0 && mvTarget.getEnergyStored() == 0, "Insufficient balance does not create target energy");
                check(WirelessEnergyManager.getUserEU(LV_OWNER).equals(BigInteger.ZERO), "Insufficient balance cannot overdraw account");
                cleanup();
                Files.writeString(parent.resolve("wireless-charger-audit-pass.txt"), "PASS assertions=" + checks + " ticks=" + ticks
                        + "\nReal dedicated-server BE ticks, LV/MV single-block type and GUI construction, exact wireless accounting, voltage gates, disable and empty-account paths. Client rendering not tested.\n");
                GTLEnhancedcore.LOGGER.info("[WIRELESS_CHARGER_AUDIT] PASS assertions={} ticks={}", checks, ticks);
                finished = true;
            }
        } catch (Throwable failure) {
            finished = true;
            try { cleanup(); } catch (Throwable cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            GTLEnhancedcore.LOGGER.error("[WIRELESS_CHARGER_AUDIT] FAIL", failure);
            try { Files.writeString(parent.resolve("wireless-charger-audit-failed.txt"), failure.toString()); }
            catch (Exception writeFailure) { GTLEnhancedcore.LOGGER.error("[WIRELESS_CHARGER_AUDIT] Could not write failure receipt", writeFailure); }
        }
    }

    private static void setup(MinecraftServer server) {
        check(server.isDedicatedServer(), "Fixture is a real dedicated server");
        world = server.overworld();
        long chunk = ChunkPos.asLong(LV_POS.getX() >> 4, LV_POS.getZ() >> 4);
        if (!world.getForcedChunks().contains(chunk)) {
            world.setChunkForced(LV_POS.getX() >> 4, LV_POS.getZ() >> 4, true);
            ownsForcedChunk = true;
        }
        MachineDefinition[] definitions = GTLEnhancedcoreMachines.getWirelessChargers();
        check(!(definitions[GTValues.LV] instanceof MultiblockMachineDefinition)
                && !(definitions[GTValues.MV] instanceof MultiblockMachineDefinition), "Both IDs register ordinary single-block definitions");
        lv = (WirelessChargerMachine) place(definitions[GTValues.LV], LV_POS);
        mv = (WirelessChargerMachine) place(definitions[GTValues.MV], MV_POS);
        lv.setWorkingEnabled(false); mv.setWorkingEnabled(false);
        check(!(lv instanceof IMultiController) && !(mv instanceof IMultiController), "Both placed machine instances are not multiblock controllers");
        check(lv.createUIWidget() != null && mv.createUIWidget() != null, "Both server-side GUI trees construct successfully");
        lv.onMachinePlaced(FakePlayerFactory.get(world, new GameProfile(LV_OWNER, "ChargerAuditLV")), definitions[GTValues.LV].asStack());
        mv.onMachinePlaced(FakePlayerFactory.get(world, new GameProfile(MV_OWNER, "ChargerAuditMV")), definitions[GTValues.MV].asStack());
        check(LV_OWNER.equals(lv.getUserId()) && MV_OWNER.equals(mv.getUserId()), "Placement binds exact independent player UUIDs");
        place(GTMachines.COMPRESSOR[GTValues.LV], LV_TARGET);
        place(GTMachines.COMPRESSOR[GTValues.MV], MV_TARGET);
        place(GTMachines.COMPRESSOR[GTValues.HV], HV_TARGET);
        lvTarget = energy(LV_TARGET); mvTarget = energy(MV_TARGET); hvTarget = energy(HV_TARGET);
        drainTargets();
        priorLv = WirelessEnergyManager.getUserEU(LV_OWNER);
        priorMv = WirelessEnergyManager.getUserEU(MV_OWNER);
        accountsTouched = true;
        WirelessEnergyManager.setUserEU(LV_OWNER, SUPPLY);
        WirelessEnergyManager.setUserEU(MV_OWNER, SUPPLY);
        lv.setWorkingEnabled(true);
        GTLEnhancedcore.LOGGER.info("[WIRELESS_CHARGER_AUDIT] Started isolated chunk {},{}", LV_POS.getX() >> 4, LV_POS.getZ() >> 4);
    }

    private static MetaMachine place(MachineDefinition definition, BlockPos position) {
        check(world.isEmptyBlock(position), "Audit placement is air at " + position);
        PLACED.add(position);
        check(world.setBlock(position, definition.defaultBlockState(), 3), "Placed " + definition.getId());
        MetaMachine result = MetaMachine.getMachine(world, position);
        check(result != null, "Placed machine has a real block entity");
        return result;
    }

    private static IEnergyContainer energy(BlockPos position) {
        for (Direction direction : Direction.values()) {
            IEnergyContainer container = GTCapabilityHelper.getEnergyContainer(world, position, direction);
            if (container != null) return container;
        }
        throw new AssertionError("Compressor has no energy capability at " + position);
    }

    private static BigInteger fee(int tier) {
        return BigInteger.valueOf((long) WirelessChargerMachine.chunkCount() * GTValues.V[tier] * 20);
    }

    private static void drainTargets() {
        if (lvTarget != null) lvTarget.removeEnergy(lvTarget.getEnergyStored());
        if (mvTarget != null) mvTarget.removeEnergy(mvTarget.getEnergyStored());
        if (hvTarget != null) hvTarget.removeEnergy(hvTarget.getEnergyStored());
    }

    private static void cleanup() {
        if (lv != null) lv.setWorkingEnabled(false);
        if (mv != null) mv.setWorkingEnabled(false);
        if (world != null) {
            for (BlockPos position : PLACED) world.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
            PLACED.clear();
            if (ownsForcedChunk) {
                world.setChunkForced(LV_POS.getX() >> 4, LV_POS.getZ() >> 4, false);
                ownsForcedChunk = false;
            }
        }
        if (accountsTouched) {
            WirelessEnergyManager.setUserEU(LV_OWNER, priorLv);
            WirelessEnergyManager.setUserEU(MV_OWNER, priorMv);
            accountsTouched = false;
        }
    }

    private static void check(boolean value, String message) { check(value, message, true); }
    private static void check(boolean value, String message, boolean log) {
        if (!value) throw new AssertionError(message);
        checks++;
        if (log) GTLEnhancedcore.LOGGER.info("[WIRELESS_CHARGER_AUDIT] OK {}", message);
    }
}
