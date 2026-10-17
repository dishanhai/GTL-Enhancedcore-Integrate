package com.gtl.enhancedcore.audit;

import com.mojang.serialization.JsonOps;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.recipe.condition.DimensionCondition;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotStore;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Opt-in three-process server integration probe; never included in the production artifact. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class PersonalPlotRuntimeChecks {
    private static final UUID ALICE = UUID.fromString("ab000001-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("ab000002-0000-0000-0000-000000000002");
    private static final UUID CHARLIE = UUID.fromString("ab000003-0000-0000-0000-000000000003");
    private static final PlotSettings ORIGINAL = PlotSettings.preset(3).withGroundBlock("minecraft:light_blue_concrete");
    private static final PlotSettings NEXT = PlotSettings.preset(4).withGroundBlock("minecraft:red_concrete");
    private static int ticks, step, checks;
    private static boolean finished;
    private static net.minecraft.nbt.CompoundTag expectedReturn;
    private PersonalPlotRuntimeChecks() {}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("gtl.personalPlot.audit") || finished || event.phase != TickEvent.Phase.END || ++ticks % 30 != 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!root.toString().contains("personal-world-runtime-20261017") || !root.getFileName().toString().equals("audit-world")) {
            finished = true; throw new IllegalStateException("Personal world audit refused non-fixture world");
        }
        Path phaseFile = root.getParent().resolve("personal-audit-phase.txt");
        try {
            int phase = Files.exists(phaseFile) ? Integer.parseInt(Files.readString(phaseFile).trim()) : 0;
            var alice = player(server, ALICE, "PlotAuditAlice");
            if (phase == 0) {
                check(PersonalPlotWorlds.snapshot(alice, "").dedicated(), "Real dedicated-server detection");
                check(!PersonalPlotWorlds.snapshot(alice, "").canEditTemplate(), "Ordinary dedicated player cannot edit template");
                check(PersonalPlotWorlds.handle(alice, PersonalPlotNetwork.Action.TEMPLATE, ORIGINAL).endsWith("permission_denied"), "Forged non-OP template request denied");
                server.getPlayerList().op(alice.getGameProfile());
                // Use another OP identity so the anti-spam clock is not bypassed.
                var operator = player(server, CHARLIE, "PlotAuditOp"); server.getPlayerList().op(operator.getGameProfile());
                check(PersonalPlotWorlds.handle(operator, PersonalPlotNetwork.Action.TEMPLATE, ORIGINAL).endsWith("template_saved"), "Template atomic save succeeds");
                var snapshot = PersonalPlotWorlds.snapshot(operator, "");
                check(!snapshot.templateReady() && snapshot.templatePending(), "New template is pending until process restart");
                check(!PersonalPlotWorlds.snapshot(alice, "").canCreatePersonal(), "Pending template cannot create live worlds");
                finish(server, phaseFile, 1, "phase0-template-pending");
            } else if (phase == 1) {
                if (step == 0) {
                    var snapshot = PersonalPlotWorlds.snapshot(alice, "");
                    check(snapshot.templateReady() && !snapshot.templatePending() && ORIGINAL.equals(snapshot.activeTemplateSettings()), "Restart activated exact saved template");
                    check(PersonalPlotWorlds.handle(alice, PersonalPlotNetwork.Action.CREATE, NEXT).endsWith("created_ready"), "Dedicated personal dimension creates live");
                    var level = server.getLevel(PersonalPlotWorlds.key(ALICE));
                    check(level != null && level.dimensionTypeId().equals(BuiltinDimensionTypes.OVERWORLD), "Live dimension has overworld type");
                    check(level.getChunkSource().getGenerator() instanceof PlotChunkGenerator plot && plot.settings().equals(ORIGINAL), "Client-proposed terrain ignored; active template is authoritative");
                    check(level.getBlockState(new BlockPos(5, -64, 5)).is(Blocks.BEDROCK), "Live generated chunk bedrock is correct");
                    check(level.getBlockState(new BlockPos(5, -54, 5)).is(Blocks.LIGHT_BLUE_CONCRETE), "Live generated concrete is correct");
                    check(PersonalPlotWorlds.isRegisteredPersonalLevel(level), "Recipe dimension bridge recognizes registered personal level");
                    conditionChecks(level, true);
                    conditionChecks(server.overworld(), false);
                    var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
                    var stem = new LevelStem(level.dimensionTypeRegistration(), level.getChunkSource().getGenerator());
                    var encoded = LevelStem.CODEC.encodeStart(ops, stem).getOrThrow(false, message -> { throw new IllegalStateException(message); });
                    var decoded = LevelStem.CODEC.parse(ops, encoded).getOrThrow(false, message -> { throw new IllegalStateException(message); });
                    check(decoded.generator() instanceof PlotChunkGenerator plot && plot.settings().equals(ORIGINAL), "Actual registry-aware LevelStem codec roundtrip");
                    for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                    alice.setPos(8.5, 81, 8.5);
                } else if (step == 1) {
                    check(PersonalPlotWorlds.handle(alice, PersonalPlotNetwork.Action.TRAVEL, null).endsWith("travelled"), "FakePlayer exercises server teleport branch");
                    check(alice.level() == server.getLevel(PersonalPlotWorlds.key(ALICE)), "Player entity moved to own level");
                } else if (step == 2) {
                    check(PersonalPlotWorlds.handle(alice, PersonalPlotNetwork.Action.RETURN, null).endsWith("returned"), "Server return branch succeeds");
                    check(alice.level() == server.overworld(), "Return restores previous dimension");
                } else if (step == 3) {
                    check(PersonalPlotWorlds.handle(alice, PersonalPlotNetwork.Action.TEMPLATE, NEXT).endsWith("template_saved"), "OP can save next template during session");
                } else if (step == 4) {
                    var bob = player(server, BOB, "PlotAuditBob");
                    check(PersonalPlotWorlds.handle(bob, PersonalPlotNetwork.Action.CREATE, NEXT).endsWith("created_ready"), "Second player gets independent live world");
                    check(PersonalPlotWorlds.snapshot(bob, "").settings().equals(ORIGINAL), "Pending new template does not affect current creates");
                    check(PersonalPlotWorlds.handle(alice, PersonalPlotNetwork.Action.CREATE, ORIGINAL).endsWith("already_owned"), "Second creation for same UUID rejected");
                    check(!Files.exists(phaseFile.resolveSibling("wireless-charger-audit-failed.txt")), "Single-block charger runtime probe did not fail");
                    check(Files.exists(phaseFile.resolveSibling("wireless-charger-audit-pass.txt")), "Single-block charger runtime probe completed");
                    finish(server, phaseFile, 2, "phase1-live-generation-travel-return");
                }
                step++;
            } else if (phase == 2) {
                check(server.getLevel(PersonalPlotWorlds.key(ALICE)) != null && server.getLevel(PersonalPlotWorlds.key(BOB)) != null, "Both runtime worlds loaded from generated pack after restart");
                check(PersonalPlotWorlds.snapshot(alice, "").settings().equals(ORIGINAL), "Existing player's terrain remains original after template update");
                check(PersonalPlotWorlds.snapshot(alice, "").activeTemplateSettings().equals(NEXT), "Second restart activated updated template");
                var charlie = player(server, CHARLIE, "PlotAuditCharlie");
                check(PersonalPlotWorlds.handle(charlie, PersonalPlotNetwork.Action.CREATE, ORIGINAL).endsWith("created_ready"), "New player receives new template after restart");
                check(PersonalPlotWorlds.snapshot(charlie, "").settings().equals(NEXT), "New template terrain applied exactly");
                check(server.getLevel(PersonalPlotWorlds.key(ALICE)).getBlockState(new BlockPos(5, -54, 5)).is(Blocks.LIGHT_BLUE_CONCRETE), "Old personal terrain persisted unchanged");
                var loaded = PersonalPlotStore.read(server.getWorldPath(LevelResource.ROOT));
                check(loaded.worlds().size() == 3, "Exactly one persisted world per player UUID");
                finish(server, phaseFile, 3, "phase2-restart-persistence-template-isolation");
            } else if (phase == 3) {
                String returnTag = "gtlEnhancedcorePersonalPlotReturn";
                if (step == 0) {
                    alice.setPos(8.5, 81, 8.5);
                    var prior = new net.minecraft.nbt.CompoundTag(); prior.putString("dimension", "minecraft:overworld");
                    prior.putDouble("x", 9.5); prior.putDouble("y", 81); prior.putDouble("z", 9.5); prior.putString("audit", "unchanged");
                    alice.getPersistentData().put(returnTag, prior);
                    check(cancelledTravel(alice, PersonalPlotNetwork.Action.TRAVEL).endsWith("travel_cancelled"), "Forge-vetoed outgoing teleport reports cancelled");
                    check(alice.level() == server.overworld(), "Forge veto leaves player in source world");
                    check(alice.getPersistentData().getCompound(returnTag).equals(prior), "Cancelled outgoing teleport preserves existing return data");
                } else if (step == 1) {
                    check(PersonalPlotWorlds.handle(alice, PersonalPlotNetwork.Action.TRAVEL, null).endsWith("travelled"), "Outgoing teleport can retry after veto removed");
                    expectedReturn = alice.getPersistentData().getCompound(returnTag).copy();
                    check(expectedReturn.getDouble("x") == 8.5 && !expectedReturn.contains("audit"), "Successful outgoing movement commits exact source return position");
                } else if (step == 2) {
                    check(cancelledTravel(alice, PersonalPlotNetwork.Action.RETURN).endsWith("travel_cancelled"), "Forge-vetoed return reports cancelled");
                    check(alice.level() == server.getLevel(PersonalPlotWorlds.key(ALICE)), "Return veto leaves player in own world");
                    check(alice.getPersistentData().getCompound(returnTag).equals(expectedReturn), "Cancelled return preserves the committed return position");
                } else if (step == 3) {
                    check(PersonalPlotWorlds.handle(alice, PersonalPlotNetwork.Action.RETURN, null).endsWith("returned"), "Return can retry after veto removed");
                    check(alice.level() == server.overworld() && !alice.getPersistentData().contains(returnTag), "Only successful return consumes return data");
                    finish(server, phaseFile, 4, "phase3-forge-travel-cancellation");
                }
                step++;
            } else throw new IllegalStateException("Audit already complete");
        } catch (Throwable error) {
            finished = true; GTLEnhancedcore.LOGGER.error("[PERSONAL_PLOT_AUDIT] FAIL", error);
            try { Files.writeString(root.getParent().resolve("personal-audit-failed.txt"), error.toString()); } catch (Exception suppressed) { error.addSuppressed(suppressed); }
            server.halt(false);
        }
    }
    private static ServerPlayer player(MinecraftServer server, UUID uuid, String name) {
        var player = FakePlayerFactory.get(server.overworld(), new GameProfile(uuid, name));
        player.setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(GTLEnhancedcoreItems.PLOT_WORLD_CONTROLLER.get())); return player;
    }
    private static String cancelledTravel(ServerPlayer player, PersonalPlotNetwork.Action action) {
        java.util.function.Consumer<EntityTravelToDimensionEvent> veto = event -> { if (event.getEntity() == player) event.setCanceled(true); };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, EntityTravelToDimensionEvent.class, veto);
        try { return PersonalPlotWorlds.handle(player, action, null); }
        finally { MinecraftForge.EVENT_BUS.unregister(veto); }
    }
    private static void conditionChecks(ServerLevel level, boolean personal) {
        BlockPos position = new BlockPos(3, 82, 3);
        level.setBlock(position, GTMachines.COMPRESSOR[GTValues.LV].defaultBlockState(), 3);
        var machine = (IRecipeLogicMachine) MetaMachine.getMachine(level, position);
        check(machine != null, "Condition probe machine exists");
        check(new DimensionCondition(Level.OVERWORLD.location()).test(null, machine.getRecipeLogic()),
                "Live Mixin accepts overworld recipe in " + (personal ? "registered personal" : "original overworld"));
        check(!new DimensionCondition(Level.NETHER.location()).test(null, machine.getRecipeLogic()), "Dimension bridge does not admit Nether recipes");
        check(!new DimensionCondition(Level.END.location()).test(null, machine.getRecipeLogic()), "Dimension bridge does not admit End recipes");
    }
    private static void finish(MinecraftServer server, Path phaseFile, int next, String label) throws Exception {
        finished = true; server.saveEverything(true, true, true); Files.writeString(phaseFile, Integer.toString(next));
        Files.writeString(phaseFile.resolveSibling(label + ".txt"), "PASS assertions=" + checks + "\nFakePlayer server path; no client rendering or network acknowledgement tested.\n");
        GTLEnhancedcore.LOGGER.info("[PERSONAL_PLOT_AUDIT] PASS {} assertions={}", label, checks); server.halt(false);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; GTLEnhancedcore.LOGGER.info("[PERSONAL_PLOT_AUDIT] OK {}", message); }
}
