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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.swing.ImageIcon;

import jakarta.annotation.Nullable;
import megamek.common.icons.Camouflage;
import megamek.common.loaders.MekSummary;
import megamek.common.loaders.MekSummaryCache;
import megamek.common.units.Entity;
import megamek.common.units.UnitType;
import mekhq.campaign.Campaign;
import mekhq.campaign.base.PlayerBase;
import mekhq.campaign.campaignOptions.CampaignOption;
import mekhq.campaign.force.Formation;
import mekhq.campaign.force.FormationLevel;
import mekhq.campaign.force.FormationSlot;
import mekhq.campaign.force.FormationTemplate;
import mekhq.campaign.location.LocationUtils;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.personnel.turnoverAndRetention.Fatigue;
import mekhq.campaign.unit.Unit;
import mekhq.gui.model.LocationDisplay;
import mekhq.gui.utilities.StaticChecks;

/**
 * The campaign's TO&amp;E as the table shows it, read on the campaign's thread: the formations' tree with
 * each one's units, the units in none, and the places the force is spread over (the main force and each of the
 * player's bases). Each unit carries what the table shows of it at a glance: its art, what it is, its condition, the
 * work on it, its crew and where it is. {@code templates} are the formation templates the campaign keeps, and
 * {@code summaries} the units' designs as MegaMek's formation rules read them, by unit id, for those it knows.
 */
