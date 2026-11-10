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
package mekhq.campaign.universe.territory;

import java.util.Arrays;

/**
 * Who holds space, worked out from the systems they hold: each claim spreads its realm's influence round it, fading to
 * nothing at {@link #REACH}, and space is the realm's whose influence there is strongest, once it passes
 * {@link #THRESHOLD}. Space is sampled every few light years; each sample keeps who holds it and how far it lies from
 * the edge of their ground, measured to where the influences tie between samples, so territory can be drawn crisp and
 * smooth at any scale. Also how disputed the ground is, and with whom. Any thread; never changes once made.
 */
public final class InfluenceMap {
    /** How far a claim's influence reaches, in light years. */
    public static final float REACH = 45;
    /** The influence that makes space a realm's: a lone system holds about 33 light years round it. */
    public static final float THRESHOLD = 0.2f;
    /** How far round a disputed system the dispute shows, in light years. */
    public static final float DISPUTE_REACH = 20;
    /** Distances are kept in sixteenths of a light year. */
    public static final float DISTANCE_SCALE = 16;
    /** The farthest distance kept, in sixteenths of a light year: no edge anywhere near. */
    public static final short FAR = 32000;
    /** A dispute at full strength. */
    public static final short DISPUTED = Short.MAX_VALUE;
    /** The values kept per sample. */
    public static final int CHANNELS = 4;
    private static final float TEXEL = 4;
    private static final int MAX_SIZE = 1024;

    private final float west;
    private final float south;
    private final float texel;
    private final int width;
    private final int height;
    private final short[] samples;

    private InfluenceMap(float west, float south, float texel, int width, int height, short[] samples) {
        this.west = west;
        this.south = south;
        this.texel = texel;
        this.width = width;
        this.height = height;
        this.samples = samples;
    }

    /**
     * Space round the {@code claims} at ({@code x}, {@code y}) light years, each by realm {@code realm} (from 1; 0
     * is no one), disputed by others when {@code disputed}, over the systems between ({@code minX}, {@code minY}) and
     * ({@code maxX}, {@code maxY}).
     */
    public static InfluenceMap of(int claims, float[] x, float[] y, int[] realm, boolean[] disputed, float minX,
          float minY, float maxX, float maxY) {
        float west = minX - REACH;
        float south = minY - REACH;
        float spanX = Math.max(1, maxX + REACH - west);
        float spanY = Math.max(1, maxY + REACH - south);
        float texel = Math.max(TEXEL, Math.max(spanX, spanY) / MAX_SIZE);
        int width = Math.max(1, (int) Math.ceil(spanX / texel));
        int height = Math.max(1, (int) Math.ceil(spanY / texel));
        return new Builder(west, south, texel, width, height).build(claims, x, y, realm, disputed);
    }

    /** Light years west of Terra of the map's western edge. */
    public float west() {
        return west;
    }

    /** Light years rimward of Terra of the map's southern edge. */
    public float south() {
        return south;
    }

    /** The light years between samples. */
    public float texel() {
        return texel;
    }

    /** Samples west to east. */
    public int width() {
        return width;
    }

    /** Samples south to north. */
    public int height() {
        return height;
    }

    /**
     * Every sample, row by row from the south, {@link #CHANNELS} values each: who holds it (a realm from 1, 0 for no
     * one), how far it lies from the edge of their ground in sixteenths of a light year, the realm disputing it (0 for
     * none) and how strongly, to {@link #DISPUTED}. Not to be changed.
     */
    public short[] samples() {
        return samples;
    }

    /** Who holds the sample in {@code column}, {@code row}: a realm from 1, or 0 for no one. */
    public int owner(int column, int row) {
        return samples[(row * width + column) * CHANNELS];
    }

    /** Light years from the sample in {@code column}, {@code row} to the edge of its holder's ground. */
    public float distance(int column, int row) {
        return samples[(row * width + column) * CHANNELS + 1] / DISTANCE_SCALE;
    }

    /** The realm disputing the sample in {@code column}, {@code row}, or 0. */
    public int partner(int column, int row) {
        return samples[(row * width + column) * CHANNELS + 2];
    }

    /** How strongly the sample in {@code column}, {@code row} is disputed, 0 to 1. */
    public float dispute(int column, int row) {
        return samples[(row * width + column) * CHANNELS + 3] / (float) DISPUTED;
    }

    /** The column of the sample nearest {@code x} light years. */
    public int column(float x) {
        return Math.round((x - west) / texel - 0.5f);
    }

    /** The row of the sample nearest {@code y} light years. */
    public int row(float y) {
        return Math.round((y - south) / texel - 0.5f);
    }

