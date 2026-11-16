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

import mekhq.gui.gdx.hub.ToeSnapshot.Group;

/**
 * The TO&amp;E table's zoom steps, and what each folds into cards. The zoom snaps from step to step rather than
 * gliding: farthest out, the force with the formations right under it folded into cards; each step in opens the next
 * tier of the tree the player built, whatever its formations are called; then every unit on its counter; and last
 * the same again, closer. Every formation as deep in the tree folds and opens together, so a step reads the same
 * across the whole force.
 *
 * <p>A formation with none under it holds only units, so it stays a card until the units show, as do the units of a
 * formation with others under it as well, folded apart into a card of their own ({@link #attached}), and the units
 * in no formation. The force itself never folds.</p>
 *
 * <p>Any one card can be opened where it stands, or any open formation folded, by hand ({@link #toggle}), without
 * stepping: the rest of the table stays as the step has it. Stepping out forgets that; stepping in keeps what was
 * opened.</p>
 *
 * <p>Each step has its own scale, so everything on the table is drawn at one size on screen there, whatever the
 * force. Plain Java, so it can be tested without GL.</p>
 */
final class ToeFolds {
    /** How many of the interface's pixels a cell takes at the steps with formations folded. */
    static final float CARDS_PIXELS = 44;
    /** How many of the interface's pixels a cell takes at the step with every unit out. */
    static final float UNITS_PIXELS = 44;
    /** How many of the interface's pixels a cell takes at the closest step. */
    static final float CLOSE_PIXELS = 72;
    /** The key of the units in no formation. */
    static final int UNASSIGNED = -1;

    /** How deep in the tree what each key shows stands, the force 0. */
    private final Map<Integer, Integer> depths = new HashMap<>();
    /** The keys that hold only units, which stay cards until the units show. */
    private final Set<Integer> leaves = new HashSet<>();
    /** The formations and cards opened (false) or folded (true) by hand, whatever the step has them. */
    private final Map<Integer, Boolean> chosen = new HashMap<>();
    /** How many tiers of formations stand under the force. */
    private int tiers;
    private int step;

    /** The steps for {@code snapshot}'s tree, at the farthest out. */
    ToeFolds(ToeSnapshot snapshot) {
        walk(snapshot.root(), 0);
        if (!snapshot.unassigned().isEmpty()) {
            depths.put(UNASSIGNED, 1);
            leaves.add(UNASSIGNED);
        }
    }

    /** The key of the card formation {@code id}'s own units fold into apart from the formations under it. */
    static int attached(int id) {
        return -2 - id;
    }

    private void walk(Group group, int depth) {
        depths.put(group.id(), depth);
        tiers = Math.max(tiers, depth);
        if (group.children().isEmpty()) {
            if (depth > 0) {
                leaves.add(group.id());
            }
            return;
        }
        if (!group.units().isEmpty()) {
            depths.put(attached(group.id()), depth + 1);
            leaves.add(attached(group.id()));
        }
        for (Group child : group.children()) {
            walk(child, depth + 1);
        }
    }

    /** How many steps there are: one for each tier of formations, then the units, then close in. */
    int steps() {
        return tiers + 2;
    }

    /** The step it's at: 0 the farthest out, {@link #unitsStep} where the units show. */
    int step() {
        return step;
    }

    /**
     * Goes to step {@code to}, kept within those there are; whether that's a different step. Stepping out forgets
     * what was opened or folded by hand; stepping in keeps what was opened.
     */
    boolean step(int to) {
        int was = step;
        step = Math.max(0, Math.min(to, steps() - 1));
        if (step < was) {
            chosen.clear();
        } else if (step > was) {
            chosen.values().removeIf(folded -> folded);
        }
        return step != was;
    }

    /** Forgets what was opened or folded by hand, everything as the step has it. */
    void forget() {
        chosen.clear();
    }

    /**
     * Takes up the step {@code from} was at and what was opened or folded by hand on it that's still on the table, as
     * the force comes back changed.
     */
    void carry(ToeFolds from) {
        step = Math.max(0, Math.min(from.step, steps() - 1));
        from.chosen.forEach((key, folded) -> {
            if (depths.containsKey(key)) {
                chosen.put(key, folded);
            }
        });
    }

    /** The first step every unit shows at, nothing folded. */
    int unitsStep() {
        return tiers;
    }

    /** Whether every unit shows, nothing folded. */
    boolean units() {
        return step >= tiers;
    }

    /** How many of the interface's pixels a cell takes at the step it's at. */
    float cellPixels() {
        return cellPixels(step);
    }

    /** How many of the interface's pixels a cell takes at step {@code at}. */
    float cellPixels(int at) {
        return at < tiers ? CARDS_PIXELS : at == tiers ? UNITS_PIXELS : CLOSE_PIXELS;
    }

    /** Whether the formation or card keyed {@code key} is folded at the step it's at, or as it was chosen by hand. */
    boolean folded(int key) {
        Boolean chose = chosen.get(key);
        return chose != null ? chose : stepFolds(key);
    }

    /** Whether the step it's at folds what's keyed {@code key}. */
    private boolean stepFolds(int key) {
        Integer depth = depths.get(key);
        return depth != null && !units() && (leaves.contains(key) || depth > step);
    }

    /**
     * Opens what's keyed {@code key} if it's folded, or folds it if it's open, by hand, leaving everything else as it
     * is; whether it could. The force itself never folds.
     */
    boolean toggle(int key) {
        Integer depth = depths.get(key);
        if (depth == null || depth == 0) {
            return false;
        }
        boolean fold = !folded(key);
        if (fold == stepFolds(key)) {
            chosen.remove(key);
        } else {
            chosen.put(key, fold);
        }
        return true;
    }
}
