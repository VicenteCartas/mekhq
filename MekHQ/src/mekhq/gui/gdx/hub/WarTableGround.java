/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MekHQ.
 *
 * MekHQ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MekHQ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MekHQ was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package mekhq.gui.gdx.hub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import mekhq.gui.gdx.hub.WarTableSnapshot.Hex;
import mekhq.gui.gdx.hub.WarTableSnapshot.Sector;
import mekhq.gui.gdx.hub.WarTableSnapshot.Site;

/**
 * The relief of the war table's map, as an operations map's is: gentle, the sector's hexes' heights blended smoothly
 * into each other (water a little below the plains, hills a little above, a mountain a third of a hex high at most),
 * roughened by a little noise the more the higher the ground, and flat under the facilities. Its mesh is each hex cut
 * into small triangles following that relief, and a skirt down to the table round the sector's edge.
 *
 * <p>Heights are in table units, the table's y; a hex is {@code radius} across its corners, laid out as
 * {@link WarTableView} does.</p>
 */
final class WarTableGround {
    private static final float SQRT3 = (float) Math.sqrt(3);
    /** Floats per vertex: position, normal and the hex's column, row and whether it's the skirt. */
    static final int STRIDE = 9;
    /** How finely a hex is cut: each of its six triangles into this many rows. */
    private static final int RINGS = 6;
    /** How far a hex's height reaches into its neighbours', in hexes' radii. */
    private static final float REACH = 1.6f;
    /** How far below the table's top the skirt reaches, in hexes' radii. */
    static final float SKIRT = 0.14f;
    /** Where a facility's flattened pad starts to blend into the ground round it, and ends, in hexes' radii. */
    private static final float PAD_INNER = 0.42f;
    private static final float PAD_OUTER = 0.74f;
    private static final float[] SIDE_X = new float[6];
    private static final float[] SIDE_Z = new float[6];

    static {
        for (int k = 0; k < 6; k++) {
            SIDE_X[k] = MathUtils.cosDeg(30 + 60 * k);
            SIDE_Z[k] = MathUtils.sinDeg(30 + 60 * k);
        }
    }

    private final List<Hex> hexes;
    private final float radius;
    private final float originX;
    private final float originZ;
    private final float base;
    private final int minX;
    private final int minY;
    private final int columns;
    private final int rows;
    /** The index of the hex at each column and row of the sector's box, -1 for none. */
    private final int[] grid;
    /** Each hex's height and roughness, in hexes' radii. */
    private final float[] levels;
    private final float[] roughness;
    /** The facilities' pads: where they lie and how high. */
    private final float[] padX;
    private final float[] padZ;
    private final float[] padY;
    private final float[] tops;

    WarTableGround(Sector sector, float radius, float originX, float originZ, float base) {
        this.hexes = sector.hexes();
        this.radius = radius;
        this.originX = originX;
        this.originZ = originZ;
        this.base = base;
        int loX = Integer.MAX_VALUE;
        int loY = Integer.MAX_VALUE;
        int hiX = Integer.MIN_VALUE;
        int hiY = Integer.MIN_VALUE;
        for (Hex hex : hexes) {
            loX = Math.min(loX, hex.x());
            loY = Math.min(loY, hex.y());
            hiX = Math.max(hiX, hex.x());
            hiY = Math.max(hiY, hex.y());
        }
        if (loX > hiX) {
            loX = loY = hiX = hiY = 0;
        }
        minX = loX;
        minY = loY;
        columns = hiX - loX + 1;
        rows = hiY - loY + 1;
        grid = new int[columns * rows];
        Arrays.fill(grid, -1);
        levels = new float[hexes.size()];
        roughness = new float[hexes.size()];
        for (int i = 0; i < hexes.size(); i++) {
            Hex hex = hexes.get(i);
            grid[(hex.y() - minY) * columns + hex.x() - minX] = i;
            levels[i] = level(hex);
            roughness[i] = roughness(hex);
        }
        List<Site> sites = sector.sites();
        padX = new float[sites.size()];
        padZ = new float[sites.size()];
        padY = new float[sites.size()];
        for (int j = 0; j < sites.size(); j++) {
            Site site = sites.get(j);
            padX[j] = centreX(site.x());
            padZ[j] = centreZ(site.x(), site.y());
            padY[j] = blend(padX[j], padZ[j], false);
        }
        tops = new float[hexes.size()];
        for (int i = 0; i < hexes.size(); i++) {
            Hex hex = hexes.get(i);
            tops[i] = height(centreX(hex.x()), centreZ(hex.x(), hex.y()));
        }
    }

    /** How high {@code hex}'s ground lies, in hexes' radii, varied by where it lies so a range isn't a plateau. */
    static float level(Hex hex) {
        float vary = HoloTableView.hash(hex.x() * 1.31, hex.y() * 2.17);
        if (WarTableAssets.water(hex)) {
            return -0.1f;
        }
        return switch (hex.relief()) {
            case WATER -> -0.1f;
            case LOW -> -0.02f + vary * 0.02f;
            case FLAT -> vary * 0.03f;
            case RAISED -> 0.16f + vary * 0.08f;
            case HIGH -> 0.42f + vary * 0.16f;
        };
    }