    private static final class Builder {
        private final float west;
        private final float south;
        private final float texel;
        private final int width;
        private final int height;
        private final int size;
        private final float[] best;
        private final float[] second;
        private final int[] bestRealm;
        private final int[] secondRealm;

        Builder(float west, float south, float texel, int width, int height) {
            this.west = west;
            this.south = south;
            this.texel = texel;
            this.width = width;
            this.height = height;
            size = width * height;
            best = new float[size];
            second = new float[size];
            bestRealm = new int[size];
            secondRealm = new int[size];
        }

        InfluenceMap build(int claims, float[] x, float[] y, int[] realm, boolean[] disputed) {
            influence(claims, x, y, realm);
            int[] owner = new int[size];
            for (int i = 0; i < size; i++) {
                // Realms tied hold the ground together: one is drawn holding it, the other disputing it
                if (best[i] > THRESHOLD && best[i] >= second[i]) {
                    owner[i] = bestRealm[i];
                }
            }
            float[] distance = edges(owner);
            float[] dispute = dispute(claims, x, y, disputed);
            short[] samples = new short[size * CHANNELS];
            for (int i = 0; i < size; i++) {
                int at = i * CHANNELS;
                samples[at] = (short) owner[i];
                samples[at + 1] = (short) Math.min(FAR, Math.round(distance[i] * texel * DISTANCE_SCALE));
                if (dispute[i] > 0 && owner[i] > 0) {
                    samples[at + 2] = (short) (owner[i] == bestRealm[i] ? secondRealm[i] : bestRealm[i]);
                    samples[at + 3] = (short) Math.round(Math.min(1, dispute[i]) * DISPUTED);
                }
            }
            return new InfluenceMap(west, south, texel, width, height, samples);
        }

        /** Sums each realm's influence and keeps the two strongest realms at every sample. */
        private void influence(int claims, float[] x, float[] y, int[] realm) {
            int realms = 0;
            for (int i = 0; i < claims; i++) {
                realms = Math.max(realms, realm[i] + 1);
            }
            // The claims by realm
            int[] start = new int[realms + 1];
            for (int i = 0; i < claims; i++) {
                start[realm[i] + 1]++;
            }
            for (int r = 0; r < realms; r++) {
                start[r + 1] += start[r];
            }
            int[] order = new int[claims];
            int[] next = Arrays.copyOf(start, realms);
            for (int i = 0; i < claims; i++) {
                order[next[realm[i]]++] = i;
            }
            float[] sum = new float[size];
            int[] stamp = new int[size];
            int[] touched = new int[size];
            for (int r = 1; r < realms; r++) {
                int count = 0;
                for (int n = start[r]; n < start[r + 1]; n++) {
                    int claim = order[n];
                    count = splat(x[claim], y[claim], REACH, r, sum, stamp, touched, count, false);
                }
                for (int n = 0; n < count; n++) {
                    int i = touched[n];
                    float w = sum[i];
                    if (w > best[i]) {
                        second[i] = best[i];
                        secondRealm[i] = bestRealm[i];
                        best[i] = w;
                        bestRealm[i] = r;
                    } else if (w > second[i]) {
                        second[i] = w;
                        secondRealm[i] = r;
                    }
                }
            }
        }

        /**
         * Adds a claim's influence at ({@code x}, {@code y}) to {@code sum}, or keeps the stronger when {@code max},
         * listing in {@code touched} the samples first touched in pass {@code pass}; returns how many are listed.
         */
        private int splat(float x, float y, float reach, int pass, float[] sum, int[] stamp, int[] touched,
              int count, boolean max) {
            float cx = (x - west) / texel - 0.5f;
            float cy = (y - south) / texel - 0.5f;
            float radius = reach / texel;
            int fromColumn = Math.max(0, (int) Math.floor(cx - radius));
            int toColumn = Math.min(width - 1, (int) Math.ceil(cx + radius));
            int fromRow = Math.max(0, (int) Math.floor(cy - radius));
            int toRow = Math.min(height - 1, (int) Math.ceil(cy + radius));
            float scale = 1 / (radius * radius);
            for (int row = fromRow; row <= toRow; row++) {
                float dy = row - cy;
                for (int column = fromColumn; column <= toColumn; column++) {
                    float dx = column - cx;
                    float d2 = (dx * dx + dy * dy) * scale;
                    if (d2 >= 1) {
                        continue;
                    }
                    float w = (1 - d2) * (1 - d2);
                    int i = row * width + column;
                    if (stamp[i] != pass) {
                        stamp[i] = pass;
                        sum[i] = 0;
                        touched[count++] = i;
                    }
                    sum[i] = max ? Math.max(sum[i], w) : sum[i] + w;
                }
            }
            return count;
        }

