package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.WirelessChargerMachine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Separate lifecycle/visual supplement; does not change or repeat the frozen performance benchmark. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class ChargerVfxLifecycleChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/charger-vfx-redesign-20261017").toAbsolutePath().normalize();
    private static final Path DIR = Path.of(System.getProperty("gtl.enhancedcore.chargerVfxDirectory", ROOT.resolve("invalid").toString())).toAbsolutePath().normalize();
    private static final BlockPos[] POS = {new BlockPos(-3, 80, 0), new BlockPos(3, 80, 0)};
    private static final Map<String, Object> REPORT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> VIEWS = new ArrayList<>();
    private static final ResourceLocation TEXTURE = GTLEnhancedcore.id("textures/effect/wireless_field.png");
    private static final String[] NAMES = {"near-lv-day", "near-mv-day", "near-lv-night", "near-mv-night",
            "lod-30-day", "lod-34-day", "lod-60-day", "facing-north", "facing-east", "facing-south", "facing-west", "reload-before-day"};
    private static CompletableFuture<Void> pending, reload;
    private static ShaderInstance beforeShader;
    private static int state, ticks, view, checks;
    private static boolean done;
    private ChargerVfxLifecycleChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.chargerVfxLifecycleAudit") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (state == 0) {
                check(DIR.startsWith(ROOT) && !DIR.equals(ROOT) && mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(DIR), "Strict isolated game directory");
                check(Files.isRegularFile(DIR.resolve("charger-vfx-audit.marker")), "Fixture marker");
                mc.options.pauseOnLostFocus = false;
                if (mc.getOverlay() != null || mc.screen == null) return;
                check(!Files.exists(DIR.resolve("saves/charger-vfx-lifecycle")), "Fresh world"); state = 1;
                mc.createWorldOpenFlows().createFreshLevel("charger-vfx-lifecycle", new LevelSettings("Charger Lifecycle Audit", GameType.CREATIVE,
                        false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(170017L, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            if (state == 1) {
                var server = mc.getSingleplayerServer();
                pending = CompletableFuture.runAsync(() -> {
                    var world = server.overworld();
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.connection.teleport(100, 140, 100, 180, 0);
                    world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    world.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                    world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                    for (int x = -12; x <= 12; x++) for (int z = -6; z <= 68; z++)
                        world.setBlock(new BlockPos(x, 79, z), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
                    for (int i = 0; i < POS.length; i++) {
                        world.setBlock(POS[i], GTLEnhancedcoreMachines.getWirelessChargers()[i == 0 ? GTValues.LV : GTValues.MV].defaultBlockState(), 3);
                        var machine = (WirelessChargerMachine) MetaMachine.getMachine(world, POS[i]);
                        machine.setWorkingEnabled(false); machine.setFrontFacing(Direction.NORTH);
                    }
                    player.getAbilities().flying = true; player.onUpdateAbilities();
                }, server);
                state = 2; ticks = 0;
            } else if (state == 2 && pending.isDone() && ++ticks >= 80) {
                pending.join(); mc.setScreen(null); mc.options.hideGui = true;
                REPORT.put("label", System.getProperty("gtl.enhancedcore.chargerVfxLabel"));
                REPORT.put("resolution", List.of(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
                view = 0; schedule(mc, view);
            } else if (state == 3 && pending.isDone() && ++ticks >= 50) {
                pending.join(); state = 4;
            } else if (state == 5 && reload.isDone() && mc.getOverlay() == null) {
                reload.join(); ticks = 0; state = 6;
            } else if (state == 6 && ++ticks >= 60 && mc.getOverlay() == null) {
                state = 7;
            } else if (state == 8 && pending.isDone() && ++ticks >= 50) {
                pending.join(); state = 9;
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    private static void schedule(Minecraft mc, int index) {
        var server = mc.getSingleplayerServer();
        pending = CompletableFuture.runAsync(() -> {
            var world = server.overworld();
            world.setDayTime(index == 2 || index == 3 || index == 13 ? 18000 : 6000);
            Direction facing = index >= 7 && index <= 10 ? new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}[index - 7] : Direction.NORTH;
            for (BlockPos pos : POS) ((WirelessChargerMachine) MetaMachine.getMachine(world, pos)).setFrontFacing(facing);
            double eyeY = 83.42, x = 0.5, z = 10.5;
            if (index <= 3 || index >= 11) {
                boolean mv = index == 1 || index == 3 || index >= 11;
                x = mv ? 3.5 : -2.5; eyeY = mv ? 83.82 : 82.92; z = 6;
            } else if (index <= 6) {
                z = 0.5 + new int[]{30, 34, 60}[index - 4];
            }
            var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.connection.teleport(x, eyeY - player.getEyeHeight(), z, 180, 0);
        }, server);
        state = 3; ticks = 0;
    }

    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.chargerVfxLifecycleAudit") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (state == 4) {
                capture(mc, NAMES[view], view);
                if (++view < NAMES.length) schedule(mc, view);
                else {
                    beforeShader = shader();
                    REPORT.put("beforeReload", shaderProbe(mc));
                    GTLEnhancedcore.LOGGER.info("[CHARGER_VFX_LIFECYCLE] Starting actual resource reload");
                    state = 5; reload = mc.reloadResourcePacks();
                }
            } else if (state == 7) {
                check(shader() != beforeShader, "Resource reload replaced actual custom ShaderInstance");
                REPORT.put("afterReload", shaderProbe(mc));
                capture(mc, "reload-after-day", 12);
                view = 13; schedule(mc, view); state = 8;
            } else if (state == 9) {
                capture(mc, "reload-after-night", 13);
                REPORT.put("status", "PASS"); REPORT.put("checks", checks); write(); done = true;
                GTLEnhancedcore.LOGGER.info("[CHARGER_VFX_LIFECYCLE] PASS {}", checks); mc.stop();
            }
        } catch (Throwable failure) { fail(mc, failure); }
    }

    private static void capture(Minecraft mc, String name, int index) throws Exception {
        check(mc.getOverlay() == null, "No resource overlay at screenshot");
        List<Map<String, Object>> machines = new ArrayList<>();
        var camera = mc.gameRenderer.getMainCamera().getPosition();
        for (int i = 0; i < POS.length; i++) {
            var entity = mc.level.getBlockEntity(POS[i]);
            check(entity != null && mc.getBlockEntityRenderDispatcher().getRenderer(entity) != null, "Actual registered BER");
            var machine = (WirelessChargerMachine) MetaMachine.getMachine(mc.level, POS[i]);
            check(!machine.isWorkingEnabled() && !machine.isActive() && machine.getUserId() == null, "Disabled inactive owner-free fixture");
            Direction expected = index >= 7 && index <= 10 ? new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}[index - 7] : Direction.NORTH;
            check(machine.getFrontFacing() == expected, "Actual synchronized horizontal facing");
            Counter consumer = new Counter(); Map<String, Integer> buffers = new LinkedHashMap<>();
            MultiBufferSource source = type -> { buffers.merge(type.toString(), 1, Integer::sum); return consumer; };
            mc.getBlockEntityRenderDispatcher().render(entity, 0.5F, new PoseStack(), source);
            check(consumer.vertices > 0 && buffers.size() == 1, "Single field buffer has vertices");
            double distance = Math.sqrt(camera.distanceToSqr(POS[i].getX() + .5, POS[i].getY() + 1.02 + (i == 0 ? 1.9 : 2.8), POS[i].getZ() + .5));
            int nearVertices = i == 0 ? 1512 : 2280;
            check(distance > 32 ? consumer.vertices < nearVertices : consumer.vertices == nearVertices, "Real dispatcher selects expected near/far LOD");
            if (index <= 3 && i == index % 2) check(distance >= 5 && distance <= 6, "Target machine viewed from 5-6 blocks");
            machines.add(Map.of("tier", i == 0 ? "LV" : "MV", "distanceToCore", distance, "vertices", consumer.vertices,
                    "lod", distance > 32 ? "far" : "near", "facing", machine.getFrontFacing().getName()));
        }
        shaderProbe(mc);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(DIR.resolve(name + ".png")); }
        VIEWS.add(Map.of("name", name, "camera", List.of(camera.x, camera.y, camera.z), "machines", machines));
        write(); GTLEnhancedcore.LOGGER.info("[CHARGER_VFX_LIFECYCLE] Captured {} {}", name, machines);
    }

    private static ShaderInstance shader() throws Exception {
        var type = Class.forName("com.gtl.enhancedcore.client.renderer.machine.WirelessChargerRenderTypes");
        var field = type.getDeclaredField("shader"); field.setAccessible(true);
        return (ShaderInstance) field.get(null);
    }
    private static Map<String, Object> shaderProbe(Minecraft mc) throws Exception {
        var shader = shader(); check(shader != null, "Registered custom shader instance");
        int program = shader.getId();
        check(GL20.glIsProgram(program) && GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE, "Live linked OpenGL shader program");
        int texture = mc.getTextureManager().getTexture(TEXTURE).getId();
        check(GL11.glIsTexture(texture), "Live OpenGL atlas texture");
        for (String suffix : new String[]{"json", "vsh", "fsh"})
            check(mc.getResourceManager().getResource(GTLEnhancedcore.id("shaders/core/wireless_field." + suffix)).isPresent(), "Shader resource " + suffix);
        String hash;
        try (var stream = mc.getResourceManager().getResource(TEXTURE).orElseThrow().open()) {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        return Map.of("programId", program, "textureId", texture, "textureSha256", hash,
                "shaderIdentity", System.identityHashCode(shader), "shaderName", shader.getName());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    private static void write() throws Exception {
        REPORT.put("views", VIEWS); REPORT.put("checks", checks);
        Files.writeString(DIR.resolve("charger-vfx-lifecycle.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
    }
    private static void fail(Minecraft mc, Throwable failure) {
        done = true; REPORT.put("status", "FAIL"); REPORT.put("error", failure.toString());
        try { write(); } catch (Exception suppressed) { failure.addSuppressed(suppressed); }
        GTLEnhancedcore.LOGGER.error("[CHARGER_VFX_LIFECYCLE] FAIL", failure); mc.stop();
    }
    private static final class Counter implements VertexConsumer {
        int vertices;
        public VertexConsumer vertex(double x, double y, double z) { check(Double.isFinite(x + y + z), "Finite coordinates"); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() { vertices++; }
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    }
}
