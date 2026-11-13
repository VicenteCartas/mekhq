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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import mekhq.campaign.force.FormationSlot;
import mekhq.gui.gdx.hub.ToeLayout.Block;
import mekhq.gui.gdx.hub.ToeMoves.Load;
import mekhq.gui.gdx.hub.ToeMoves.Move;
import mekhq.gui.gdx.hub.ToeMoves.Verdict;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import mekhq.gui.gdx.hub.ToeSnapshot.Kind;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;
import org.junit.jupiter.api.Test;

class ToeMovesTest {
    private static final FormationSlot MEDIUM_MEK = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.MEDIUM);
    private static final int ROOT = 0;
    private static final int COMPANY = 1;
    private static final int ALPHA = 2;
    private static final int BRAVO = 3;
    private static final int OUT = 4;

    private final Card alpha1 = card("Alpha 1", false);
    private final Card alpha2 = card("Alpha 2", false);
    private final Card bravo1 = card("Bravo 1", false);
    private final Card away = card("Away", true);
    private final Card spare = card("Spare", false);
    private final Group alpha = new Group(ALPHA, "Alpha Lance", null, List.of(), List.of(alpha1, alpha2), false, List.of());
    private final Group bravo = new Group(BRAVO, "Bravo Lance", null, List.of(), List.of(bravo1), false, List.of());
    private final Group company = new Group(COMPANY, "Company", null, List.of(alpha, bravo), List.of(), false, List.of());
    private final Group out = new Group(OUT, "Deployed Lance", null, List.of(), List.of(away), true, List.of());
    private final Group root = new Group(ROOT, "Force", null, List.of(company, out), List.of(), false, List.of());
    private final ToeSnapshot snapshot = new ToeSnapshot("Force", root, List.of(spare), List.of());
    private final ToeMoves moves = new ToeMoves(snapshot);

    @Test
    void aUnitMovesToAnotherFormationAndAnUndoPutsItBack() {
        Load load = Load.of(List.of(alpha1));
        assertEquals(Verdict.MOVE, moves.judge(load, BRAVO));
        List<Move> made = moves.moves(load, BRAVO);
        assertEquals(List.of(Move.units(List.of(alpha1.id()), BRAVO)), made);
        assertEquals(List.of(Move.units(List.of(alpha1.id()), ALPHA)), moves.undoing(made));

        ToeSnapshot after = ToeMoves.applied(snapshot, made);
        Group company = after.root().children().get(0);
        assertEquals(List.of(alpha2), company.children().get(0).units());
        assertEquals(List.of(bravo1, alpha1), company.children().get(1).units());
    }

    @Test
    void aFormationCantGoUnderItselfOrOutOfTheForceAndTheForceDoesntMove() {
        Load load = Load.of(company);
        assertEquals(Verdict.INTO_ITSELF, moves.judge(load, COMPANY));
        assertEquals(Verdict.INTO_ITSELF, moves.judge(load, ALPHA));
        assertEquals(Verdict.HOME, moves.judge(load, ROOT));
        assertEquals(Verdict.NOT_UNASSIGNED, moves.judge(load, ToeMoves.UNASSIGNED));
        assertEquals(Verdict.TOP, moves.judge(Load.of(root), COMPANY));
        assertTrue(moves.moves(load, ALPHA).isEmpty());
    }

    @Test
    void aFormationMovesLastUnderItsNewParentAndAnUndoPutsItBack() {
        List<Move> made = moves.moves(Load.of(alpha), ROOT);
        assertEquals(List.of(Move.formation(ALPHA, ROOT)), made);
        assertEquals(List.of(Move.formation(ALPHA, COMPANY)), moves.undoing(made));

        ToeSnapshot after = ToeMoves.applied(snapshot, made);
        List<Group> top = after.root().children();
        assertEquals(List.of(COMPANY, OUT, ALPHA), top.stream().map(Group::id).toList());
        assertEquals(List.of(bravo), top.get(0).children());
        assertEquals(List.of(alpha1, alpha2), top.get(2).units());
    }

    @Test
    void nothingDeployedMovesNorDoesAnythingMoveIntoIt() {
        assertTrue(ToeMoves.locked(Load.of(List.of(alpha1, away))));
        assertEquals(Verdict.LOCKED, moves.judge(Load.of(List.of(away)), BRAVO));
        assertEquals(Verdict.LOCKED, moves.judge(Load.of(out), COMPANY));
        assertEquals(Verdict.TARGET_LOCKED, moves.judge(Load.of(List.of(alpha1)), OUT));
        assertEquals(Verdict.TARGET_LOCKED, moves.judge(Load.of(bravo), OUT));
        assertTrue(Verdict.TARGET_LOCKED.refused());
        assertTrue(!Verdict.HOME.refused() && !Verdict.HOME.moves());
    }

    @Test
    void unassigningTakesOnlyTheUnitsInAFormationAndAnUndoReturnsEachToItsOwn() {
        Load load = Load.of(List.of(alpha1, bravo1, spare));
        List<Move> made = moves.moves(load, ToeMoves.UNASSIGNED);
        assertEquals(List.of(Move.units(List.of(alpha1.id(), bravo1.id()), ToeMoves.UNASSIGNED)), made);
        assertEquals(List.of(Move.units(List.of(alpha1.id()), ALPHA), Move.units(List.of(bravo1.id()), BRAVO)),
              moves.undoing(made));
        assertEquals(Verdict.HOME, moves.judge(Load.of(List.of(spare)), ToeMoves.UNASSIGNED));

        ToeSnapshot after = ToeMoves.applied(snapshot, made);
        assertEquals(List.of(spare, alpha1, bravo1), after.unassigned());
        assertTrue(after.root().children().get(0).children().get(1).units().isEmpty());
    }

    @Test
    void aBlockTakesDropsForItsFormationAndCarriesWhatsOnIt() {
        Block attached = block(ToeFolds.attached(ALPHA), null, 2, List.of(alpha1, alpha2), true);
        assertEquals(ALPHA, ToeMoves.target(attached));
        assertEquals(List.of(alpha1, alpha2), ToeMoves.load(attached).units());

        Block unassigned = block(ToeFolds.UNASSIGNED, null, 1, List.of(spare), false);
        assertEquals(ToeMoves.UNASSIGNED, ToeMoves.target(unassigned));

        Block lance = block(BRAVO, bravo, 2, List.of(bravo1), true);
        assertEquals(BRAVO, ToeMoves.target(lance));
        assertSame(bravo, ToeMoves.load(lance).formation());

        assertNull(ToeMoves.load(block(ROOT, root, 0, snapshot.allUnits(), false)));
    }

    private static Block block(int key, Group group, int level, List<Card> units, boolean card) {
        return new Block(key, group, "", 0, 0, 1, 1, level, units, card);
    }

    private static Card card(String name, boolean deployed) {
        return new Card(UUID.randomUUID(), name, "", Kind.MEK, 50, null, null, Condition.SOUND, Work.NONE, Crew.FULL,
              false, true, deployed, false, 0, MEDIUM_MEK);
    }
}
