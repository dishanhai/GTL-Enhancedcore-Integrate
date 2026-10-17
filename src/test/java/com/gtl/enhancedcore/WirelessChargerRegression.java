package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.renderer.machine.WirelessChargerField;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Headless geometry/resource checks. These do not measure rendered appearance or client FPS. */
final class WirelessChargerRegression {
    static int run() throws Exception {
        int checks = 0;
        List<Vertex> lv = snapshot(WirelessChargerField.create(false, false), 23.5);
        List<Vertex> mvVertices = snapshot(WirelessChargerField.create(true, false), 23.5);
        check(mvVertices.size() > lv.size(), "MV has a stronger, more layered field"); checks++;
        check(maxHeight(mvVertices) > maxHeight(lv) + 1, "Tier field heights differ visibly"); checks++;
        check(mvVertices.get(0).r < lv.get(0).r && mvVertices.get(0).g < lv.get(0).g,
                "MV cobalt and LV pale blue retain distinct palettes"); checks++;
        for (boolean mv : new boolean[]{false, true}) {
            check(WirelessChargerField.coreCenterHeight(mv) == (mv ? 2.8 : 1.9),
                    "Soft core is centered above the top coil"); checks++;
            WirelessChargerField.Mesh near = WirelessChargerField.create(mv, false);
            WirelessChargerField.Mesh far = WirelessChargerField.create(mv, true);
            check(near.vertexCount() <= (mv ? 2600 : 1600), "Near geometry stays inside tier budget"); checks++;
            check(far.vertexCount() <= near.vertexCount() * 0.36
                    && far.vertexCount() >= near.vertexCount() * 0.29,
                    "Far geometry uses roughly one third of the near vertices"); checks++;
            for (WirelessChargerField.Mesh mesh : new WirelessChargerField.Mesh[]{near, far}) {
                int capacity = mesh.capacity();
                for (double time : new double[]{0, 0.5, 95, 189.9999, 190, 10000.25,
                        24000000.25, 0x1.0p40 + 0.25, -0.125}) {
                    List<Vertex> vertices = snapshot(mesh, time);
                    check(vertices.size() == capacity && mesh.capacity() == capacity,
                            "Update reuses fixed mesh capacity without topology changes"); checks++;
                    check(vertices.size() % 4 == 0, "Complete quad topology"); checks++;
                    boolean connectedQuad = false;
                    int[] heightBins = new int[6];
                    double maxY = maxHeight(vertices);
                    for (Vertex v : vertices) {
                        check(Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z)
                                && Float.isFinite(v.u) && Float.isFinite(v.v), "Finite coordinates and UVs"); checks++;
                        check(Math.hypot(v.x, v.z) <= WirelessChargerField.maxRadius(mv)
                                && v.y >= 0 && v.y <= WirelessChargerField.maxHeight(mv),
                                "All geometry remains inside the declared coil-relative bounds"); checks++;
                        check(v.u >= 0 && v.u <= 1 && v.v >= 0 && v.v <= 1, "Normalized soft-strip UVs"); checks++;
                        check(v.a >= 0 && v.a <= 255 && v.r >= 0 && v.r <= 255
                                && v.g >= 0 && v.g <= 255 && v.b == 255, "Valid additive blue RGBA"); checks++;
                        if (v.a > 20) heightBins[Math.min(5, (int) (v.y / maxY * 6))]++;
                    }
                    for (int count : heightBins) {
                        check(count > 0, "Visible geometry connects the entire height instead of a floating globe"); checks++;
                    }
                    for (int i = 0; i < vertices.size(); i += 4) {
                        Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2), d = vertices.get(i + 3);
                        check(areaSquared(a, b, c) > 1e-13 && areaSquared(a, c, d) > 1e-13,
                                "Each emitted quad has two nondegenerate triangles"); checks++;
                        check(a.u == d.u && b.u == c.u && a.v == 0 && b.v == 0 && c.v == 1 && d.v == 1,
                                "Every ribbon has consistent length/width texture mapping"); checks++;
                        double minY = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
                        double topY = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));
                        if (minY <= 0.025 && topY > 0.1 && Math.hypot(a.x, a.z) < 0.4) connectedQuad = true;
                    }
                    check(connectedQuad, "Magnetic ribbons physically start at the machine top coil"); checks++;
                    check(vertices.equals(snapshot(mesh, time)), "Same time reproduces the same mesh"); checks++;
                }
                for (double time : new double[]{123.125, 24000000.125, 0x1.0p40 + 0.125}) {
                    List<Vertex> before = snapshot(mesh, time), after = snapshot(mesh, time + 0.125);
                    check(!before.equals(after), "Partial ticks animate even at very large world times"); checks++;
                    double displacement = 0, visualDelta = 0;
                    for (int i = 0; i < before.size(); i++) {
                        displacement = Math.max(displacement, distance(before.get(i), after.get(i)));
                        visualDelta = Math.max(visualDelta, visualDistance(before.get(i), after.get(i)));
                    }
                    check(displacement > 0.0001 && displacement < 0.025,
                            "Adjacent partial ticks move continuously without random-frame jumps"); checks++;
                    check(visualDelta < 0.10, "Moving light packets have smooth adjacent partial alpha"); checks++;
                }
                for (double wrap : mv ? new double[]{95, 190, 380} : new double[]{190, 380}) {
                    List<Vertex> before = snapshot(mesh, wrap - 0.0001), after = snapshot(mesh, wrap + 0.0001);
                    int hiddenResets = 0;
                    for (int i = 0; i < before.size(); i++) {
                        Vertex a = before.get(i), b = after.get(i);
                        if (distance(a, b) > 0.5) {
                            check(a.a <= 1 && b.a <= 1, "Expanding wave fades before its geometric reset"); checks++;
                            hiddenResets++;
                        }
                        check(visualDistance(a, b) < 0.04,
                                "Wave seam is continuous after weighting positions by visible alpha"); checks++;
                    }
                    check(hiddenResets >= 40, "The seam check actually crosses a resetting wave"); checks++;
                }
            }
            String tier = mv ? "mv" : "lv";
            Path assets = Path.of("src/main/resources/assets/gtl_enhancedcore");
            for (String active : new String[]{"", "_active"}) {
                var model = JsonParser.parseString(Files.readString(assets.resolve(
                        "models/block/machine/" + tier + "_wireless_charger" + active + ".json"))).getAsJsonObject();
                for (var texture : model.getAsJsonObject("textures").entrySet()) {
                    var image = ImageIO.read(assets.resolve("textures/" + texture.getValue().getAsString().split(":", 2)[1] + ".png").toFile());
                    check(image != null && image.getWidth() == 16 && image.getHeight() == 16,
                            "Every machine overlay remains a native 16px texture"); checks++;
                }
            }
        }
        return checks;
    }

    private static List<Vertex> snapshot(WirelessChargerField.Mesh mesh, double time) {
        WirelessChargerField.update(mesh, time);
        List<Vertex> vertices = new ArrayList<>(mesh.vertexCount());
        mesh.emit((x,y,z,u,v,r,g,b,a) -> vertices.add(new Vertex(x,y,z,u,v,r,g,b,a)));
        return vertices;
    }

    private static double maxHeight(List<Vertex> vertices) {
        double max = 0;
        for (Vertex vertex : vertices) max = Math.max(max, vertex.y);
        return max;
    }

    private static double areaSquared(Vertex a, Vertex b, Vertex c) {
        double x1 = b.x - a.x, y1 = b.y - a.y, z1 = b.z - a.z;
        double x2 = c.x - a.x, y2 = c.y - a.y, z2 = c.z - a.z;
        double x = y1 * z2 - z1 * y2, y = z1 * x2 - x1 * z2, z = x1 * y2 - y1 * x2;
        return x * x + y * y + z * z;
    }

    private static double distance(Vertex a, Vertex b) {
        double x = a.x - b.x, y = a.y - b.y, z = a.z - b.z;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double visualDistance(Vertex a, Vertex b) {
        double aa = a.a / 255.0, ba = b.a / 255.0;
        double x = a.x * aa - b.x * ba, y = a.y * aa - b.y * ba, z = a.z * aa - b.z * ba;
        return Math.sqrt(x * x + y * y + z * z) + Math.abs(aa - ba);
    }

    private record Vertex(float x, float y, float z, float u, float v, int r, int g, int b, int a) {}
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
