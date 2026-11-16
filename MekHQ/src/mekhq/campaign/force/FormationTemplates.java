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
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nullable;
import megamek.logging.MMLogger;
import mekhq.utilities.MHQXMLUtility;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * The formation templates a campaign keeps besides the {@link FormationTemplate#STANDARD standard} ones, in the order
 * the player saved them. Names are unique, ignoring case: saving one under a name already kept replaces it in place.
 * Formations shaped by a template keep their own copy, so changing or removing one here changes no formation.
 */
public class FormationTemplates {
    private static final MMLogger LOGGER = MMLogger.create(FormationTemplates.class);

    private final List<FormationTemplate> templates = new ArrayList<>();

    /** The templates the campaign keeps, in the order they were first saved. */
    public List<FormationTemplate> getTemplates() {
        return List.copyOf(templates);
    }

    /** The standard templates followed by the campaign's own. */
    public List<FormationTemplate> all() {
        List<FormationTemplate> all = new ArrayList<>(FormationTemplate.STANDARD);
        all.addAll(templates);
        return List.copyOf(all);
    }

    /** The template the campaign keeps under {@code name}, ignoring case, or null if it keeps none. */
    public @Nullable FormationTemplate get(String name) {
        int index = indexOf(name);
        return index < 0 ? null : templates.get(index);
    }

    /** Keeps {@code template}, in place of the one already under its name if there is one. */
    public void save(FormationTemplate template) {
        int index = indexOf(template.name());
        if (index < 0) {
            templates.add(template);
        } else {
            templates.set(index, template);
        }
    }

    /** Forgets the template under {@code name}, ignoring case; whether there was one. */
    public boolean remove(String name) {
        int index = indexOf(name);
        if (index >= 0) {
            templates.remove(index);
        }
        return index >= 0;
    }

    private int indexOf(String name) {
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).name().equalsIgnoreCase(name.strip())) {
                return i;
            }
        }
        return -1;
    }

    /** Writes the templates as a {@code <formationTemplates>} element, or nothing when there are none. */
    public void writeToXML(PrintWriter pw, int indent) {
        if (templates.isEmpty()) {
            return;
        }
        MHQXMLUtility.writeSimpleXMLOpenTag(pw, indent++, "formationTemplates");
        for (FormationTemplate template : templates) {
            template.writeToXML(pw, indent);
        }
        MHQXMLUtility.writeSimpleXMLCloseTag(pw, --indent, "formationTemplates");
    }

    /** Keeps the templates saved in {@code templatesNode}, a {@code <formationTemplates>} element. */
    public void fillFromXML(Node templatesNode) {
        NodeList nodes = templatesNode.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE || !node.getNodeName().equals("formationTemplate")) {
                continue;
            }
            FormationTemplate template = FormationTemplate.generateInstanceFromXML(node);
            if (template == null) {
                LOGGER.error("Formation template without a name not loaded");
            } else {
                save(template);
            }
        }
    }
}