    /** How rough {@code hex}'s ground is, how far its noise lifts and sinks it, in hexes' radii. */
    private static float roughness(Hex hex) {
        if (WarTableAssets.water(hex)) {
            return 0;
        }
        if (WarTableAssets.ground(hex).plot() == WarTableAssets.PLOT_BUILT) {
            return 0.004f;
        }
        return switch (hex.relief()) {
            case WATER -> 0;
            case LOW -> 0.012f;
            case FLAT -> 0.018f;
            case RAISED -> 0.05f;
            case HIGH -> 0.14f;
        };
    }

    /** The table's y of the ground at ({@code x}, {@code z}). */
    float height(float x, float z) {
        float h = blend(x, z, true);
        for (int j = 0; j < padX.length; j++) {
            float d = (float) Math.hypot(x - padX[j], z - padZ[j]) / radius;
            if (d < PAD_OUTER) {
                float t = MathUtils.clamp((d - PAD_INNER) / (PAD_OUTER - PAD_INNER), 0, 1);
                h = MathUtils.lerp(padY[j], h, t * t * (3 - 2 * t));
            }
        }
        return base + h * radius;
    }

    /** The table's y of the ground at the centre of the {@code index}-th hex. */
    float top(int index) {
        return index < 0 || index >= tops.length ? base : tops[index];
    }

    /** The hexes' heights near ({@code x}, {@code z}) blended, in hexes' radii, {@code rough}ened or not. */
    private float blend(float x, float z, boolean rough) {
        float qx = (x - originX) / radius;
        float qz = (z - originZ) / radius;
        int column = Math.round(qx / 1.5f);
        float sum = 0;
        float bumps = 0;
        float weights = 0;
        float reach = REACH * REACH;
        for (int c = column - 2; c <= column + 2; c++) {
            if (c < minX || c >= minX + columns) {
                continue;
            }
            float odd = (c & 1) == 1 ? 0.5f : 0;
            int row = Math.round(qz / SQRT3 - odd);
            for (int r = row - 2; r <= row + 2; r++) {
                if (r < minY || r >= minY + rows) {
                    continue;
                }
                int index = grid[(r - minY) * columns + c - minX];
                if (index < 0) {
                    continue;
                }
                float dx = qx - 1.5f * c;
                float dz = qz - SQRT3 * (r + odd);
                float d2 = dx * dx + dz * dz;
                if (d2 >= reach) {
                    continue;
                }
                float w = 1 - d2 / reach;
                w = w * w * w;
                sum += w * levels[index];
                bumps += w * roughness[index];
                weights += w;
            }
        }
        if (weights <= 0) {
            return 0;
        }
        float h = sum / weights;
        if (rough) {
            h += bumps / weights * (ridged(qx * 1.3f, qz * 1.3f) - 0.45f) * 2;
        }
        return h;
    }

    private float centreX(int x) {
        return originX + 1.5f * radius * x;
    }

    private float centreZ(int x, int y) {
        return originZ + SQRT3 * radius * (y + ((x & 1) == 1 ? 0.5f : 0));
    }

    /** The index of the hex across side {@code k} of ({@code x}, {@code y}), -1 for none; odd columns sit lower. */
    int across(int x, int y, int k) {
        int odd = x & 1;
        int nx;
        int ny;
        switch (k) {
            case 1 -> {
                nx = x;
                ny = y + 1;
            }
            case 4 -> {
                nx = x;
                ny = y - 1;
            }
            default -> {
                nx = x + (k == 0 || k == 5 ? 1 : -1);
                ny = y + (k == 0 || k == 2 ? odd : odd - 1);
            }
        }
        if (nx < minX || ny < minY || nx >= minX + columns || ny >= minY + rows) {
            return -1;
        }
        return grid[(ny - minY) * columns + nx - minX];
    }

    /** GPU thread: the ground's meshes, each hex cut into triangles following the relief, and the skirt round it. */
    List<Mesh> meshes() {
        List<Mesh> meshes = new ArrayList<>();
        FloatArray vertices = new FloatArray();
        ShortArray indices = new ShortArray();
        int perHex = 6 * (RINGS + 1) * (RINGS + 2) / 2 + 6 * 2 * (RINGS + 1);
        for (Hex hex : hexes) {
            if (vertices.size / STRIDE + perHex > Short.MAX_VALUE * 2 - 2) {
                meshes.add(mesh(vertices, indices));
                vertices.clear();
                indices.clear();
            }
            hex(hex, vertices, indices);
        }
        if (vertices.size > 0) {
            meshes.add(mesh(vertices, indices));
        }
        return meshes;
    }

