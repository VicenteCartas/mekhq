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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.Nullable;
import mekhq.gui.gdx.hub.ToeLayout.Block;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;

/**
 * Reorganizing the TO&amp;E table by dragging: what a drag carries, where it may be dropped and the moves a drop makes,
 * by the rules the TO&amp;E has always kept. Nothing out on a scenario or a StratCon track moves, nor does anything
 * move into a formation that is; a formation can't go under itself, the force itself doesn't move, and only units
 * can be taken out of every formation.
 *
 * <p>Plain Java over a {@link ToeSnapshot}, so it can be tested without GL or a campaign.</p>
 */
final class ToeMoves {
    /** Where units go to be in no formation; a formation's id otherwise. */
    static final int UNASSIGNED = ToeFolds.UNASSIGNED;

    /** What a drag carries: a formation, or units. */
    record Load(@Nullable Group formation, List<Card> units) {
        static Load of(Group formation) {
            return new Load(formation, List.of());
        }

        static Load of(List<Card> units) {
            return new Load(null, List.copyOf(units));
        }
    }

    /** What a drop would do: move what's carried, or nothing, and why not. */
    enum Verdict {
        /** It moves. */
        MOVE,
        /** It's already there. */
        HOME,
        /** What's carried is out on a deployment. */
        LOCKED,
        /** Where it would go is out on a deployment. */
        TARGET_LOCKED,
        /** A formation would go under itself. */
        INTO_ITSELF,
        /** The force itself would move. */
        TOP,
        /** A formation would be taken out of the force. */
        NOT_UNASSIGNED;

        boolean moves() {
            return this == MOVE;
        }

        /** Whether it's refused, as a drop that's merely home isn't. */
        boolean refused() {
            return this != MOVE && this != HOME;
        }
    }

    /**
     * A move: {@code units} into formation {@code into} (or out of every one, at {@link #UNASSIGNED}), or, with no
     * units, formation {@code formation} under formation {@code into}.
     */
    record Move(int formation, List<UUID> units, int into) {
        static Move units(List<UUID> units, int into) {
            return new Move(UNASSIGNED, List.copyOf(units), into);
        }

        static Move formation(int formation, int into) {
            return new Move(formation, List.of(), into);
        }

        boolean ofUnits() {
            return !units.isEmpty();
        }
    }

    private final Group root;
    private final Map<Integer, Group> groups = new HashMap<>();
    private final Map<Integer, Integer> parents = new HashMap<>();
    private final Map<UUID, Integer> homes = new HashMap<>();

    ToeMoves(ToeSnapshot snapshot) {
        root = snapshot.root();
        index(root, UNASSIGNED);
        for (Card card : snapshot.unassigned()) {
            homes.put(card.id(), UNASSIGNED);
        }
    }

    private void index(Group group, int parent) {
        groups.put(group.id(), group);
        parents.put(group.id(), parent);
        for (Card card : group.units()) {
            homes.put(card.id(), group.id());
        }
        for (Group child : group.children()) {
            index(child, group.id());
        }
    }

    /** Where a drop onto {@code block} goes: its formation's id, or {@link #UNASSIGNED}. */
    static int target(Block block) {
        if (block.group() != null) {
            return block.group().id();
        }
        // An attached card holds its formation's own units, keyed apart from it by ToeFolds.attached
        return block.key() == ToeFolds.UNASSIGNED ? UNASSIGNED : -2 - block.key();
    }

    /**
     * What a drag picking up {@code block} carries: a formation's card carries the formation (a formation's block,
     * likewise, by its head), an attached card or the unassigned units' card its units. Null for the force itself,
     * which doesn't move, or nothing to carry.
     */
    static @Nullable Load load(Block block) {
        if (block.group() != null) {
            return block.level() == 0 ? null : Load.of(block.group());
        }
        return block.units().isEmpty() ? null : Load.of(block.units());
    }

    /** The formation {@code id}, or null if there's none such. */
    @Nullable Group group(int id) {
        return groups.get(id);
    }

    /** Whether {@code load} is out on a deployment, so it can't be picked up. */
    static boolean locked(Load load) {
        if (load.formation() != null) {
            return load.formation().deployed();
        }
        for (Card card : load.units()) {
            if (card.deployed()) {
                return true;
            }
        }
        return false;
    }

