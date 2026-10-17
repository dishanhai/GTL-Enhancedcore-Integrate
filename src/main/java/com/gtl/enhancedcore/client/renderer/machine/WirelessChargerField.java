package com.gtl.enhancedcore.client.renderer.machine;

/** Reusable, allocation-free induction ribbons, in coordinates relative to the top coil. */
public final class WirelessChargerField {
    private static final double TAU = Math.PI * 2;

    private WirelessChargerField() {}

    @FunctionalInterface
    public interface VertexSink {
        void vertex(float x, float y, float z, float u, float v, int r, int g, int b, int a);
    }

    public static double coreCenterHeight(boolean mv) { return mv ? 2.8 : 1.9; }
    public static double maxRadius(boolean mv) { return mv ? 2.15 : 1.55; }
    public static double maxHeight(boolean mv) { return mv ? 4.8 : 3.3; }

    public static Mesh create(boolean mv, boolean far) {
        Mesh mesh = new Mesh(mv, far);
        update(mesh, 0);
        return mesh;
    }

    /** Share one mesh per tier/LOD, updating once for a given render time. No frame allocations. */
    public static void update(Mesh mesh, double time) {
        if (!Double.isFinite(time)) throw new IllegalArgumentException("Finite animation time required");
        mesh.size = 0;
        boolean mv = mesh.mv;
        double center = coreCenterHeight(mv);
        int rings = mv ? 3 : 2;
        for (int ring = 0; ring < rings; ring++) {
            double angle = TAU * phase(time, 420 + ring * 100) * (ring % 2 == 0 ? 1 : -1) + ring * 1.9;
            double tilt = ring == 0 ? 0.43 : ring == 1 ? -0.55 : 0.72;
            annulus(mesh, mesh.circle, mv ? 1.5 : 1.05, center + (ring - (rings - 1) * 0.5) * 0.13,
                    mv ? 0.066 : 0.050, angle, tilt, 0.65,
                    phase(time, 160 + ring * 33), mv ? 138 : 124, mv ? 116 : 128, mv ? 3 : 2);
        }

        // Unbroken magnetic arches leave and return to the physical top coil.
        int lines = mv ? 6 : 4;
        double drift = TAU * phase(time, 1400);
        double packet = phase(time, mv ? 150 : 190);
        double breathing = 1 + 0.025 * Math.sin(TAU * phase(time, 270));
        for (int line = 0; line < lines; line++) {
            double angle = drift + Math.PI * line / lines;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            for (int i = 0; i < mesh.fluxSegments; i++) {
                int p = i * 2, q = p + 2;
                double r0 = mesh.flux[p] * breathing, r1 = mesh.flux[q] * breathing;
                double u0 = (double) i / mesh.fluxSegments, u1 = (double) (i + 1) / mesh.fluxSegments;
                double pulse0 = pulse(u0, packet + (double) line / lines, 0.13);
                double pulse1 = pulse(u1, packet + (double) line / lines, 0.13);
                mesh.ribbon(r0 * cos, mesh.flux[p + 1], r0 * sin,
                        r1 * cos, mesh.flux[q + 1], r1 * sin, mv ? 0.042 : 0.030,
                        u0, u1, (int) (62 + pulse0 * (mv ? 165 : 130)),
                        (int) (62 + pulse1 * (mv ? 165 : 130)), 0.35);
            }
        }

        // Narrow helical streams carry softer light upwards from the coil.
        int streams = mv ? 3 : 2;
        double streamRotation = TAU * phase(time, mv ? 310 : 380);
        for (int stream = 0; stream < streams; stream++) {
            double angle = streamRotation + TAU * stream / streams;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double head = phase(time, mv ? 115 : 145) + (double) stream / streams;
            for (int i = 0; i < mesh.streamSegments; i++) {
                int p = i * 3, q = p + 3;
                double x0 = mesh.stream[p], z0 = mesh.stream[p + 2];
                double x1 = mesh.stream[q], z1 = mesh.stream[q + 2];
                double u0 = (double) i / mesh.streamSegments, u1 = (double) (i + 1) / mesh.streamSegments;
                mesh.ribbon(x0 * cos - z0 * sin, mesh.stream[p + 1], x0 * sin + z0 * cos,
                        x1 * cos - z1 * sin, mesh.stream[q + 1], x1 * sin + z1 * cos,
                        mv ? 0.052 : 0.038, u0, u1,
                        (int) ((72 + 166 * pulse(u0, head, 0.20)) * Math.min(1, (1 - u0) * 5)),
                        (int) ((72 + 166 * pulse(u1, head, 0.20)) * Math.min(1, (1 - u1) * 5)), 0.8);
            }
        }

        // Fade both sides of each wave's wrap to zero before its radius resets.
        for (int wave = 0; wave < (mv ? 2 : 1); wave++) {
            double progress = phase(time + wave * 95, 190);
            double fade = 16 * progress * progress * (1 - progress) * (1 - progress);
            annulus(mesh, mesh.waveCircle, 0.25 + progress * (mv ? 1.82 : 1.22),
                    0.11 + progress * 0.10, mv ? 0.075 : 0.060, 0, 0, 0.15,
                    0, (int) (fade * (mv ? 84 : 62)), 0, 0);
        }
        annulus(mesh, mesh.sourceCircle, mv ? 0.31 : 0.27, 0.018,
                mv ? 0.052 : 0.040, 0, 0, 0.8, phase(time, 200), 97, 90, 2);
    }

