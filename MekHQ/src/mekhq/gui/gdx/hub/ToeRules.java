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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.Nullable;
import megamek.client.ratgenerator.FormationQualification;
import megamek.client.ratgenerator.FormationType;
import megamek.common.loaders.MekSummary;
import megamek.common.units.EntityWeightClass;
import megamek.common.units.UnitRole;
import mekhq.campaign.force.FormationSlot;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;

/**
 * How a formation's own units stand against the MegaMek formation rule it's meant to meet: each unit as MegaMek knows
 * its design, or for one it doesn't, a stand-in of the unit's kind and weight class whose role isn't known. It only
 * ever warns. Plain Java over a {@link ToeSnapshot}; call it on one thread only, as MegaMek builds its rules the first
 * time.
 */
final class ToeRules {
    private ToeRules() {
    }

    /**
     * How {@code group}'s own units stand against its rule, their designs from {@code summaries} by unit id; null if it
     * has no rule, MegaMek knows no rule of that name, or it has no units to judge.
     */
    static @Nullable FormationQualification qualify(Group group, Map<UUID, MekSummary> summaries) {
        return qualify(group.rule(), group.units(), summaries);
    }

    /** As {@link #qualify(Group, Map)}, of {@code units} against the rule named {@code rule}. */
    static @Nullable FormationQualification qualify(@Nullable String rule, List<Card> units,
          Map<UUID, MekSummary> summaries) {
        FormationType type = rule == null ? null : FormationType.getFormationType(rule);
        if (type == null || units.isEmpty()) {
            return null;
        }
        List<MekSummary> designs = new ArrayList<>();
        for (Card card : units) {
            MekSummary summary = summaries.get(card.id());
            designs.add(summary == null ? standIn(card) : summary);
        }
        return type.qualify(designs);
    }

    /**
     * Whether putting {@code card} into {@code group} makes it meet its rule when it doesn't yet; false for a formation
     * with no rule or one it meets already.
     */
    static boolean completes(Group group, Card card, Map<UUID, MekSummary> summaries) {
        return group.rule() != null && !meets(group, summaries) && meetsWith(group, card, summaries);
    }

    /** Whether {@code group}'s own units meet its rule. */
    static boolean meets(Group group, Map<UUID, MekSummary> summaries) {
        FormationQualification qualification = qualify(group, summaries);
        return qualification != null && qualification.qualifies();
    }

    /** Whether {@code group}'s own units and {@code card} with them meet its rule. */
    static boolean meetsWith(Group group, Card card, Map<UUID, MekSummary> summaries) {
        List<Card> with = new ArrayList<>(group.units());
        with.add(card);
        FormationQualification qualification = qualify(group.rule(), with, summaries);
        return qualification != null && qualification.qualifies();
    }

    /** A design standing in for {@code card}'s, whose role isn't known: its name, kind, tonnage and weight class. */
    static MekSummary standIn(Card card) {
        MekSummary summary = new MekSummary();
        summary.setName(card.name());
        summary.setChassis(card.name());
        summary.setModel("");
        summary.setUnitType(typeName(card.kind()));
        summary.setTons(card.tons());
        summary.setWeightClass(weightClass(card.fit().weight()));
        summary.setUnitRole(UnitRole.UNDETERMINED);
        return summary;
    }

    /** The unit type as MegaMek's unit files name it. */
    private static String typeName(ToeSnapshot.Kind kind) {
        return switch (kind) {
            case MEK -> "Mek";
            case VEHICLE, OTHER -> "Tank";
            case VTOL -> "VTOL";
            case NAVAL -> "Naval";
            case AEROSPACE -> "AeroSpaceFighter";
            case INFANTRY -> "Infantry";
            case BATTLE_ARMOR -> "BattleArmor";
            case PROTOMEK -> "ProtoMek";
            case LARGE_CRAFT -> "Dropship";
        };
    }

    private static int weightClass(FormationSlot.Weight weight) {
        return switch (weight) {
            case ULTRA_LIGHT -> EntityWeightClass.WEIGHT_ULTRA_LIGHT;
            case LIGHT -> EntityWeightClass.WEIGHT_LIGHT;
            case MEDIUM -> EntityWeightClass.WEIGHT_MEDIUM;
            case HEAVY -> EntityWeightClass.WEIGHT_HEAVY;
            case ASSAULT -> EntityWeightClass.WEIGHT_ASSAULT;
            case SUPER_HEAVY -> EntityWeightClass.WEIGHT_SUPER_HEAVY;
            // Only craft too large for a slot to name have no weight class one does
            case ANY -> EntityWeightClass.WEIGHT_SMALL_DROP;
        };
    }
}