    /** What dropping {@code load} into {@code into} would do. */
    Verdict judge(Load load, int into) {
        Group target = into == UNASSIGNED ? null : groups.get(into);
        if (into != UNASSIGNED && target == null) {
            return Verdict.TARGET_LOCKED;
        }
        Group formation = load.formation();
        if (formation != null && formation.id() == root.id()) {
            return Verdict.TOP;
        }
        if (locked(load)) {
            return Verdict.LOCKED;
        }
        if (formation != null) {
            if (target == null) {
                return Verdict.NOT_UNASSIGNED;
            }
            if (formation.id() == into || under(into, formation.id())) {
                return Verdict.INTO_ITSELF;
            }
            if (parents.getOrDefault(formation.id(), UNASSIGNED) == into) {
                return Verdict.HOME;
            }
            return target.deployed() ? Verdict.TARGET_LOCKED : Verdict.MOVE;
        }
        if (target != null && target.deployed()) {
            return Verdict.TARGET_LOCKED;
        }
        for (Card card : load.units()) {
            if (homes.getOrDefault(card.id(), UNASSIGNED) != into) {
                return Verdict.MOVE;
            }
        }
        return Verdict.HOME;
    }

    /** Whether formation {@code id} is somewhere under formation {@code above}. */
    private boolean under(int id, int above) {
        Integer at = parents.get(id);
        while (at != null && at != UNASSIGNED) {
            if (at == above) {
                return true;
            }
            at = parents.get(at);
        }
        return false;
    }

    /** The moves dropping {@code load} into {@code into} makes, none if it's refused or already there. */
    List<Move> moves(Load load, int into) {
        if (!judge(load, into).moves()) {
            return List.of();
        }
        if (load.formation() != null) {
            return List.of(Move.formation(load.formation().id(), into));
        }
        List<UUID> moving = new ArrayList<>();
        for (Card card : load.units()) {
            if (homes.getOrDefault(card.id(), UNASSIGNED) != into) {
                moving.add(card.id());
            }
        }
        return List.of(Move.units(moving, into));
    }

    /** The moves that put back what {@code moves} moves, each unit and formation where it is now. */
    List<Move> undoing(List<Move> moves) {
        List<Move> back = new ArrayList<>();
        for (Move move : moves) {
            if (move.ofUnits()) {
                Map<Integer, List<UUID>> byHome = new LinkedHashMap<>();
                for (UUID unit : move.units()) {
                    byHome.computeIfAbsent(homes.getOrDefault(unit, UNASSIGNED), home -> new ArrayList<>()).add(unit);
                }
                byHome.forEach((home, units) -> back.add(Move.units(units, home)));
            } else {
                back.add(Move.formation(move.formation(), parents.getOrDefault(move.formation(), root.id())));
            }
        }
        return back;
    }

    /**
     * {@code snapshot} as it would be after {@code moves}: moved units at the end of their new formation's (or with
     * the unassigned), a moved formation last under its new parent. For a table with no campaign behind it.
     */
    static ToeSnapshot applied(ToeSnapshot snapshot, List<Move> moves) {
        ToeSnapshot at = snapshot;
        for (Move move : moves) {
            at = applied(at, move);
        }
        return at;
    }

    private static ToeSnapshot applied(ToeSnapshot snapshot, Move move) {
        if (move.ofUnits()) {
            Set<UUID> moving = new HashSet<>(move.units());
            List<Card> carried = new ArrayList<>();
            for (Card card : snapshot.allUnits()) {
                if (moving.contains(card.id())) {
                    carried.add(card);
                }
            }
            List<Card> unassigned = new ArrayList<>();
            for (Card card : snapshot.unassigned()) {
                if (!moving.contains(card.id())) {
                    unassigned.add(card);
                }
            }
            if (move.into() == UNASSIGNED) {
                unassigned.addAll(carried);
            }
            Group root = rebuilt(snapshot.root(), moving, carried, move.into(), -1, UNASSIGNED, null);
            return snapshot.with(root, unassigned);
        }
        Group formation = new ToeMoves(snapshot).group(move.formation());
        if (formation == null) {
            return snapshot;
        }
        Group root = rebuilt(snapshot.root(), Set.of(), List.of(), UNASSIGNED, move.formation(), move.into(),
              formation);
        return snapshot.with(root, snapshot.unassigned());
    }

    /**
     * {@code group} with the units {@code moving} taken out and {@code carried} put at the end of formation
     * {@code into}'s, formation {@code removed} taken out and formation {@code added} put last under {@code under}.
     */
    private static Group rebuilt(Group group, Set<UUID> moving, List<Card> carried, int into, int removed, int under,
          @Nullable Group added) {
        List<Group> children = new ArrayList<>();
        for (Group child : group.children()) {
            if (child.id() != removed) {
                children.add(rebuilt(child, moving, carried, into, removed, under, added));
            }
        }
        if (added != null && group.id() == under) {
            children.add(added);
        }
        List<Card> units = new ArrayList<>();
        for (Card card : group.units()) {
            if (!moving.contains(card.id())) {
                units.add(card);
            }
        }
        if (group.id() == into) {
            units.addAll(carried);
        }
        return group.with(children, units);
    }
}