        /**
         * How far, in samples, each sample lies from the edge of its holder's ground: the edges are placed between
         * neighbours held by others where their holders' influences tie, then carried to every sample by nearest edge
         * point.
         */
        private float[] edges(int[] owner) {
            float[] edgeX = new float[size];
            float[] edgeY = new float[size];
            float[] nearest = new float[size];
            Arrays.fill(nearest, Float.MAX_VALUE);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    int i = row * width + column;
                    if (column + 1 < width && owner[i + 1] != owner[i]) {
                        float t = tie(i, i + 1, owner);
                        offer(i, column, row, column + t, row, edgeX, edgeY, nearest);
                        offer(i + 1, column + 1, row, column + t, row, edgeX, edgeY, nearest);
                    }
                    if (row + 1 < height && owner[i + width] != owner[i]) {
                        float t = tie(i, i + width, owner);
                        offer(i, column, row, column, row + t, edgeX, edgeY, nearest);
                        offer(i + width, column, row + 1, column, row + t, edgeX, edgeY, nearest);
                    }
                }
            }
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    carry(column, row, -1, 0, edgeX, edgeY, nearest);
                    carry(column, row, -1, -1, edgeX, edgeY, nearest);
                    carry(column, row, 0, -1, edgeX, edgeY, nearest);
                    carry(column, row, 1, -1, edgeX, edgeY, nearest);
                }
                for (int column = width - 1; column >= 0; column--) {
                    carry(column, row, 1, 0, edgeX, edgeY, nearest);
                }
            }
            for (int row = height - 1; row >= 0; row--) {
                for (int column = width - 1; column >= 0; column--) {
                    carry(column, row, 1, 0, edgeX, edgeY, nearest);
                    carry(column, row, 1, 1, edgeX, edgeY, nearest);
                    carry(column, row, 0, 1, edgeX, edgeY, nearest);
                    carry(column, row, -1, 1, edgeX, edgeY, nearest);
                }
                for (int column = 0; column < width; column++) {
                    carry(column, row, -1, 0, edgeX, edgeY, nearest);
                }
            }
            for (int i = 0; i < size; i++) {
                nearest[i] = nearest[i] == Float.MAX_VALUE ? Float.MAX_VALUE : (float) Math.sqrt(nearest[i]);
            }
            return nearest;
        }

        /**
         * Where between samples {@code here} and {@code there}, 0 to 1, the influences of their holders tie: no one's
         * is the threshold. Measured between the two holders alone, so a realm tied with another for its ground still
         * has a smooth edge against everyone else.
         */
        private float tie(int here, int there, int[] owner) {
            float atHere = influence(here, owner[here]) - influence(here, owner[there]);
            float atThere = influence(there, owner[here]) - influence(there, owner[there]);
            float span = atHere - atThere;
            return span <= 1e-6f ? 0.5f : Math.clamp(atHere / span, 0, 1);
        }

        /** {@code realm}'s influence at sample {@code i}; no one's is the threshold. */
        private float influence(int i, int realm) {
            if (realm == 0) {
                return THRESHOLD;
            }
            return bestRealm[i] == realm ? best[i] : secondRealm[i] == realm ? second[i] : 0;
        }

        private static void offer(int i, int column, int row, float x, float y, float[] edgeX, float[] edgeY,
              float[] nearest) {
            float dx = x - column;
            float dy = y - row;
            float d2 = dx * dx + dy * dy;
            if (d2 < nearest[i]) {
                nearest[i] = d2;
                edgeX[i] = x;
                edgeY[i] = y;
            }
        }

        /** Offers the sample at {@code column}, {@code row} the edge nearest its neighbour ({@code dx}, {@code dy}) away. */
        private void carry(int column, int row, int dx, int dy, float[] edgeX, float[] edgeY, float[] nearest) {
            int c = column + dx;
            int r = row + dy;
            if (c < 0 || r < 0 || c >= width || r >= height) {
                return;
            }
            int from = r * width + c;
            if (nearest[from] == Float.MAX_VALUE) {
                return;
            }
            offer(row * width + column, column, row, edgeX[from], edgeY[from], edgeX, edgeY, nearest);
        }

        /** How near each sample lies to a disputed claim, 1 on it, falling to 0 at {@link #DISPUTE_REACH}. */
        private float[] dispute(int claims, float[] x, float[] y, boolean[] disputed) {
            float[] dispute = new float[size];
            int[] stamp = new int[size];
            int[] touched = new int[size];
            for (int i = 0; i < claims; i++) {
                if (disputed[i]) {
                    splat(x[i], y[i], DISPUTE_REACH, 1, dispute, stamp, touched, 0, true);
                }
            }
            return dispute;
        }
    }
}
