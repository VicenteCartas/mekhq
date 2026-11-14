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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import mekhq.campaign.force.FormationSlot;
import mekhq.gui.gdx.hub.ToeLayout.Block;
import mekhq.gui.gdx.hub.ToeLayout.Slot;
import mekhq.gui.gdx.hub.ToeLayout.Vacancy;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import mekhq.gui.gdx.hub.ToeSnapshot.Kind;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;
import org.junit.jupiter.api.Test;

class ToeLayoutTest {
    private static final FormationSlot MEDIUM_MEK = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.MEDIUM);
    private static final float EPSILON = 1e-4f;

    @Test
    void everyUnitHasOneSlot() {
        ToeSnapshot snapshot = regiment();
        ToeLayout layout = ToeLayout.of(snapshot, "Unassigned");

        Set<UUID> slotted = new HashSet<>();
        for (Slot slot : layout.slots()) {
            assertTrue(slotted.add(slot.card().id()), "a unit stands in two slots");
        }
        assertEquals(snapshot.allUnits().size(), slotted.size());
    }

    @Test
    void slotsStandOnTheirBlocks() {
        ToeLayout layout = ToeLayout.of(regiment(), "Unassigned");

        for (Slot slot : layout.slots()) {
            assertTrue(slot.block().contains(slot.x(), slot.z()), slot.card().name() + " stands off its block");
            assertTrue(slot.block().units().contains(slot.card()));
        }
    }

    @Test
    void formationsStandBelowTheirParentsWithoutOverlapping() {
        ToeLayout layout = ToeLayout.of(regiment(), "Unassigned");
        Map<Group, Block> byGroup = byGroup(layout);

        for (Block parent : layout.blocks()) {
            if (parent.group() == null) {
                continue;
            }
            float[] reach = layout.reach(parent.key());
            assertNotNull(reach);
            for (Group child : parent.group().children()) {
                Block block = byGroup.get(child);
                assertNotNull(block, child.name() + " has no node");
                assertEquals(parent.level() + 1, block.level());
                assertTrue(block.z() >= parent.z() + parent.depth() - EPSILON,
                      child.name() + " stands above " + parent.name());
                float[] under = layout.reach(block.key());
                assertNotNull(under);
                assertTrue(under[0] >= reach[0] - EPSILON && under[1] >= reach[1] - EPSILON
                      && under[2] <= reach[2] + EPSILON && under[3] <= reach[3] + EPSILON,
                      child.name() + " spills out of " + parent.name());
            }
        }
        List<Block> blocks = layout.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                assertFalse(overlap(blocks.get(i), blocks.get(j)),
                      blocks.get(i).name() + " overlaps " + blocks.get(j).name());
            }
        }
    }

    @Test
    void aLineRunsDownToEveryFormation() {
        ToeLayout layout = ToeLayout.of(regiment(), "Unassigned");

        for (Block block : layout.blocks()) {
            if (block.group() == null || block.level() == 0) {
                continue;
            }
            boolean reached = layout.links().stream().anyMatch(link ->
                  Math.abs(link.z1() - block.z()) < EPSILON && link.x1() >= block.x() - EPSILON
                        && link.x1() <= block.x() + block.width() + EPSILON
                        || Math.abs(link.x1() - block.x()) < EPSILON && link.z1() >= block.z() - EPSILON
                        && link.z1() <= block.z() + block.depth() + EPSILON);
            assertTrue(reached, "no line runs down to " + block.name());
        }
    }

    @Test
    void aCompanySpreadsItsLancesSideBySide() {
        List<Group> lances = new ArrayList<>();
        for (int l = 0; l < 3; l++) {
            List<Card> units = new ArrayList<>();
            for (int u = 0; u < 4; u++) {
                units.add(card("Unit " + l + u, 0));
            }
            lances.add(new Group(l + 1, "Lance " + l, null, List.of(), units, false, List.of()));
        }
        Group root = new Group(9, "Company", null, lances, List.of(), false, List.of());

        ToeLayout layout = ToeLayout.of(new ToeSnapshot("Company", root, List.of(), List.of()), "Unassigned");

        Map<Group, Block> byGroup = byGroup(layout);
        for (int l = 1; l < lances.size(); l++) {
            assertEquals(byGroup.get(lances.get(0)).z(), byGroup.get(lances.get(l)).z(), EPSILON);
            assertTrue(byGroup.get(lances.get(l)).x() > byGroup.get(lances.get(l - 1)).x());
        }
    }

    @Test
    void aTierLaysOutWhatsUnderItOneWayAndNeverSpreadsBelowOneThatLists() {
        // A headquarters' sections beside a section of teams of its own, as a company's lances stand beside another
        List<Group> sections = new ArrayList<>();
        int id = 1;
        for (int s = 0; s < 3; s++) {
            sections.add(new Group(id++, "Section " + s, null, List.of(), List.of(card("Clerk " + s, 0)), false,
                  List.of()));
        }
        List<Group> teams = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            teams.add(new Group(id++, "Team " + t, null, List.of(), List.of(card("Crane " + t, 0)), false, List.of()));
        }
        sections.add(new Group(id++, "Recovery", null, teams, List.of(), false, List.of()));
        List<Group> companies = new ArrayList<>();
        for (int c = 0; c < 2; c++) {
            List<Group> lances = new ArrayList<>();
            for (int l = 0; l < 5; l++) {
                List<Card> units = new ArrayList<>();
                for (int u = 0; u < 4; u++) {
                    units.add(card("Tank " + c + l + u, 0));
                }
                lances.add(new Group(id++, "Lance " + c + l, null, List.of(), units, false, List.of()));
            }
            companies.add(new Group(id++, "Company " + c, null, lances, List.of(), false, List.of()));
        }
        Group headquarters = new Group(id++, "Headquarters", null, sections, List.of(), false, List.of());
        Group armour = new Group(id++, "Armour", null, companies, List.of(), false, List.of());
        Group root = new Group(id, "Force", null, List.of(headquarters, armour), List.of(), false, List.of());

        for (ToeSnapshot snapshot : List.of(new ToeSnapshot("Force", root, List.of(), List.of()), regiment())) {
            ToeLayout layout = ToeLayout.of(snapshot, "Unassigned");

            Map<Group, Block> byGroup = byGroup(layout);
            Map<Integer, Boolean> spreads = new HashMap<>();
            for (Block parent : layout.blocks()) {
                if (parent.group() == null || parent.group().children().size() < 2) {
                    continue;
                }
                List<Block> under = parent.group().children().stream().map(byGroup::get).toList();
                boolean side = under.stream().allMatch(block -> Math.abs(block.z() - under.get(0).z()) < EPSILON);
                boolean listed = under.stream().allMatch(block -> Math.abs(block.x() - under.get(0).x()) < EPSILON);
                assertTrue(side != listed, parent.name() + " neither spreads nor lists what's under it");
                Boolean tier = spreads.putIfAbsent(parent.level(), side);
                assertTrue(tier == null || tier == side, parent.name() + " lays out its tier another way");
            }
            boolean listing = false;
            for (int level = 0; spreads.containsKey(level); level++) {
                assertFalse(listing && spreads.get(level), "tier " + level + " spreads below one that lists");
                listing |= !spreads.get(level);
            }
        }
    }

    @Test
    void anEmptyFormationStillHasItsBlock() {
        ToeLayout layout = ToeLayout.of(regiment(), "Unassigned");

        Block empty = layout.blocks().stream().filter(block -> block.name().equals("Replacements")).findFirst()
                            .orElseThrow();
        assertTrue(empty.units().isEmpty());
        assertTrue(empty.width() > 0 && empty.depth() > 0);
    }

    @Test
    void vacantSlotsStandOnTheirBlockAfterItsUnits() {
        FormationSlot light = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.LIGHT);
        Group lance = new Group(1, "Lance", null, List.of(), List.of(card("A", 0), card("B", 0)), false,
              List.of(MEDIUM_MEK, light, MEDIUM_MEK));
        Group replacements = new Group(2, "Replacements", null, List.of(), List.of(), false,
              List.of(FormationSlot.OPEN, FormationSlot.OPEN));
        Group root = new Group(3, "Company", null, List.of(lance, replacements), List.of(), false, List.of());

        ToeLayout layout = ToeLayout.of(new ToeSnapshot("Company", root, List.of(), List.of()), "Unassigned");

        List<Vacancy> onLance = layout.vacancies().stream().filter(v -> v.block().group() == lance).toList();
        assertEquals(List.of(light), onLance.stream().map(Vacancy::slot).toList());
        Vacancy vacancy = onLance.get(0);
        Block block = vacancy.block();
        float x = block.x() + vacancy.dx();
        float z = block.z() + vacancy.dz();
        assertTrue(block.contains(x, z));
        for (Slot slot : layout.slots()) {
            assertTrue(Math.abs(slot.x() - x) + Math.abs(slot.z() - z) > ToeLayout.CELL / 2,
                  "a vacancy stands on " + slot.card().name());
        }
        assertEquals(2, layout.vacancies().stream().filter(v -> v.block().group() == replacements).count());
    }

    @Test
    void theUnassignedStandApartFromTheForce() {
        ToeSnapshot snapshot = regiment();
        ToeLayout layout = ToeLayout.of(snapshot, "Unassigned");

        Block force = layout.blocks().stream().filter(block -> block.level() == 0 && block.group() != null)
                            .findFirst().orElseThrow();
        Block unassigned = layout.blocks().stream().filter(block -> block.group() == null).findFirst()
                                 .orElseThrow();
        assertEquals("Unassigned", unassigned.name());
        assertEquals(snapshot.unassigned(), unassigned.units());
        assertFalse(overlap(force, unassigned));
    }

    @Test
    void aForceWithNoneUnassignedHasNoBlockForThem() {
        ToeSnapshot snapshot = new ToeSnapshot("Company", regiment().root(), List.of(), List.of());

        ToeLayout layout = ToeLayout.of(snapshot, "Unassigned");

        assertTrue(layout.blocks().stream().allMatch(block -> block.group() != null));
    }

    @Test
    void boundsOfAPickCoverOnlyItsUnits() {
        ToeLayout layout = ToeLayout.of(regiment(), "Unassigned");

        float[] box = layout.bounds(card -> card.place() == 1);

        assertNotNull(box);
        int inside = 0;
        for (Slot slot : layout.slots()) {
            boolean within = slot.x() >= box[0] - EPSILON && slot.x() <= box[2] + EPSILON
                  && slot.z() >= box[1] - EPSILON && slot.z() <= box[3] + EPSILON;
            if (slot.card().place() == 1) {
                assertTrue(within, slot.card().name() + " lies outside its place's bounds");
                inside++;
            }
        }
        assertEquals(3, inside);
        float[] all = layout.bounds();
        assertTrue(box[0] >= all[0] && box[1] >= all[1] && box[2] <= all[2] && box[3] <= all[3]);
    }

    @Test
    void boundsOfAnEmptyPickAreNull() {
        ToeLayout layout = ToeLayout.of(regiment(), "Unassigned");

        assertNull(layout.bounds(card -> card.place() == 7));
    }

    @Test
    void aLongUnassignedListPacksIntoRowsNotALine() {
        assertEquals(3, ToeLayout.unassignedRow(3));
        assertEquals(ToeLayout.ROW, ToeLayout.unassignedRow(ToeLayout.ROW));
        assertTrue(ToeLayout.unassignedRow(200) > ToeLayout.ROW);
        assertTrue(ToeLayout.unassignedRow(200) < 200 / 4);
    }

    @Test
    void foldedLancesBecomeCardsHoldingTheirUnits() {
        ToeSnapshot snapshot = regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        folds.step(2);

        ToeLayout layout = ToeLayout.of(snapshot, "Unassigned", folds);

        assertTrue(layout.slots().isEmpty(), "every lance, attached and unassigned unit is folded a step out");
        Group lance = snapshot.root().children().get(0).children().get(0).children().get(0);
        Block card = layout.block(lance.id());
        assertNotNull(card);
        assertTrue(card.card());
        assertEquals(lance.units(), card.units());
        for (Card unit : snapshot.allUnits()) {
            assertNotNull(layout.holder(unit.id()), unit.name() + " is on no block");
        }
        Group company = snapshot.root().children().get(0).children().get(0);
        Block attached = layout.block(ToeFolds.attached(company.id()));
        assertNotNull(attached);
        assertTrue(attached.card());
        assertEquals(company.units(), attached.units());
        assertEquals(company.name(), attached.name());
        assertTrue(attached.attached());
        Block companyBlock = layout.block(company.id());
        assertNotNull(companyBlock);
        assertFalse(companyBlock.card());
        assertEquals(companyBlock.level() + 1, attached.level());
        assertTrue(inside(attached, companyBlock));
        assertTrue(layout.block(ToeFolds.UNASSIGNED).card());
    }

    @Test
    void aFoldedCompanyHoldsTheLancesFoldedIntoIt() {
        ToeSnapshot snapshot = regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        folds.step(1);

        ToeLayout layout = ToeLayout.of(snapshot, "Unassigned", folds);

        Group company = snapshot.root().children().get(0).children().get(0);
        Block card = layout.block(company.id());
        assertNotNull(card);
        assertTrue(card.card());
        for (Group lance : company.children()) {
            assertNull(layout.block(lance.id()), lance.name() + " is still on the table");
            assertSame(card, layout.holder(lance.id()));
        }
        assertEquals(ToeLayout.ROW + 3 + 4 + 4 + 1, card.units().size());
        List<Block> cards = layout.blocks().stream().filter(Block::card).toList();
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                assertFalse(overlap(cards.get(i), cards.get(j)),
                      cards.get(i).name() + " overlaps " + cards.get(j).name());
            }
        }
    }

    @Test
    void headingsAndCardsKeepTheirSizeOnScreenAtEveryStep() {
        ToeSnapshot snapshot = regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        Group company = snapshot.root().children().get(0).children().get(0);
        Group lance = company.children().get(0);
        folds.step(2);
        ToeLayout lances = ToeLayout.of(snapshot, "Unassigned", folds);
        float lanceCell = folds.cellPixels();
        folds.step(1);
        ToeLayout companies = ToeLayout.of(snapshot, "Unassigned", folds);
        float companyCell = folds.cellPixels();
        Block lanceCard = lances.block(lance.id());
        Block companyCard = companies.block(company.id());
        Block root = companies.block(snapshot.root().id());

        assertNotNull(lanceCard);
        assertNotNull(companyCard);
        assertNotNull(root);
        assertEquals(lanceCard.depth() * lanceCell, companyCard.depth() * companyCell, EPSILON);
        assertEquals(ToeLayout.HEAD_PIXELS, root.head() * companyCell, EPSILON);
        assertEquals(ToeLayout.HEAD_PIXELS, companyCard.head() * companyCell, EPSILON);
    }

    @Test
    void boundsOfAPickTakeInTheCardsItsUnitsAreFoldedInto() {
        ToeSnapshot snapshot = regiment();
        ToeFolds folds = new ToeFolds(snapshot);
        folds.step(2);
        ToeLayout layout = ToeLayout.of(snapshot, "Unassigned", folds);

        float[] box = layout.bounds(card -> card.place() == 1);

        Group lance = snapshot.root().children().get(1).children().get(0).children().get(0);
        Block card = layout.block(lance.id());
        assertNotNull(box);
        assertNotNull(card);
        assertArrayEquals(new float[] { card.x(), card.z(), card.x() + card.width(), card.z() + card.depth() }, box,
              EPSILON);
    }

    /**
     * A force of two battalions, one of three companies of three lances and one of two, a lance with more units than
     * a plate's row holds, an empty formation, units at a base, and a few unassigned.
     */
    static ToeSnapshot regiment() {
        List<Group> battalions = new ArrayList<>();
        int id = 1;
        for (int b = 0; b < 2; b++) {
            List<Group> companies = new ArrayList<>();
            for (int c = 0; c < 3 - b; c++) {
                List<Group> lances = new ArrayList<>();
                for (int l = 0; l < 3; l++) {
                    int size = b == 0 && c == 0 && l == 0 ? ToeLayout.ROW + 3 : 4;
                    List<Card> units = new ArrayList<>();
                    for (int u = 0; u < size; u++) {
                        units.add(card("Unit " + b + c + l + u, b == 1 && c == 0 && l == 0 && u < 3 ? 1 : 0));
                    }
                    lances.add(new Group(id++, "Lance " + b + c + l, null, List.of(), units, false, List.of()));
                }
                companies.add(new Group(id++, "Company " + b + c, null, lances,
                      List.of(card("Commander " + b + c, 0)), false, List.of()));
            }
            battalions.add(new Group(id++, "Battalion " + b, null, companies, List.of(), false, List.of()));
        }
        battalions.add(new Group(id++, "Replacements", null, List.of(), List.of(), false, List.of()));
        Group root = new Group(id, "Regiment", null, battalions, List.of(), false, List.of());
        List<Card> unassigned = List.of(card("Spare 1", 0), card("Spare 2", 0), card("Spare 3", 0));
        return new ToeSnapshot("Regiment", root, unassigned, List.of("Kestrel Station"));
    }

    static Card card(String name, int place) {
        return new Card(UUID.randomUUID(), name, "", Kind.MEK, 50, null, null, Condition.SOUND, Work.NONE, Crew.FULL,
              false, true, false, false, place, MEDIUM_MEK);
    }

    private static Map<Group, Block> byGroup(ToeLayout layout) {
        Map<Group, Block> byGroup = new IdentityHashMap<>();
        for (Block block : layout.blocks()) {
            if (block.group() != null) {
                byGroup.put(block.group(), block);
            }
        }
        return byGroup;
    }

    private static boolean inside(Block inner, Block outer) {
        return inner.x() >= outer.x() - EPSILON && inner.z() >= outer.z() - EPSILON
              && inner.x() + inner.width() <= outer.x() + outer.width() + EPSILON
              && inner.z() + inner.depth() <= outer.z() + outer.depth() + EPSILON;
    }

    private static boolean overlap(Block a, Block b) {
        return a.x() < b.x() + b.width() - EPSILON && b.x() < a.x() + a.width() - EPSILON
              && a.z() < b.z() + b.depth() - EPSILON && b.z() < a.z() + a.depth() - EPSILON;
    }
}
