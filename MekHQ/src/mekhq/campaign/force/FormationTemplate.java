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

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.Nullable;
import mekhq.utilities.MHQXMLUtility;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A shape a formation can be given: a name, the places it authorizes ({@link FormationSlot}), and optionally the
 * MegaMek formation rule it's meant to meet ({@code megamek.client.ratgenerator.FormationType}, by name, such as
 * {@code "Recon"}). A player's own "Ball of Death" of a Mek, a tank and a fighter needs no rule; a plain lance is four
 * open places; a recon lance can name both its places and the rule. Applying one to a formation copies it there
 * ({@link Formation#applyTemplate(FormationTemplate)}), and neither its slots nor its rule ever stop a unit going
 * where it's put: they only warn.
 *
 * @param name  what the player calls it, never blank
 * @param slots the places it authorizes, in order; as many as its strength
 * @param rule  the name of the MegaMek formation rule it's meant to meet, or null for none
 */
public record FormationTemplate(String name, List<FormationSlot> slots, @Nullable String rule) {
    /** The Inner Sphere's four. */
    public static final FormationTemplate LANCE = open("Lance", 4);
    /** The Clans' five. */
    public static final FormationTemplate STAR = open("Star", 5);
    /** ComStar's and the Word of Blake's six. */
    public static final FormationTemplate LEVEL_II = open("Level II", 6);
    /** The strengths every campaign knows, smallest first. */
    public static final List<FormationTemplate> STANDARD = List.of(LANCE, STAR, LEVEL_II);

    public FormationTemplate {
        name = text(name);
        if (name == null) {
            throw new IllegalArgumentException("A formation template needs a name");
        }
        slots = List.copyOf(slots);
        rule = text(rule);
    }

    /** A template of {@code size} places open to any unit, meeting no rule. */
    public static FormationTemplate open(String name, int size) {
        return new FormationTemplate(name, Collections.nCopies(size, FormationSlot.OPEN), null);
    }

    /** How many places it authorizes. */
    public int size() {
        return slots.size();
    }

    /** Whether it sets only a strength: every place is open to any unit. */
    public boolean sizeOnly() {
        return slots.stream().allMatch(FormationSlot.OPEN::equals);
    }

    /** {@code value} stripped, or null when there's nothing left of it. */
    static @Nullable String text(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public void writeToXML(PrintWriter pw, int indent) {
        MHQXMLUtility.writeSimpleXMLOpenTag(pw, indent++, "formationTemplate");
        MHQXMLUtility.writeSimpleXMLTag(pw, indent, "name", name);
        if (rule != null) {
            MHQXMLUtility.writeSimpleXMLTag(pw, indent, "rule", rule);
        }
        FormationSlot.writeToXML(pw, indent, slots);
        MHQXMLUtility.writeSimpleXMLCloseTag(pw, --indent, "formationTemplate");
    }

    /** The template saved in {@code templateNode}, a {@code <formationTemplate>} element, or null if it has no name. */
    public static @Nullable FormationTemplate generateInstanceFromXML(Node templateNode) {
        String name = null;
        String rule = null;
        List<FormationSlot> slots = List.of();
        NodeList nodes = templateNode.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            switch (node.getNodeName()) {
                case "name" -> name = text(node.getTextContent());
                case "rule" -> rule = node.getTextContent();
                case "slots" -> slots = FormationSlot.parseAll(node);
                default -> {
                }
            }
        }
        return name == null ? null : new FormationTemplate(name, slots, rule);
    }
}
