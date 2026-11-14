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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.Nullable;
import mekhq.campaign.force.FormationSlot;
import mekhq.campaign.force.FormationTemplate;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;

/**
 * How a formation's units sit in the places it's authorized ({@link FormationSlot}): the slots left vacant, and the
 * units that don't fit one, whether nothing fits them or the formation's over strength. A formation authorizing nothing
 * has neither. Plain Java over a {@link ToeSnapshot}.
 */
final class ToeSlots {
    /** The slots of a formation nobody fills, in the order it lists them, and the units in none of its slots. */
    record Fill(List<FormationSlot> vacant, Set<UUID> misfits) {
        static final Fill NONE = new Fill(List.of(), Set.of());

        boolean misfit(Card card) {
            return misfits.contains(card.id());
        }
    }

    /** How the units being carried to a formation would sit in its slots. */
    enum Fit {
        /** It authorizes nothing, so anything goes. */
        OPEN,
        /** Each of them would fill a slot left vacant. */
        FITS,
        /** Some would be in no slot. */
        MISFITS
    }

    private ToeSlots() {
    }

    /**
     * {@code slots} brought to {@code template}'s strength: those set already keep their kind and weight class, so a
     * lance of lights and mediums made a star keeps them, and any more are for whichever kind most of {@code slots}
     * are, else most of {@code units}, else Meks, of any weight.
     */
    static List<FormationSlot> template(FormationTemplate template, List<FormationSlot> slots, List<Card> units) {
        int size = template.size();
        List<FormationSlot> shaped = new ArrayList<>(slots.subList(0, Math.min(slots.size(), size)));
        FormationSlot more = new FormationSlot(mostly(slots.isEmpty() ? units.stream().map(Card::fit).toList()
              : slots), FormationSlot.Weight.ANY);
        while (shaped.size() < size) {
            shaped.add(more);
        }
        return List.copyOf(shaped);
    }

    private static FormationSlot.Kind mostly(List<FormationSlot> slots) {
        int[] counts = new int[FormationSlot.Kind.values().length];
        FormationSlot.Kind most = FormationSlot.Kind.MEK;
        for (FormationSlot slot : slots) {
            counts[slot.kind().ordinal()]++;
        }
        for (FormationSlot.Kind kind : FormationSlot.Kind.values()) {
            if (counts[kind.ordinal()] > counts[most.ordinal()]) {
                most = kind;
            }
        }
        return most;
    }

    /** How {@code group}'s own units sit in its slots. */
    static Fill of(Group group) {
        if (group.slots().isEmpty()) {
            return Fill.NONE;
        }
        List<Card> units = group.units();
        int[] seats = FormationSlot.assign(group.slots(), units.stream().map(Card::fit).toList());
        boolean[] taken = new boolean[group.slots().size()];
        Set<UUID> misfits = new HashSet<>();
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] < 0) {
                misfits.add(units.get(i).id());
            } else {
                taken[seats[i]] = true;
            }
        }
        List<FormationSlot> vacant = new ArrayList<>();
        for (int i = 0; i < taken.length; i++) {
            if (!taken[i]) {
                vacant.add(group.slots().get(i));
            }
        }
        return new Fill(List.copyOf(vacant), Set.copyOf(misfits));
    }

    /** How many slots are vacant and how many units fit none, in {@code group} and everything under it. */
    static int[] tally(Group group) {
        int[] counts = new int[2];
        add(group, counts, true);
        return counts;
    }

    /** As {@link #tally(Group)}, of {@code group}'s own units and slots only. */
    static int[] own(Group group) {
        int[] counts = new int[2];
        add(group, counts, false);
        return counts;
    }

    private static void add(Group group, int[] counts, boolean under) {
        Fill fill = of(group);
        counts[0] += fill.vacant().size();
        counts[1] += fill.misfits().size();
        if (under) {
            for (Group child : group.children()) {
                add(child, counts, true);
            }
        }
    }

    /**
     * How {@code units} put into {@code target} would sit in its slots, beside what's there already: whether every one
     * of them finds a slot without one already filled losing it.
     */
    static Fit fit(@Nullable Group target, List<Card> units) {
        if (target == null || target.slots().isEmpty() || units.isEmpty()) {
            return Fit.OPEN;
        }
        Set<UUID> moving = new HashSet<>();
        for (Card card : units) {
            moving.add(card.id());
        }
        List<FormationSlot> staying = new ArrayList<>();
        for (Card card : target.units()) {
            if (!moving.contains(card.id())) {
                staying.add(card.fit());
            }
        }
        List<FormationSlot> after = new ArrayList<>(staying);
        for (Card card : units) {
            after.add(card.fit());
        }
        return seated(target.slots(), after) - seated(target.slots(), staying) == units.size() ? Fit.FITS
              : Fit.MISFITS;
    }

    private static int seated(List<FormationSlot> slots, List<FormationSlot> units) {
        int count = 0;
        for (int seat : FormationSlot.assign(slots, units)) {
            count += seat >= 0 ? 1 : 0;
        }
        return count;
    }
}