record ToeSnapshot(String force, Group root, List<Card> unassigned, List<String> bases,
      List<FormationTemplate> templates, Map<UUID, MekSummary> summaries) {
    /** The largest a unit's art is kept, in pixels across, so its texture stays light. */
    private static final int ART_SIZE = 96;
    /** Unit art already camouflaged, by {@link #artKey}; it's costly to paint, and a regiment repeats much of it. */
    private static final Map<String, BufferedImage> ART = new HashMap<>();
    private static final int ART_KEPT = 512;
    /** Whether each camouflage, by category and file, has a picture to paint with. */
    private static final Map<String, Boolean> PAINTABLE = new HashMap<>();

    /** The main force's place; a base's is 1 + its index in {@link #bases}. */
    static final int MAIN_FORCE = 0;
    /** The effective fatigue at which a crew counts as tired, as {@link Fatigue} first reports it. */
    private static final int TIRED = 5;

    /** A force that keeps no templates of its own and whose units' designs aren't known. */
    ToeSnapshot(String force, Group root, List<Card> unassigned, List<String> bases) {
        this(force, root, unassigned, bases, List.of(), Map.of());
    }

    /** This force with its formations and unassigned units as given, all else as it is. */
    ToeSnapshot with(Group root, List<Card> unassigned) {
        return new ToeSnapshot(force, root, unassigned, bases, templates, summaries);
    }

    /**
     * A formation: its own units and its sub-formations, in the TO&amp;E's order; {@code deployed} whether it's out on
     * a scenario or a StratCon track, so it can't be reorganized until it's back; {@code slots} the places it's
     * authorized, none if it authorizes nothing; {@code echelon} how big a formation it stands for, as
     * {@link FormationLevel#getDepth}: 0 a team, 1 a lance, star or Level II, 2 a company, 3 a battalion and on up, -1
     * if it's not known; {@code template} the name of the template it was last shaped by and {@code rule} the MegaMek
     * formation rule it's meant to meet, each null for none.
     */
    record Group(int id, String name, @Nullable BufferedImage icon, List<Group> children, List<Card> units,
          boolean deployed, List<FormationSlot> slots, int echelon, @Nullable String template,
          @Nullable String rule) {
        /** A formation shaped by no template. */
        Group(int id, String name, @Nullable BufferedImage icon, List<Group> children, List<Card> units,
              boolean deployed, List<FormationSlot> slots, int echelon) {
            this(id, name, icon, children, units, deployed, slots, echelon, null, null);
        }

        /** A formation whose echelon isn't known. */
        Group(int id, String name, @Nullable BufferedImage icon, List<Group> children, List<Card> units,
              boolean deployed, List<FormationSlot> slots) {
            this(id, name, icon, children, units, deployed, slots, -1);
        }

        /** This formation with the sub-formations and units given, all else as it is. */
        Group with(List<Group> children, List<Card> units) {
            return new Group(id, name, icon, children, units, deployed, slots, echelon, template, rule);
        }

        /** This formation shaped as given: its slots, its template's name and its rule. */
        Group shaped(List<FormationSlot> slots, @Nullable String template, @Nullable String rule) {
            return new Group(id, name, icon, children, units, deployed, slots, echelon, template, rule);
        }

        /**
         * How big a formation it stands for, as {@link #echelon}; if that's not known, one up from the biggest under
         * it, a lance for one with none.
         */
        int reckonedEchelon() {
            if (echelon >= 0) {
                return echelon;
            }
            int below = 0;
            for (Group child : children) {
                below = Math.max(below, child.reckonedEchelon());
            }
            return below + 1;
        }

        /** Every unit in the formation and under it. */
        List<Card> allUnits() {
            List<Card> all = new ArrayList<>();
            collect(this, all);
            return all;
        }

        private static void collect(Group group, List<Card> into) {
            into.addAll(group.units());
            for (Group child : group.children()) {
                collect(child, into);
            }
        }
    }

    /**
     * A unit. {@code art} keys its picture {@code image} so the table can share a standee between units that look
     * alike; {@code fatigued} whether any of its crew is tired or worse; {@code deployed} whether it's out on a scenario
     * or a StratCon track, so it can't be moved; {@code place} is where it is ({@link #MAIN_FORCE}, or 1 + a base's
     * index), {@code transit} whether it's on its way somewhere; {@code fit} the kind and weight class of slot it
     * fills exactly.
     */
    record Card(UUID id, String name, String crewName, Kind kind, double tons, @Nullable String art,
          @Nullable BufferedImage image, Condition condition, Work work, Crew crew, boolean fatigued,
          boolean maintained, boolean deployed, boolean transit, int place, FormationSlot fit) {
        /** Whether the unit could take the field today: whole enough, crewed, home and not laid up. */
        boolean ready() {
            return condition.fights() && work == Work.NONE && crew != Crew.NONE && !deployed && !transit;
        }
    }

    /** What a folded formation counts against its units, the most pressing first. */
    enum Issue {
        DEPLOYED, OUT_OF_ACTION, DAMAGED, REPAIR, NO_CREW, FATIGUED, MOTHBALLED, TRANSIT;

        boolean of(Card card) {
            return switch (this) {
                case DEPLOYED -> card.deployed();
                case OUT_OF_ACTION -> !card.condition().fights();
                case DAMAGED -> card.condition().fights() && card.condition() != Condition.SOUND;
                case REPAIR -> card.work() == Work.REPAIR || card.work() == Work.REFIT;
                case NO_CREW -> card.crew() == Crew.NONE || card.crew() == Crew.PARTIAL;
                case FATIGUED -> card.fatigued();
                case MOTHBALLED -> card.work() == Work.MOTHBALLED || card.work() == Work.MOTHBALLING
                      || card.work() == Work.ACTIVATING;
                case TRANSIT -> card.transit();
            };
        }

        /** {@code card}'s most pressing issue, or null if it has none. */
        static @Nullable Issue first(Card card) {
            for (Issue issue : values()) {
                if (issue.of(card)) {
                    return issue;
                }
            }
            return null;
        }

        /** How many of {@code cards} have each issue, by its ordinal. */
        static int[] tally(List<Card> cards) {
            int[] counts = new int[values().length];
            for (Card card : cards) {
                for (Issue issue : values()) {
                    counts[issue.ordinal()] += issue.of(card) ? 1 : 0;
                }
            }
            return counts;
        }
    }

    /** What a unit is, as far as the table draws it differently. */
    enum Kind {
        MEK, VEHICLE, VTOL, NAVAL, AEROSPACE, INFANTRY, BATTLE_ARMOR, PROTOMEK, LARGE_CRAFT, OTHER;

        static Kind of(int unitType) {
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
                default -> OTHER;
            };
        }
    }

    /** How whole a unit is, worst last. */
    enum Condition {
        SOUND, LIGHT, MODERATE, HEAVY, CRIPPLED, INOPERABLE, SALVAGE;

        /** Whether the unit can still take the field, however hurt. */
        boolean fights() {
            return ordinal() < INOPERABLE.ordinal();
        }

        /**
         * The condition of a unit of damage level {@code damage} (one of {@link Entity}'s {@code DMG_} levels) that
         * is {@code functional} and {@code repairable} or not.
         */
        static Condition of(int damage, boolean functional, boolean repairable) {
            if (!repairable) {
                return SALVAGE;
            }
            if (!functional) {
                return INOPERABLE;
            }
            return switch (damage) {
                case Entity.DMG_NONE -> SOUND;
                case Entity.DMG_LIGHT -> LIGHT;
                case Entity.DMG_MODERATE -> MODERATE;
                case Entity.DMG_HEAVY -> HEAVY;
                default -> CRIPPLED;
            };
        }
    }

    /** What's being done to a unit that keeps it out of the field. */
    enum Work {
        NONE, REPAIR, REFIT, MOTHBALLING, ACTIVATING, MOTHBALLED;

        /** The work on a unit, the longest-lasting first: laid up or being laid up, refitted, then repaired. */
        static Work of(boolean mothballed, boolean mothballing, boolean refitting, boolean underRepair) {
            if (mothballing) {
                return mothballed ? ACTIVATING : MOTHBALLING;
            }
            if (mothballed) {
                return MOTHBALLED;
            }
            if (refitting) {
                return REFIT;
            }
            return underRepair ? REPAIR : NONE;
        }
    }

    /** How fully a unit is crewed; a trailer and the like need no crew at all. */
    enum Crew {
        FULL, PARTIAL, NONE, NOT_NEEDED
    }

    /** Every unit on the table: those in formations, then the unassigned. */
    List<Card> allUnits() {
        List<Card> all = root.allUnits();
        all.addAll(unassigned);
        return all;
    }

    /** Reads {@code campaign}'s TO&amp;E. Campaign thread only. */
    static ToeSnapshot of(Campaign campaign) {
        Map<UUID, Integer> places = new HashMap<>();
        for (Unit unit : campaign.getPlayerForce().getHangar().getUnits()) {
            places.put(unit.getId(), MAIN_FORCE);
        }
        List<PlayerBase> playerBases = new ArrayList<>(campaign.getCampaignLocationManager().getPlayerBases());
        playerBases.sort(Comparator.comparing(LocationDisplay::formatBaseName));
        List<String> bases = new ArrayList<>();
        for (int i = 0; i < playerBases.size(); i++) {
            PlayerBase base = playerBases.get(i);
            bases.add(LocationDisplay.formatBaseName(base));
            for (Unit unit : base.getBaseHangar().getUnits()) {
                places.put(unit.getId(), i + 1);
            }
        }
        Set<UUID> placed = new HashSet<>();
        Formation top = campaign.getPlayerForce().getFormations();
        Group root = group(campaign, top, places, placed);
        List<Card> unassigned = new ArrayList<>();
        for (Unit unit : campaign.getPlayerForce().getHangar().getUnits()) {
            addUnassigned(campaign, unit, places, placed, unassigned);
        }
        for (PlayerBase base : playerBases) {
            for (Unit unit : base.getBaseHangar().getUnits()) {
                addUnassigned(campaign, unit, places, placed, unassigned);
            }
        }
        return new ToeSnapshot(campaign.getPlayerForce().getName(), root, unassigned, bases,
              campaign.getPlayerForce().getFormationTemplates().getTemplates(), summaries(campaign, placed));
    }

    /**
     * The designs of the units {@code ids}: as the unit cache has them, or read from the unit itself for a design it
     * lacks or while it is still loading, which this never waits for.
     */
    private static Map<UUID, MekSummary> summaries(Campaign campaign, Set<UUID> ids) {
        MekSummaryCache cache = MekSummaryCache.getInstance();
        boolean cached = cache.isInitialized();
        Map<UUID, MekSummary> summaries = new HashMap<>();
        for (UUID id : ids) {
            Unit unit = campaign.getUnit(id);
            Entity entity = unit == null ? null : unit.getEntity();
            if (entity == null) {
                continue;
            }
            MekSummary summary = cached ? cache.getMek(entity.getShortNameRaw()) : null;
            if (summary == null && cached) {
                summary = cache.getMek(entity.getShortName());
            }
            summaries.put(id, summary == null ? design(entity) : summary);
        }
        return Map.copyOf(summaries);
    }

    /** What the formation rules ask of {@code entity}'s design, read from the unit itself. */
    private static MekSummary design(Entity entity) {
        MekSummary design = new MekSummary();
        design.setName(entity.getShortNameRaw());
        design.setChassis(entity.getChassis());
        design.setModel(entity.getModel());
        design.setUnitType(UnitType.getTypeName(entity.getUnitType()));
        design.setTons(entity.getWeight());
        design.setWeightClass(entity.getWeightClass());
        design.setWalkMp(entity.getWalkMP());
        design.setRunMp(entity.getRunMP());
        design.setJumpMp(entity.getAnyTypeMaxJumpMP());
        design.setTotalArmor(entity.getTotalArmor());
        design.setEquipment(entity.getEquipment());
        design.setUnitRole(entity.getRole());
        return design;
    }

    private static void addUnassigned(Campaign campaign, Unit unit, Map<UUID, Integer> places, Set<UUID> placed,
          List<Card> into) {
        if (placed.add(unit.getId())) {
            Card card = card(campaign, unit, places);
            if (card != null) {
                into.add(card);
            }
        }
    }

    private static Group group(Campaign campaign, Formation formation, Map<UUID, Integer> places, Set<UUID> placed) {
        List<Group> children = new ArrayList<>();
        for (Formation sub : formation.getSubFormations()) {
            children.add(group(campaign, sub, places, placed));
        }
        List<Card> units = new ArrayList<>();
        // getAllChildren orders the formation's units as the TO&E does: crewed by their commanders' rank, then the rest
        for (Object child : formation.getAllChildren(campaign)) {
            if (child instanceof Unit unit && placed.add(unit.getId())) {
                Card card = card(campaign, unit, places);
                if (card != null) {
                    units.add(card);
                }
            }
        }
        BufferedImage icon = formation.getFormationIcon() == null ? null
              : WarTableSnapshot.icon(formation.getFormationIcon().getBaseImage());
        return new Group(formation.getId(), formation.getName(), icon, children, units,
              StaticChecks.isFormationLockedByDeployment(campaign, formation), formation.getSlots(),
              formation.getFormationLevel() == null ? -1 : formation.getFormationLevel().getDepth(),
              formation.getTemplateName(), formation.getFormationRule());
    }

    private static @Nullable Card card(Campaign campaign, Unit unit, Map<UUID, Integer> places) {
        Entity entity = unit.getEntity();
        if (entity == null) {
            return null;
        }
        Person commander = unit.getCommander();
        Crew crew = unit.isNotCrewedEntityType() ? Crew.NOT_NEEDED : unit.getCrew().isEmpty() ? Crew.NONE
              : unit.isFullyCrewed() ? Crew.FULL : Crew.PARTIAL;
        Condition condition = Condition.of(unit.getDamageState(), unit.isFunctional(), unit.isRepairable());
        Work work = Work.of(unit.isMothballed(), unit.isMothballing(), unit.isRefitting(), unit.isUnderRepair());
        int place = places.getOrDefault(unit.getId(), MAIN_FORCE);
        // The whole main force on the move shows in the hub's own header; here only what travels apart from it counts
        boolean transit = !unit.isPresent() || (place != MAIN_FORCE && LocationUtils.isInTransit(unit));
        Camouflage camouflage = paintable(unit.getUtilizedCamouflage(campaign));
        String art = artKey(entity, camouflage);
        BufferedImage image = art(unit, camouflage, art);
        return new Card(unit.getId(), unit.getName(), commander == null ? "" : commander.getFullTitle(),
              Kind.of(entity.getUnitType()), entity.getWeight(), image == null ? null : art, image, condition, work,
              crew, fatigued(campaign, unit), unit.getTech() != null, StaticChecks.isUnitLockedByDeployment(unit),
              transit, place, FormationSlot.of(entity));
    }

    /** Whether, with fatigue in play, any of {@code unit}'s crew is tired or worse. */
    private static boolean fatigued(Campaign campaign, Unit unit) {
        if (!campaign.getCampaignOptions().get(CampaignOption.USE_FATIGUE)) {
            return false;
        }
        for (Person person : unit.getCrew()) {
            if (Fatigue.getEffectiveFatigue(person, campaign) >= TIRED) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code camouflage}, or none when its picture can't be found (a custom camouflage from another install, say):
     * painting with a missing one would cover the unit in the placeholder picture's red cross.
     */
    private static Camouflage paintable(Camouflage camouflage) {
        if (camouflage.hasDefaultCategory() || camouflage.isColourCamouflage()) {
            return camouflage;
        }
        boolean found = PAINTABLE.computeIfAbsent(camouflage.getCategory() + '|' + camouflage.getFilename(),
              key -> camouflage.getBaseImage() != null);
        return found ? camouflage : new Camouflage();
    }

    private static String artKey(Entity entity, Camouflage camouflage) {
        return entity.getShortNameRaw() + '|' + camouflage.getCategory() + '|' + camouflage.getFilename() + '|'
              + camouflage.hashCode();
    }

    /** {@code unit}'s picture in {@code camouflage}, undamaged; the one painted before for the same key if any. */
    private static @Nullable BufferedImage art(Unit unit, Camouflage camouflage, String key) {
        BufferedImage known = ART.get(key);
        if (known != null) {
            return known;
        }
        BufferedImage painted = copy(unit.getImage(null, camouflage, false));
        if (painted != null) {
            if (ART.size() >= ART_KEPT) {
                ART.clear();
            }
            ART.put(key, painted);
        }
        return painted;
    }

    /** A copy of {@code image} no more than {@link #ART_SIZE} pixels across; null for none. */
    static @Nullable BufferedImage copy(@Nullable Image image) {
        if (image == null) {
            return null;
        }
        ImageIcon loaded = new ImageIcon(image);
        int width = loaded.getIconWidth();
        int height = loaded.getIconHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        float scale = Math.min(1, ART_SIZE / (float) Math.max(width, height));
        int w = Math.max(1, Math.round(width * scale));
        int h = Math.max(1, Math.round(height * scale));
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(loaded.getImage(), 0, 0, w, h, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }
}
