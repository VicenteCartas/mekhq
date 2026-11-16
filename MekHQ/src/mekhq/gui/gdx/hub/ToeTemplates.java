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
import java.util.List;

import jakarta.annotation.Nullable;
import megamek.client.ratgenerator.FormationType;
import megamek.client.ui.Messages;
import mekhq.campaign.force.FormationSlot;
import mekhq.campaign.force.FormationTemplate;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;

/**
 * What the slot editor's template select offers: shaping a formation by hand, the usual strengths, the templates the
 * campaign keeps, and MegaMek's formation rules from Campaign Operations. Plain Java over a {@link ToeSnapshot}.
 */
final class ToeTemplates {
    /** How many places a ground formation rule starts a formation with, when it authorizes none yet. */
    static final int GROUND_SIZE = 4;
    /** How many places an aerospace formation rule starts a formation with: a squadron's six. */
    static final int AEROSPACE_SIZE = 6;

    /** Where a choice comes from. */
    enum Type {
        /** Shaping by hand: the slots stay as they are and meet no rule. */
        CUSTOM,
        /** One of the usual strengths every campaign knows. */
        STANDARD,
        /** One of the campaign's own templates. */
        KEPT,
        /** A MegaMek formation rule. */
        RULE
    }

    /**
     * A choice in the select: {@code name} what a formation shaped by it is called, {@code label} how it reads in the
     * list, {@code template} the shape it gives (for a rule, the open places it starts a formation with that has none;
     * null for custom) and {@code rule} the formation rule it's meant to meet, null for none.
     */
    record Choice(Type type, String name, String label, @Nullable FormationTemplate template, @Nullable String rule) {
        static final Choice CUSTOM = new Choice(Type.CUSTOM, "", "", null, null);
    }

    /** A formation rule as the select offers it: its name, how it reads, and whether it's for aerospace units. */
    record Rule(String name, String label, boolean aerospace) {
    }

    private ToeTemplates() {
    }

    /**
     * The choices, in order: custom, the usual strengths, the campaign's {@code kept} templates as it lists them, then
     * the {@code rules} by how they read.
     */
    static List<Choice> choices(List<FormationTemplate> kept, List<Rule> rules) {
        List<Choice> choices = new ArrayList<>();
        choices.add(Choice.CUSTOM);
        for (FormationTemplate template : FormationTemplate.STANDARD) {
            choices.add(new Choice(Type.STANDARD, template.name(), template.name(), template, null));
        }
        for (FormationTemplate template : kept) {
            choices.add(new Choice(Type.KEPT, template.name(), template.name(), template, template.rule()));
        }
        List<Rule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparing(Rule::label, String.CASE_INSENSITIVE_ORDER));
        for (Rule rule : sorted) {
            FormationTemplate start = FormationTemplate.open(rule.name(),
                  rule.aerospace() ? AEROSPACE_SIZE : GROUND_SIZE);
            choices.add(new Choice(Type.RULE, rule.name(), rule.label(), start, rule.name()));
        }
        return List.copyOf(choices);
    }

    /**
     * Which of {@code choices} {@code group} was shaped by: the kept or usual template of its template's name, else the
     * rule it's meant to meet, else custom.
     */
    static int current(List<Choice> choices, Group group) {
        String template = group.template();
        if (template != null) {
            for (Type type : List.of(Type.KEPT, Type.STANDARD)) {
                for (int i = 0; i < choices.size(); i++) {
                    Choice choice = choices.get(i);
                    if (choice.type() == type && choice.name().equalsIgnoreCase(template)) {
                        return i;
                    }
                }
            }
        }
        if (group.rule() != null) {
            for (int i = 0; i < choices.size(); i++) {
                Choice choice = choices.get(i);
                if (choice.type() == Type.RULE && group.rule().equals(choice.rule())) {
                    return i;
                }
            }
        }
        return 0;
    }

    /**
     * The slots {@code choice} gives a formation of {@code units} whose slots are {@code draft} so far. Custom keeps
     * them; a template that sets only a strength brings them to it as {@link ToeSlots#template} does; any other
     * template gives its own; a rule keeps them, or starts with its open places if there are none.
     */
    static List<FormationSlot> slots(Choice choice, List<FormationSlot> draft, List<Card> units) {
        FormationTemplate template = choice.template();
        if (template == null || choice.type() == Type.RULE && !draft.isEmpty()) {
            return List.copyOf(draft);
        }
        if (choice.type() == Type.RULE) {
            return template.slots();
        }
        return template.sizeOnly() ? ToeSlots.template(template, draft, units) : template.slots();
    }

    /** Whether {@code name} may be kept as one of the campaign's templates: it names something, not a usual one. */
    static boolean keepable(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return FormationTemplate.STANDARD.stream().noneMatch(template -> template.name().equalsIgnoreCase(name.strip()));
    }

    /** The formation rules MegaMek knows. Call it on one thread only: MegaMek builds them the first time. */
    static List<Rule> rules() {
        List<Rule> rules = new ArrayList<>();
        for (FormationType type : FormationType.getAllFormations()) {
            rules.add(new Rule(type.getName(), type.getNameWithFaction(), !type.isGround()));
        }
        return List.copyOf(rules);
    }

    /** What the formation rule {@code rule} asks for, as MegaMek tells it; empty if it has nothing to tell. */
    static String description(String rule) {
        FormationType type = FormationType.getFormationType(rule);
        if (type == null) {
            return "";
        }
        String text = Messages.getString(type.getTooltipKey());
        return text.startsWith("!") ? "" : text;
    }
}
