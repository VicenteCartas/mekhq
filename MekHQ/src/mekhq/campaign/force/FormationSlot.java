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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import jakarta.annotation.Nullable;
import megamek.common.units.Entity;
import megamek.common.units.EntityWeightClass;
import megamek.common.units.UnitType;
import megamek.logging.MMLogger;
import mekhq.utilities.MHQXMLUtility;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * One place a formation is authorized: the kind of unit it calls for and its weight class, either of which may be left
 * open. A recon lance might call for three light Meks and a medium one; a formation with no slots authorizes nothing
 * and fits whatever is put in it. Slots only advise: a unit that doesn't fit one still goes where it's put, and shows
 * as not fitting.
 *
 * <p>What a unit is reads as a slot too ({@link #of(Entity)}), the kind and weight class it would fill exactly, with
 * {@code ANY} for what no slot can name (a DropShip's weight class, say); a unit fits a slot when everything the slot
 * names matches it.</p>
 */
public record FormationSlot(Kind kind, Weight weight) {
    private static final MMLogger LOGGER = MMLogger.create(FormationSlot.class);

    /** A slot open to any unit. */
    public static final FormationSlot OPEN = new FormationSlot(Kind.ANY, Weight.ANY);

    /** The kinds of unit a slot can call for. */
    public enum Kind {
        ANY, MEK, VEHICLE, VTOL, NAVAL, AEROSPACE, INFANTRY, BATTLE_ARMOR, PROTOMEK, LARGE_CRAFT;

        /** The kind of a unit of {@link UnitType} {@code unitType}, {@code ANY} for one no slot names. */
        public static Kind of(int unitType) {
            return switch (unitType) {
                case UnitType.MEK -> MEK;
                case UnitType.TANK, UnitType.GUN_EMPLACEMENT -> VEHICLE;
                case UnitType.VTOL -> VTOL;
                case UnitType.NAVAL -> NAVAL;
                case UnitType.CONV_FIGHTER, UnitType.AEROSPACE_FIGHTER, UnitType.AERO -> AEROSPACE;
                case UnitType.INFANTRY -> INFANTRY;
                case UnitType.BATTLE_ARMOR -> BATTLE_ARMOR;
                case UnitType.PROTOMEK -> PROTOMEK;
                case UnitType.SMALL_CRAFT, UnitType.DROPSHIP, UnitType.JUMPSHIP, UnitType.WARSHIP,
                      UnitType.SPACE_STATION -> LARGE_CRAFT;
                default -> ANY;
            };
        }
    }

    /** The weight classes a slot can call for, lightest first. */
    public enum Weight {
        ANY, ULTRA_LIGHT, LIGHT, MEDIUM, HEAVY, ASSAULT, SUPER_HEAVY;

        /**
         * The weight class of a unit of {@link EntityWeightClass} {@code weightClass}: a support vehicle's small,
         * medium or large reads as light, medium or heavy, and the classes of craft too large for a slot to name read
         * as {@code ANY}.
         */
        public static Weight of(int weightClass) {
            return switch (weightClass) {
                case EntityWeightClass.WEIGHT_ULTRA_LIGHT -> ULTRA_LIGHT;
                case EntityWeightClass.WEIGHT_LIGHT, EntityWeightClass.WEIGHT_SMALL_SUPPORT -> LIGHT;
                case EntityWeightClass.WEIGHT_MEDIUM, EntityWeightClass.WEIGHT_MEDIUM_SUPPORT -> MEDIUM;
                case EntityWeightClass.WEIGHT_HEAVY, EntityWeightClass.WEIGHT_LARGE_SUPPORT -> HEAVY;
                case EntityWeightClass.WEIGHT_ASSAULT -> ASSAULT;
                case EntityWeightClass.WEIGHT_SUPER_HEAVY -> SUPER_HEAVY;
                default -> ANY;
            };
        }
    }

    /** What {@code entity} is, as the slot it would fill exactly. */
    public static FormationSlot of(Entity entity) {
        return new FormationSlot(Kind.of(entity.getUnitType()), Weight.of(entity.getWeightClass()));
    }

    /** Whether a unit that is {@code unit} ({@link #of(Entity)}) fits this slot. */
    public boolean admits(FormationSlot unit) {
        return (kind == Kind.ANY || kind == unit.kind()) && (weight == Weight.ANY || weight == unit.weight());
    }

    /** How much the slot names, 0 to 2: a unit that fits a narrower slot belongs there rather than in an open one. */
    public int specificity() {
        return (kind == Kind.ANY ? 0 : 1) + (weight == Weight.ANY ? 0 : 1);
    }

    /**
     * Seats {@code units} ({@link #of(Entity)} each) in {@code slots}, as many as can fit: the index of the slot each
     * unit takes, or -1 for one left without, whether nothing fits it or what does is taken. Each unit tries the
     * narrowest slots first and earlier units are seated first, so a formation's order settles a tie.
     */
    public static int[] assign(List<FormationSlot> slots, List<FormationSlot> units) {
        Integer[] order = new Integer[slots.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingInt((Integer i) -> -slots.get(i).specificity()));
        int[] seated = new int[units.size()];
        Arrays.fill(seated, -1);
        int[] holder = new int[slots.size()];
        Arrays.fill(holder, -1);
        for (int unit = 0; unit < units.size(); unit++) {
            seat(unit, slots, units, order, holder, seated, new boolean[slots.size()]);
        }
        return seated;
    }

    /** Kuhn's augmenting path: seats {@code unit}, moving whoever holds a slot it fits elsewhere if they can go. */
    private static boolean seat(int unit, List<FormationSlot> slots, List<FormationSlot> units, Integer[] order,
          int[] holder, int[] seated, boolean[] tried) {
        for (int slot : order) {
            if (tried[slot] || !slots.get(slot).admits(units.get(unit))) {
                continue;
            }
            tried[slot] = true;
            if (holder[slot] < 0 || seat(holder[slot], slots, units, order, holder, seated, tried)) {
                holder[slot] = unit;
                seated[unit] = slot;
                return true;
            }
        }
        return false;
    }

    /** The slot as it's saved: {@code KIND:WEIGHT}. */
    public String code() {
        return kind.name() + ':' + weight.name();
    }

    /** The slot saved as {@code code}, or null if it isn't one. */
    public static @Nullable FormationSlot parse(String code) {
        String[] parts = code.trim().toUpperCase(Locale.ROOT).split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new FormationSlot(Kind.valueOf(parts[0]), Weight.valueOf(parts[1]));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Writes {@code slots} as a {@code <slots>} element, or nothing when there are none. */
    static void writeToXML(PrintWriter pw, int indent, List<FormationSlot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        MHQXMLUtility.writeSimpleXMLOpenTag(pw, indent++, "slots");
        for (FormationSlot slot : slots) {
            MHQXMLUtility.writeSimpleXMLTag(pw, indent, "slot", slot.code());
        }
        MHQXMLUtility.writeSimpleXMLCloseTag(pw, --indent, "slots");
    }

    /** The slots saved in {@code slotsNode}, a {@code <slots>} element, leaving out any it can't read. */
    static List<FormationSlot> parseAll(Node slotsNode) {
        List<FormationSlot> slots = new ArrayList<>();
        NodeList nodes = slotsNode.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE || !node.getNodeName().equalsIgnoreCase("slot")) {
                continue;
            }
            FormationSlot slot = parse(node.getTextContent());
            if (slot == null) {
                LOGGER.error("Unknown formation slot not loaded: " + node.getTextContent());
            } else {
                slots.add(slot);
            }
        }
        return slots;
    }
}