    private void hex(Hex hex, FloatArray vertices, ShortArray indices) {
        float cx = centreX(hex.x());
        float cz = centreZ(hex.x(), hex.y());
        for (int k = 0; k < 6; k++) {
            float ax = cx + radius * MathUtils.cosDeg(60 * k);
            float az = cz + radius * MathUtils.sinDeg(60 * k);
            float bx = cx + radius * MathUtils.cosDeg(60 * (k + 1));
            float bz = cz + radius * MathUtils.sinDeg(60 * (k + 1));
            int first = vertices.size / STRIDE;
            int[][] at = new int[RINGS + 1][];
            for (int a = 0; a <= RINGS; a++) {
                at[a] = new int[RINGS + 1 - a];
                for (int b = 0; b <= RINGS - a; b++) {
                    float x = cx + (ax - cx) * a / RINGS + (bx - cx) * b / RINGS;
                    float z = cz + (az - cz) * a / RINGS + (bz - cz) * b / RINGS;
                    at[a][b] = vertices.size / STRIDE - first;
                    ground(vertices, x, z, hex);
                }
            }
            for (int a = 0; a < RINGS; a++) {
                for (int b = 0; b < RINGS - a; b++) {
                    triangle(indices, first, at[a][b], at[a + 1][b], at[a][b + 1]);
                    if (b < RINGS - a - 1) {
                        triangle(indices, first, at[a + 1][b], at[a + 1][b + 1], at[a][b + 1]);
                    }
                }
            }
            if (across(hex.x(), hex.y(), k) < 0) {
                skirt(vertices, indices, hex, ax, az, bx, bz, k);
            }
        }
    }

    private void ground(FloatArray vertices, float x, float z, Hex hex) {
        float e = radius * 0.06f;
        float dx = (height(x + e, z) - height(x - e, z)) / (2 * e);
        float dz = (height(x, z + e) - height(x, z - e)) / (2 * e);
        float length = (float) Math.sqrt(dx * dx + 1 + dz * dz);
        vertices.addAll(x, height(x, z), z, -dx / length, 1 / length, -dz / length, hex.x(), hex.y(), 0);
    }

    /** The skirt under side {@code k} of {@code hex}, from corner a to corner b, down to below the table's top. */
    private void skirt(FloatArray vertices, ShortArray indices, Hex hex, float ax, float az, float bx, float bz,
          int k) {
        int first = vertices.size / STRIDE;
        float bottom = base - SKIRT * radius;
        for (int s = 0; s <= RINGS; s++) {
            float x = MathUtils.lerp(ax, bx, s / (float) RINGS);
            float z = MathUtils.lerp(az, bz, s / (float) RINGS);
            vertices.addAll(x, height(x, z), z, SIDE_X[k], 0, SIDE_Z[k], hex.x(), hex.y(), 1);
            vertices.addAll(x, bottom, z, SIDE_X[k], 0, SIDE_Z[k], hex.x(), hex.y(), 1);
        }
        for (int s = 0; s < RINGS; s++) {
            triangle(indices, first, 2 * s, 2 * s + 1, 2 * s + 2);
            triangle(indices, first, 2 * s + 1, 2 * s + 3, 2 * s + 2);
        }
    }

    private static void triangle(ShortArray indices, int first, int a, int b, int c) {
        indices.add((short) (first + a));
        indices.add((short) (first + b));
        indices.add((short) (first + c));
    }

    private static Mesh mesh(FloatArray vertices, ShortArray indices) {
        Mesh mesh = new Mesh(true, vertices.size / STRIDE, indices.size, new VertexAttributes(
              VertexAttribute.Position(), VertexAttribute.Normal(),
              new VertexAttribute(VertexAttributes.Usage.Generic, 3, "a_hex")));
        mesh.setVertices(vertices.items, 0, vertices.size);
        mesh.setIndices(indices.items, 0, indices.size);
        return mesh;
    }

    /** Ridged noise, 0 to about 1: sharp crests along its creases, as weathered ground has. */
    private static float ridged(float x, float z) {
        float sum = 0;
        float weight = 0.5f;
        float total = 0;
        for (int octave = 0; octave < 4; octave++) {
            float n = 1 - Math.abs(noise(x, z) * 2 - 1);
            sum += weight * n * n;
            total += weight;
            float turned = x * 0.8f - z * 0.6f;
            z = (x * 0.6f + z * 0.8f) * 2.03f + 17;
            x = turned * 2.03f + 17;
            weight *= 0.5f;
        }
        return sum / total;
    }

    private static float noise(float x, float z) {
        int ix = MathUtils.floor(x);
        int iz = MathUtils.floor(z);
        float fx = x - ix;
        float fz = z - iz;
        float ux = fx * fx * (3 - 2 * fx);
        float uz = fz * fz * (3 - 2 * fz);
        float a = lattice(ix, iz);
        float b = lattice(ix + 1, iz);
        float c = lattice(ix, iz + 1);
        float d = lattice(ix + 1, iz + 1);
        return MathUtils.lerp(MathUtils.lerp(a, b, ux), MathUtils.lerp(c, d, ux), uz);
    }

    private static float lattice(int x, int z) {
        int h = x * 374_761_393 + z * 668_265_263;
        h = (h ^ (h >>> 13)) * 1_274_126_177;
        return ((h ^ (h >>> 16)) & 0xFF_FFFF) / (float) 0xFF_FFFF;
    }
}
