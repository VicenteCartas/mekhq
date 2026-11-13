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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import mekhq.gui.gdx.hub.ToeSnapshot.Group;

/**
 * Which formations the TO&amp;E table folds into cards at a zoom. Zooming out, a formation's units grow too small to
 * read, so it folds into a card that sums them up; further out its parent folds in turn, and so on up the tree the
 * player built, whatever its formations are called. The force itself never folds.
 *
 * <p>Each formation folds below its own number of pixels to a cell: one holding only units below
 * {@link #UNITS_FOLD}, one over others a step below the last of them to fold, so its parts have all folded first.
 * It folds a little under that and opens a little over, so it doesn't flicker as the zoom eases in. A formation
 * with units of its own as well as others under it folds those units apart, into a card of their own
 * ({@link #attached}).</p>
 *
 * <p>Plain Java, so it can be tested without GL.</p>
 */
final class ToeFolds {
    /** How many pixels a cell takes on screen below which a formation holding only units folds. */
    static final float UNITS_FOLD = 34;
    /** How far below the first of its children to fold a formation folds. */
    static final float STEP = 0.8f;
    /** How far past its fold the zoom must go to fold or open a formation. */
    static final float HYSTERESIS = 1.06f;
    /** The key of the units in no formation. */
    static final int UNASSIGNED = -1;

    private final Map<Integer, Float> thresholds = new HashMap<>();
    private final Set<Integer> folded = new HashSet<>();

    /** The folds for {@code snapshot}'s tree; nothing folded yet. */
    ToeFolds(ToeSnapshot snapshot) {
        Group root = snapshot.root();
        if (!root.units().isEmpty() && !root.children().isEmpty()) {
            thresholds.put(attached(root.id()), UNITS_FOLD);
        }
        for (Group child : root.children()) {
            threshold(child);
        }
        if (!snapshot.unassigned().isEmpty()) {
            thresholds.put(UNASSIGNED, UNITS_FOLD);
        }
    }

    /** The key of the card formation {@code id}'s own units fold into apart from the formations under it. */
    static int attached(int id) {
        return -2 - id;
    }

    private float threshold(Group group) {
        float last = Float.MAX_VALUE;
        if (!group.units().isEmpty() && !group.children().isEmpty()) {
            thresholds.put(attached(group.id()), UNITS_FOLD);
            last = UNITS_FOLD;
        }
        for (Group child : group.children()) {
            last = Math.min(last, threshold(child));
        }
        float threshold = group.children().isEmpty() ? UNITS_FOLD : STEP * last;
        thresholds.put(group.id(), threshold);
        return threshold;
    }

    /** Whether the formation or card keyed {@code key} is folded. */
    boolean folded(int key) {
        return folded.contains(key);
    }

    /** The pixels to a cell below which {@code key} folds; 0 if it never does. */
    float threshold(int key) {
        return thresholds.getOrDefault(key, 0f);
    }

    /** Folds exactly what folds at {@code cellPixels}, with no allowance either way. */
    void reset(float cellPixels) {
        folded.clear();
        for (Map.Entry<Integer, Float> entry : thresholds.entrySet()) {
            if (cellPixels < entry.getValue()) {
                folded.add(entry.getKey());
            }
        }
    }

    /** Folds and opens what the zoom has gone well past at {@code cellPixels}; whether anything changed. */
    boolean update(float cellPixels) {
        boolean changed = false;
        for (Map.Entry<Integer, Float> entry : thresholds.entrySet()) {
            int key = entry.getKey();
            float threshold = entry.getValue();
            if (folded.contains(key) ? cellPixels > threshold * HYSTERESIS : cellPixels < threshold / HYSTERESIS) {
                changed |= folded.contains(key) ? folded.remove(key) : folded.add(key);
            }
        }
        return changed;
    }

    /** Every zoom something folds at, from the closest in to the farthest out. */
    float[] steps() {
        TreeSet<Float> distinct = new TreeSet<>(thresholds.values());
        float[] steps = new float[distinct.size()];
        int i = steps.length;
        for (float threshold : distinct) {
            steps[--i] = threshold;
        }
        return steps;
    }

    /** The farthest-out zoom anything folds at short of the force itself: where everything is folded. */
    float lowest() {
        float lowest = Float.MAX_VALUE;
        for (float threshold : thresholds.values()) {
            lowest = Math.min(lowest, threshold);
        }
        return lowest == Float.MAX_VALUE ? UNITS_FOLD : lowest;
    }
}
