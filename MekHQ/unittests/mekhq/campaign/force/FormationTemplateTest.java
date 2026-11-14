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
package mekhq.campaign.force;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import mekhq.MHQConstants;
import mekhq.campaign.Campaign;
import mekhq.campaign.force.FormationSlot.Kind;
import mekhq.campaign.force.FormationSlot.Weight;
import mekhq.utilities.MHQXMLUtility;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class FormationTemplateTest {
    private static final FormationTemplate BALL_OF_DEATH = new FormationTemplate("Ball of Death & <Co>",
          List.of(new FormationSlot(Kind.MEK, Weight.ANY), new FormationSlot(Kind.VEHICLE, Weight.ANY),
                new FormationSlot(Kind.AEROSPACE, Weight.ANY)), null);
    private static final FormationTemplate RECON = new FormationTemplate("Recon Lance",
          List.of(new FormationSlot(Kind.MEK, Weight.LIGHT), new FormationSlot(Kind.MEK, Weight.LIGHT),
                new FormationSlot(Kind.MEK, Weight.LIGHT), new FormationSlot(Kind.MEK, Weight.MEDIUM)), "Recon");

    @Test
    void aFormationKeepsTheTemplateItWasShapedByThroughSaveAndLoad() throws Exception {
        Formation formation = new Formation("Second Lance");
        formation.applyTemplate(RECON);

        Formation loaded = reload(formation);

        assertEquals(RECON.slots(), loaded.getSlots());
        assertEquals("Recon Lance", loaded.getTemplateName());
        assertEquals("Recon", loaded.getFormationRule());
    }

    @Test
    void aFormationShapedByNoTemplateSavesNone() throws Exception {
        Formation formation = new Formation("Lance");
        formation.applyTemplate(RECON);
        formation.applyTemplate(null);

        String written = write(formation::writeToXML);
        Formation loaded = reload(formation);

        assertFalse(written.contains("templateName"));
        assertFalse(written.contains("formationRule"));
        assertTrue(loaded.getSlots().isEmpty());
        assertNull(loaded.getTemplateName());
        assertNull(loaded.getFormationRule());
    }

    @Test
    void aCampaignsTemplatesAreSavedAndLoadedInOrder() throws Exception {
        FormationTemplates library = new FormationTemplates();
        library.save(BALL_OF_DEATH);
        library.save(RECON);

        FormationTemplates loaded = new FormationTemplates();
        loaded.fillFromXML(parse(write(library::writeToXML)));

        assertEquals(List.of(BALL_OF_DEATH, RECON), loaded.getTemplates());
        assertEquals(FormationTemplate.STANDARD.size() + 2, loaded.all().size());
    }

    @Test
    void savingUnderANameAlreadyKeptReplacesItInPlace() {
        FormationTemplates library = new FormationTemplates();
        library.save(RECON);
        library.save(BALL_OF_DEATH);
        FormationTemplate wider = FormationTemplate.open(" recon lance ", 5);

        library.save(wider);

        assertEquals(List.of(wider, BALL_OF_DEATH), library.getTemplates());
        assertEquals(wider, library.get("RECON LANCE"));
        assertTrue(library.remove("Recon Lance"));
        assertFalse(library.remove("Recon Lance"));
        assertTrue(write(library::writeToXML).contains("Ball of Death"));
        assertTrue(write(new FormationTemplates()::writeToXML).isEmpty());
    }

    @Test
    void aTemplateNeedsANameAndReadsABlankRuleAsNone() {
        assertThrows(IllegalArgumentException.class, () -> FormationTemplate.open("  ", 4));
        assertNull(new FormationTemplate("Lance", List.of(), " ").rule());
        assertTrue(FormationTemplate.LANCE.sizeOnly());
        assertFalse(RECON.sizeOnly());
    }

    private interface Writer {
        void write(PrintWriter writer, int indent);
    }

    private static String write(Writer source) {
        StringWriter written = new StringWriter();
        try (PrintWriter writer = new PrintWriter(written)) {
            source.write(writer, 0);
        }
        return written.toString();
    }

    private static Element parse(String xml) throws Exception {
        return MHQXMLUtility.newSafeDocumentBuilder()
                     .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                     .getDocumentElement();
    }

    private static Formation reload(Formation formation) throws Exception {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getPlayerForce()).thenReturn(mock(PlayerForce.class));
        Formation loaded = Formation.generateInstanceFromXML(parse(write(formation::writeToXML)), campaign,
              MHQConstants.VERSION);
        assertNotNull(loaded);
        return loaded;
    }
}