    private static double phase(double time, double period) {
        double remainder = time % period;
        return (remainder < 0 ? remainder + period : remainder) / period;
    }

    /** Periodic smooth light packet, with no random frames or per-vertex trigonometry. */
    private static double pulse(double position, double head, double width) {
        double delta = position - head;
        delta -= Math.floor(delta + 0.5);
        double normalized = delta / width;
        if (Math.abs(normalized) >= 1) return 0;
        double envelope = 1 - normalized * normalized;
        return envelope * envelope;
    }

    private static float[] circle(int segments) {
        float[] points = new float[(segments + 1) * 2];
        for (int i = 0; i <= segments; i++) {
            points[i * 2] = (float) Math.cos(TAU * i / segments);
            points[i * 2 + 1] = (float) Math.sin(TAU * i / segments);
        }
        return points;
    }

    private static void annulus(Mesh mesh, float[] circle, double radius, double height, double width,
                                double rotation, double tilt, double white, double head,
                                int baseAlpha, int packetAlpha, int packets) {
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        double tiltCos = Math.cos(tilt), tiltSin = Math.sin(tilt);
        int segments = circle.length / 2 - 1;
        for (int i = 0; i < segments; i++) {
            double u0 = (double) i / segments, u1 = (double) (i + 1) / segments;
            int a0 = baseAlpha + (int) (packetAlpha * pulse(u0 * packets, head, 0.17));
            int a1 = baseAlpha + (int) (packetAlpha * pulse(u1 * packets, head, 0.17));
            double x0 = circle[i * 2] * tiltCos, y0 = circle[i * 2] * tiltSin, z0 = circle[i * 2 + 1];
            double x1 = circle[(i + 1) * 2] * tiltCos, y1 = circle[(i + 1) * 2] * tiltSin, z1 = circle[(i + 1) * 2 + 1];
            double rx0 = x0 * cos - z0 * sin, rz0 = x0 * sin + z0 * cos;
            double rx1 = x1 * cos - z1 * sin, rz1 = x1 * sin + z1 * cos;
            // Constant-U texture luminance keeps these small arcs visually joined.
            mesh.vertex(rx0 * (radius - width), height + y0 * (radius - width), rz0 * (radius - width), 0, 0, a0, white);
            mesh.vertex(rx1 * (radius - width), height + y1 * (radius - width), rz1 * (radius - width), 1, 0, a1, white);
            mesh.vertex(rx1 * (radius + width), height + y1 * (radius + width), rz1 * (radius + width), 1, 1, a1, white);
            mesh.vertex(rx0 * (radius + width), height + y0 * (radius + width), rz0 * (radius + width), 0, 1, a0, white);
        }
    }

    public static final class Mesh {
        private final boolean mv;
        private final float[] positionsUv;
        private final int[] colors;
        private final float[] circle, waveCircle, sourceCircle, flux, stream;
        private final int fluxSegments, streamSegments;
        private int size;

