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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import mekhq.campaign.force.FormationSlot;
import mekhq.campaign.force.FormationTemplate;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import mekhq.gui.gdx.hub.ToeSnapshot.Kind;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;
import mekhq.gui.gdx.hub.ToeTemplates.Choice;
import mekhq.gui.gdx.hub.ToeTemplates.Rule;
import mekhq.gui.gdx.hub.ToeTemplates.Type;
import org.junit.jupiter.api.Test;

class ToeTemplatesTest {
    private static final FormationSlot LIGHT = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.LIGHT);
    private static final FormationSlot HEAVY = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.HEAVY);
    private static final FormationSlot ANY_MEK = new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.ANY);
    private static final FormationTemplate BALL = new FormationTemplate("Ball of Death",
          List.of(HEAVY, new FormationSlot(FormationSlot.Kind.VEHICLE, FormationSlot.Weight.ANY)), null);
    private static final FormationTemplate SCOUTS = new FormationTemplate("Scouts", List.of(LIGHT, LIGHT), "Recon");
    private static final List<Rule> RULES = List.of(new Rule("Recon", "Recon", false),
          new Rule("Aerospace Superiority Squadron", "Aerospace Superiority Squadron", true),
          new Rule("Anvil", "Anvil (FWL)", false));

    @Test
    void theChoicesRunFromCustomThroughTheUsualStrengthsAndTheKeptTemplatesToTheRulesInOrder() {
        List<Choice> choices = ToeTemplates.choices(List.of(BALL, SCOUTS), RULES);

        assertEquals(List.of(Type.CUSTOM, Type.STANDARD, Type.STANDARD, Type.STANDARD, Type.KEPT, Type.KEPT,
              Type.RULE, Type.RULE, Type.RULE), choices.stream().map(Choice::type).toList());
        assertEquals(List.of("", "Lance", "Star", "Level II", "Ball of Death", "Scouts",
              "Aerospace Superiority Squadron", "Anvil (FWL)", "Recon"), choices.stream().map(Choice::label).toList());
        assertEquals("Recon", choices.get(5).rule());
        assertEquals("Anvil", choices.get(7).rule());
    }

    @Test
    void aFormationShowsTheChoiceItWasShapedBy() {
        List<Choice> choices = ToeTemplates.choices(List.of(BALL, SCOUTS), RULES);

        assertEquals(4, ToeTemplates.current(choices, shaped("ball of death", null)));
        assertEquals(2, ToeTemplates.current(choices, shaped("Star", null)));
        assertEquals(8, ToeTemplates.current(choices, shaped("My recon lance", "Recon")));
        assertEquals(0, ToeTemplates.current(choices, shaped("Forgotten", null)));
        assertEquals(0, ToeTemplates.current(choices, shaped(null, null)));
    }

    @Test
    void aStrengthKeepsTheSlotsSetWhileAKeptTemplateGivesItsOwn() {
        List<Choice> choices = ToeTemplates.choices(List.of(BALL), RULES);
        List<FormationSlot> draft = List.of(LIGHT, HEAVY);

        assertEquals(List.of(LIGHT, HEAVY, ANY_MEK, ANY_MEK, ANY_MEK), ToeTemplates.slots(choices.get(2), draft,
              List.of()));
        assertEquals(BALL.slots(), ToeTemplates.slots(choices.get(4), draft, List.of()));
        assertEquals(draft, ToeTemplates.slots(choices.get(0), draft, List.of()));
    }

    @Test
    void aRuleKeepsTheSlotsSetOrStartsWithItsOpenPlaces() {
        List<Choice> choices = ToeTemplates.choices(List.of(), RULES);
        Choice squadron = choices.get(4);
        Choice recon = choices.get(6);

        assertEquals(List.of(LIGHT), ToeTemplates.slots(recon, List.of(LIGHT), List.of(card(HEAVY))));
        assertEquals(Collections.nCopies(ToeTemplates.GROUND_SIZE, FormationSlot.OPEN),
              ToeTemplates.slots(recon, List.of(), List.of(card(HEAVY))));
        assertEquals(Collections.nCopies(ToeTemplates.AEROSPACE_SIZE, FormationSlot.OPEN),
              ToeTemplates.slots(squadron, List.of(), List.of()));
    }

    @Test
    void onlyANamedShapeOtherThanAUsualStrengthCanBeKept() {
        assertTrue(ToeTemplates.keepable("Ball of Death"));
        assertFalse(ToeTemplates.keepable("  "));
        assertFalse(ToeTemplates.keepable(null));
        assertFalse(ToeTemplates.keepable(" level ii "));
    }

    private static Group shaped(String template, String rule) {
        return new Group(1, "Lance", null, List.of(), List.of(), false, List.of(), 1, template, rule);
    }

    private static Card card(FormationSlot fit) {
        return new Card(UUID.randomUUID(), "Unit", "", Kind.MEK, 50, null, null, Condition.SOUND, Work.NONE,
              Crew.FULL, false, true, false, false, 0, fit);
    }
}
