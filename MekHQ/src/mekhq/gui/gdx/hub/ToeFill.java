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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.Nullable;
import mekhq.campaign.force.FormationSlot;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;

/**
 * The units that could fill a formation's vacant slot, as its picker offers them: each one whose kind and weight the
 * slot admits, wherever it is now, the best choice first. Plain Java over a {@link ToeSnapshot}.
 */
final class ToeFill {
    /**
     * A unit the slot admits: the formation it's in now (null if in none), whether taking it leaves a slot vacant
     * there, whether it's somewhere other than where the formation is, and whether with it the formation meets the
     * formation rule it's meant to and doesn't yet.
     */
    record Candidate(Card card, @Nullable Group from, boolean leavesVacancy, boolean elsewhere, boolean completes) {
    }

    private ToeFill() {
    }

    /**
     * The units that could fill {@code slot} in {@code target}: neither out on a deployment nor in it already. Those
     * that make the formation meet its rule come first, then the unassigned, then those that leave no slot vacant
     * behind them, those at the formation's place before those elsewhere, sound and fit before ailing, then by name.
     * Judges the rule through MegaMek, so call it on one thread only.
     */
    static List<Candidate> candidates(ToeSnapshot shown, Group target, FormationSlot slot) {
        Map<UUID, Group> homes = new HashMap<>();
        index(shown.root(), homes);
        int place = place(shown, target);
        boolean unmet = target.rule() != null && !ToeRules.meets(target, shown.summaries());
        List<Candidate> candidates = new ArrayList<>();
        for (Card card : shown.allUnits()) {
            Group from = homes.get(card.id());
            if (card.deployed() || from != null && from.id() == target.id() || !slot.admits(card.fit())) {
                continue;
            }
            boolean leaves = from != null && vacatedBy(from, card);
            boolean completes = unmet && ToeRules.meetsWith(target, card, shown.summaries());
            candidates.add(new Candidate(card, from, leaves, card.place() != place, completes));
        }
        candidates.sort(Comparator.comparing((Candidate candidate) -> !candidate.completes())
              .thenComparing(candidate -> candidate.from() != null)
              .thenComparing(Candidate::leavesVacancy)
              .thenComparing(Candidate::elsewhere)
              .thenComparing(candidate -> ToeSnapshot.Issue.first(candidate.card()) != null)
              .thenComparing(candidate -> !candidate.card().ready())
              .thenComparing(candidate -> candidate.card().name()));
        return candidates;
    }

    /**
     * Where {@code target} is: the place most of its units are at, or its nearest formation above's if it has none;
     * the main force if nothing above it has any either.
     */
    static int place(ToeSnapshot shown, Group target) {
        List<Group> path = new ArrayList<>();
        if (!path(shown.root(), target, path)) {
            path.add(target);
        }
        for (int i = path.size() - 1; i >= 0; i--) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (Card card : path.get(i).allUnits()) {
                counts.merge(card.place(), 1, Integer::sum);
            }
            if (!counts.isEmpty()) {
                return counts.entrySet().stream()
                      .max(Map.Entry.<Integer, Integer>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                      .orElseThrow()
                      .getKey();
            }
        }
        return ToeSnapshot.MAIN_FORCE;
    }

    /** Whether taking {@code card} out of {@code from} leaves one of its slots nobody fills. */
    private static boolean vacatedBy(Group from, Card card) {
        if (from.slots().isEmpty()) {
            return false;
        }
        List<Card> rest = new ArrayList<>(from.units());
        rest.removeIf(unit -> unit.id().equals(card.id()));
        Group without = from.with(from.children(), rest);
        return ToeSlots.of(without).vacant().size() > ToeSlots.of(from).vacant().size();
    }

    private static void index(Group group, Map<UUID, Group> homes) {
        for (Card card : group.units()) {
            homes.put(card.id(), group);
        }
        for (Group child : group.children()) {
            index(child, homes);
        }
    }

    private static boolean path(Group at, Group target, List<Group> path) {
        path.add(at);
        if (at == target) {
            return true;
        }
        for (Group child : at.children()) {
            if (path(child, target, path)) {
                return true;
            }
        }
        path.remove(path.size() - 1);
        return false;
    }
}