        private Mesh(boolean mv, boolean far) {
            this.mv = mv;
            int rings = mv ? 3 : 2, lines = mv ? 6 : 4, streams = mv ? 3 : 2;
            int ringSegments = far ? 16 : 48, waveSegments = far ? 12 : 40;
            int sourceSegments = far ? 12 : 32;
            fluxSegments = far ? 7 : 20;
            streamSegments = far ? 4 : 12;
            int capacity = 4 * (rings * ringSegments + lines * fluxSegments * 2
                    + streams * streamSegments * 2 + (mv ? 2 : 1) * waveSegments + sourceSegments);
            positionsUv = new float[capacity * 5];
            colors = new int[capacity];
            circle = circle(ringSegments);
            waveCircle = circle(waveSegments);
            sourceCircle = circle(sourceSegments);
            flux = new float[(fluxSegments + 1) * 2];
            for (int i = 0; i <= fluxSegments; i++) {
                double t = (double) i / fluxSegments;
                flux[i * 2] = (float) (0.24 * Math.cos(Math.PI * t) + (mv ? 1.62 : 1.12) * Math.sin(TAU * t));
                flux[i * 2 + 1] = (float) ((mv ? 4.5 : 3.14) * Math.sin(Math.PI * t));
            }
            stream = new float[(streamSegments + 1) * 3];
            for (int i = 0; i <= streamSegments; i++) {
                double t = (double) i / streamSegments;
                double radius = 0.13 + t * (mv ? 0.30 : 0.20);
                stream[i * 3] = (float) (radius * Math.cos(TAU * t * 0.75));
                stream[i * 3 + 1] = (float) (t * (coreCenterHeight(mv) + 0.40));
                stream[i * 3 + 2] = (float) (radius * Math.sin(TAU * t * 0.75));
            }
        }

        public int vertexCount() { return size; }
        public int capacity() { return colors.length; }

        public void emit(VertexSink sink) {
            for (int i = 0, p = 0; i < size; i++, p += 5) {
                int color = colors[i];
                sink.vertex(positionsUv[p], positionsUv[p + 1], positionsUv[p + 2],
                        positionsUv[p + 3], positionsUv[p + 4], color >>> 24, (color >>> 16) & 255,
                        (color >>> 8) & 255, color & 255);
            }
        }

        private void vertex(double x, double y, double z, double u, double v, int alpha, double white) {
            int p = size * 5;
            positionsUv[p] = (float) x;
            positionsUv[p + 1] = (float) Math.max(0, y);
            positionsUv[p + 2] = (float) z;
            positionsUv[p + 3] = (float) u;
            positionsUv[p + 4] = (float) v;
            int r = (int) ((mv ? 38 : 116) + white * (mv ? 150 : 104));
            int g = (int) ((mv ? 113 : 189) + white * (mv ? 108 : 51));
            colors[size++] = (r << 24) | (g << 16) | (255 << 8) | Math.min(255, alpha);
        }

        private void ribbon(double x0, double y0, double z0, double x1, double y1, double z1,
                            double width, double u0, double u1, int a0, int a1, double white) {
            double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double wx = horizontal < 1e-9 ? width : -dz / horizontal * width;
            double wz = horizontal < 1e-9 ? 0 : dx / horizontal * width;
            strip(x0, y0, z0, x1, y1, z1, wx, 0, wz, u0, u1, a0, a1, white);
            double vx = dy * wz, vy = dz * wx - dx * wz, vz = -dy * wx;
            double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double scale = width / Math.max(1e-12, length);
            strip(x0, y0, z0, x1, y1, z1, vx * scale, vy * scale, vz * scale, u0, u1, a0, a1, white);
        }

        private void strip(double x0, double y0, double z0, double x1, double y1, double z1,
                           double wx, double wy, double wz, double u0, double u1, int a0, int a1, double white) {
            vertex(x0 - wx, y0 - wy, z0 - wz, u0, 0, a0, white);
            vertex(x1 - wx, y1 - wy, z1 - wz, u1, 0, a1, white);
            vertex(x1 + wx, y1 + wy, z1 + wz, u1, 1, a1, white);
            vertex(x0 + wx, y0 + wy, z0 + wz, u0, 1, a0, white);
        }
    }
}
