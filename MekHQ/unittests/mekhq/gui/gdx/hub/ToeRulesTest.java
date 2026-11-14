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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import megamek.client.ratgenerator.FormationQualification;
import megamek.client.ratgenerator.FormationQualification.Verdict;
import megamek.common.loaders.MekSummary;
import megamek.common.units.EntityWeightClass;
import megamek.common.units.UnitRole;
import mekhq.campaign.force.FormationSlot;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import mekhq.gui.gdx.hub.ToeSnapshot.Kind;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;
import org.junit.jupiter.api.Test;

class ToeRulesTest {
    // Light Fire: Medium or lighter, half of them Missile Boats or Snipers
    private static final String LIGHT_FIRE = "Light Fire";

    private final Map<UUID, MekSummary> summaries = new HashMap<>();

    @Test
    void aFormationWithoutARuleOrUnitsIsNotJudged() {
        Card unit = known(UnitRole.SNIPER);

        assertNull(ToeRules.qualify(lance(null, List.of(unit)), summaries));
        assertNull(ToeRules.qualify(lance("No Such Rule", List.of(unit)), summaries));
        assertNull(ToeRules.qualify(lance(LIGHT_FIRE, List.of()), summaries));
    }

    @Test
    void knownDesignsMeetOrFailTheRule() {
        Group meets = lance(LIGHT_FIRE, List.of(known(UnitRole.SNIPER), known(UnitRole.MISSILE_BOAT),
              known(UnitRole.SCOUT), known(UnitRole.STRIKER)));
        Group fails = lance(LIGHT_FIRE, List.of(known(UnitRole.SNIPER), known(UnitRole.SCOUT),
              known(UnitRole.SCOUT), known(UnitRole.STRIKER)));

        assertEquals(Verdict.QUALIFIES, verdict(meets));
        FormationQualification failed = ToeRules.qualify(fails, summaries);
        assertNotNull(failed);
        assertEquals(Verdict.FAILS, failed.verdict());
        assertEquals(1, failed.unmet().get(0).shortfall());
    }

    @Test
    void unitsMegaMekDoesNotKnowCannotBeVerified() {
        Group lance = lance(LIGHT_FIRE, List.of(unknown(FormationSlot.Weight.LIGHT),
              unknown(FormationSlot.Weight.MEDIUM), known(UnitRole.SNIPER), known(UnitRole.SCOUT)));

        assertEquals(Verdict.UNVERIFIED, verdict(lance));
    }

    @Test
    void aStandInTooHeavyForTheRuleFailsItWhateverItsRole() {
        Group lance = lance(LIGHT_FIRE, List.of(unknown(FormationSlot.Weight.HEAVY), known(UnitRole.SNIPER),
              known(UnitRole.SNIPER), known(UnitRole.SCOUT)));

        assertEquals(Verdict.FAILS, verdict(lance));
    }

    @Test
    void aCandidateCompletesTheRuleOnlyIfItMakesTheFormationMeetIt() {
        Group lance = lance(LIGHT_FIRE, List.of(known(UnitRole.SNIPER), known(UnitRole.SCOUT),
              known(UnitRole.SCOUT)));

        assertTrue(ToeRules.completes(lance, known(UnitRole.MISSILE_BOAT), summaries));
        assertFalse(ToeRules.completes(lance, known(UnitRole.STRIKER), summaries));
        assertFalse(ToeRules.completes(lance(null, lance.units()), known(UnitRole.MISSILE_BOAT), summaries));
    }

    private Verdict verdict(Group group) {
        FormationQualification qualification = ToeRules.qualify(group, summaries);
        assertNotNull(qualification);
        return qualification.verdict();
    }

    private static Group lance(String rule, List<Card> units) {
        return new Group(1, "Lance", null, List.of(), units, false, List.of(), 1, null, rule);
    }

    /** A medium 'Mek whose design MegaMek knows, of {@code role}. */
    private Card known(UnitRole role) {
        Card card = unknown(FormationSlot.Weight.MEDIUM);
        MekSummary summary = new MekSummary();
        summary.setName(role.name());
        summary.setUnitType("Mek");
        summary.setTons(50);
        summary.setWeightClass(EntityWeightClass.WEIGHT_MEDIUM);
        summary.setUnitRole(role);
        summaries.put(card.id(), summary);
        return card;
    }

    /** A 'Mek of {@code weight} whose design MegaMek doesn't know. */
    private static Card unknown(FormationSlot.Weight weight) {
        return new Card(UUID.randomUUID(), "Unit", "", Kind.MEK, 50, null, null, Condition.SOUND, Work.NONE,
              Crew.FULL, false, true, false, false, 0, new FormationSlot(FormationSlot.Kind.MEK, weight));
    }
}
