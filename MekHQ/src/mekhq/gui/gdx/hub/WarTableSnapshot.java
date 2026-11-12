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

import static mekhq.utilities.MHQInternationalization.getTextAt;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import javax.swing.ImageIcon;

import jakarta.annotation.Nullable;
import megamek.common.loaders.MapSettings;
import megamek.common.units.UnitType;
import mekhq.campaign.Campaign;
import mekhq.campaign.digitalGM.stratCon.StratConCampaignState;
import mekhq.campaign.digitalGM.stratCon.StratConCoords;
import mekhq.campaign.digitalGM.stratCon.StratConRulesManager;
import mekhq.campaign.digitalGM.stratCon.StratConScenario;
import mekhq.campaign.digitalGM.stratCon.StratConTrackState;
import mekhq.campaign.digitalGM.stratCon.biome.StratConBiomeManifest;
import mekhq.campaign.digitalGM.stratCon.biome.StratConTerrainCategory;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacility;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacility.FacilityTier;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacility.FacilityType;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacilityOperations;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacilitySupply;
import mekhq.campaign.digitalGM.stratCon.facility.StratConRoadCut;
import mekhq.campaign.digitalGM.stratCon.facility.StratConSupplyLink;
import mekhq.campaign.digitalGM.stratCon.pointOfInterest.StratConPointOfInterest;
import mekhq.campaign.force.Formation;
import mekhq.campaign.mission.contract.AbstractContract;
import mekhq.campaign.mission.scenarios.AtBDynamicScenario;
import mekhq.campaign.mission.scenarios.ScenarioForceTemplate;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.utilities.ScenarioUtils;

/**
 * What the war table shows of an operation, copied from its StratCon state on the campaign's thread so the table never
 * reads the campaign. Only what the player knows is copied: unscouted hexes keep their terrain but say they're
 * unscouted, and facilities, scenarios and points of interest the player hasn't found are left out, by
 * {@code StratConPanel}'s rules.
 *
 * @param contract      the contract's ID, which orders given at the table name it by
 * @param operation     the contract's name
 * @param sectors       its sectors, in the contract's order
 * @param victoryPoints the operation's victory points
 * @param supportPoints its support points
 */
record WarTableSnapshot(UUID contract, String operation, List<Sector> sectors, int victoryPoints,
      int supportPoints) {
    /** The largest a force's icon is copied, in pixels across. */
    static final int ICON_SIZE = 128;
    /** What recalling a force costs, in support points. */
    static final int RECALL_COST = StratConRulesManager.RECALL_FORMATION_SUPPORT_POINT_COST;
    private static final String STRATCON_BUNDLE = "mekhq.resources.AtBStratCon";

    WarTableSnapshot {
        sectors = List.copyOf(sectors);
    }

    /**
     * The operations of {@code campaign}'s active contracts that StratCon runs: the one at the system {@code systemId}
     * first when there is one, then those where the force is, then the rest. Campaign thread.
     */
    static List<WarTableSnapshot> operations(Campaign campaign, @Nullable String systemId) {
        List<AbstractContract> contracts = new ArrayList<>();
        for (AbstractContract contract : campaign.getActiveContracts(false)) {
            StratConCampaignState state = contract.getStratConCampaignState();
            if (state != null && !state.getTracks().isEmpty()) {
                contracts.add(contract);
            }
        }
        PlanetarySystem here = campaign.getCurrentSystem();
        contracts.sort(Comparator.comparingInt(contract -> {
            PlanetarySystem system = contract.getTargetSystem();
            if (system != null && systemId != null && systemId.equals(system.getId())) {
                return 0;
            }
            return system != null && system.equals(here) ? 1 : 2;
        }));
        LocalDate today = campaign.getLocalDate();
        IntFunction<String> forceName = id -> {
            Formation formation = campaign.getPlayerForce().getFormation(id);
            return formation == null ? "" : formation.getName();
        };
        IntFunction<BufferedImage> forceIcon = id -> {
            Formation formation = campaign.getPlayerForce().getFormation(id);
            return formation == null || formation.getFormationIcon() == null ? null
                  : icon(formation.getFormationIcon().getBaseImage());
        };
        List<WarTableSnapshot> operations = new ArrayList<>();
        for (AbstractContract contract : contracts) {
            StratConCampaignState state = contract.getStratConCampaignState();
            List<Sector> sectors = new ArrayList<>();
            for (StratConTrackState track : state.getTracks()) {
                Forces forces = new Forces() {
                    @Override
                    public String name(int force) {
                        return forceName.apply(force);
                    }

                    @Override
                    public @Nullable BufferedImage icon(int force) {
                        return forceIcon.apply(force);
                    }

                    @Override
                    public String hold(int force) {
                        return WarTableSnapshot.hold(campaign, state, track, force);
                    }
                };
                sectors.add(of(track, today, forces, reserve(campaign, state, track, forces)));
            }
            operations.add(new WarTableSnapshot(contract.getId(), contract.getName(), sectors,
                  state.getVictoryPoints(), state.getSupportPoints()));
        }
        return operations;
    }

    /**
     * The forces that could be sent onto {@code track} now, as StratCon's deployment wizard offers them for a bare
     * hex. Campaign thread.
     */
    private static List<Reserve> reserve(Campaign campaign, StratConCampaignState state, StratConTrackState track,
          Forces forces) {
        if (campaign.getPlayerForce() == null) {
            return List.of();
        }
        List<Reserve> reserve = new ArrayList<>();
        for (int force : StratConRulesManager.getAvailableForceIDsForManualDeployment(
              ScenarioForceTemplate.SPECIAL_UNIT_TYPE_ATB_MIX, campaign, track, false, null, state, false)) {
            Formation formation = campaign.getPlayerForce().getFormation(force);
            Formation parent = formation == null ? null : formation.getParentFormation();
            reserve.add(new Reserve(force, forces.name(force), forces.icon(force), kind(campaign, formation),
                  parent == null ? "" : parent.getName()));
        }
        return reserve;
    }

    /** What {@code formation} mostly fields, as the reserve sorts it. Campaign thread. */
    private static Kind kind(Campaign campaign, @Nullable Formation formation) {
        if (formation == null) {
            return Kind.GROUND;
        }
        for (UUID id : formation.getAllUnits(false)) {
            if (campaign.getUnit(id) == null || campaign.getUnit(id).getEntity() == null) {
                return Kind.GROUND;
            }
        }
        return switch (formation.getPrimaryUnitType(campaign)) {
            case UnitType.CONV_FIGHTER, UnitType.AEROSPACE_FIGHTER, UnitType.SMALL_CRAFT, UnitType.DROPSHIP,
                 UnitType.JUMPSHIP, UnitType.WARSHIP, UnitType.SPACE_STATION, UnitType.AERO -> Kind.AEROSPACE;
            case UnitType.INFANTRY, UnitType.BATTLE_ARMOR -> Kind.INFANTRY;
            default -> Kind.GROUND;
        };
    }

    /** Why {@code force} can't be recalled from {@code track} now, as StratCon's panel says it; blank if it can. */
    private static String hold(Campaign campaign, StratConCampaignState state, StratConTrackState track, int force) {
        if (StratConRulesManager.canRecallFormation(force, track, state, campaign)) {
            return "";
        }
        Formation formation = campaign.getPlayerForce().getFormation(force);
        String key = formation != null && formation.isDeployed() ? "stratConTab.contextMenu.recall.inScenario"
              : track.getFacilityOrder(force) != null ? "stratConTab.contextMenu.recall.facilityOrder"
              : "stratConTab.contextMenu.recall.noSupport";
        return getTextAt(STRATCON_BUNDLE, key);
    }

    /** What the table knows of the player's forces, by their IDs. */
    interface Forces {
        String name(int force);

        /** The force's icon, at most {@link #ICON_SIZE} pixels across, or null for none. */
        @Nullable BufferedImage icon(int force);

        /** Why the force can't be recalled from the sector now; blank when it can. */
        default String hold(int force) {
            return "";
        }
    }

    /**
     * One sector's table.
     *
     * @param name        the sector's name
     * @param width       its width in hexes
     * @param height      its height in hexes
     * @param temperature its temperature, as StratCon keeps it
     * @param hexes       its hexes with terrain; a sector's shape leaves some of its rectangle empty
     * @param sites       the facilities the player knows of
     * @param beacons     the scenarios the player knows of
     * @param tokens      the player's forces deployed on it
     * @param supply      both sides' supply networks
     * @param cuts        where roads are cut
     * @param marks       the points of interest the player can see
     * @param reserve     the player's forces that could be sent onto it now
     */
    record Sector(String name, int width, int height, int temperature, List<Hex> hexes, List<Site> sites,
          List<Beacon> beacons, List<Token> tokens, List<Link> supply, List<Spot> cuts, List<Mark> marks,
          List<Reserve> reserve) {
        Sector {
            hexes = List.copyOf(hexes);
            sites = List.copyOf(sites);
            beacons = List.copyOf(beacons);
            tokens = List.copyOf(tokens);
            supply = List.copyOf(supply);
            cuts = List.copyOf(cuts);
            marks = List.copyOf(marks);
            reserve = List.copyOf(reserve);
        }

        /** The hex at ({@code x}, {@code y}), or null where the sector has none. */
        @Nullable Hex hex(int x, int y) {
            for (Hex hex : hexes) {
                if (hex.x() == x && hex.y() == y) {
                    return hex;
                }
            }
            return null;
        }
    }

    /** How high a hex's column stands on the table, from its terrain. */
    enum Relief {
        WATER(0.15f), LOW(0.45f), FLAT(0.55f), RAISED(0.85f), HIGH(1.35f);

        /** The column's height, in table units (a hex is one across). */
        final float height;

        Relief(float height) {
            this.height = height;
        }

        static Relief of(@Nullable StratConTerrainCategory category) {
            if (category == null) {
                return FLAT;
            }
            return switch (category) {
                case OCEAN -> WATER;
                case MOUNTAIN, VOLCANIC -> HIGH;
                case HILLS -> RAISED;
                case AGRICULTURE, VEGETATION -> LOW;
                case URBAN, LUNAR, BARREN, NEUTRAL -> FLAT;
            };
        }
    }

    /**
     * A hex of the sector.
     *
     * @param terrain the terrain type's name, as StratCon calls it
     * @param relief  how high its column stands
     * @param image   the path of its terrain tile, or null for none
     * @param scouted whether the player has scouted it
     * @param road    whether a road runs through it
     * @param city    whether it's a city
     * @param open    whether a force could be sent onto it now
     */
    record Hex(int x, int y, String terrain, Relief relief, @Nullable String image, boolean scouted, boolean road,
          boolean city, boolean open) {
    }

    /** A hex's place, for the marks that need no more than that. */
    record Spot(int x, int y) {
    }

    /**
     * A facility.
     *
     * @param type      what it is, or null when StratCon doesn't say
     * @param tier      how large it is, or null when the player hasn't scouted it
     * @param allied    whether its owner is on the player's side
     * @param objective whether holding or taking it is one of the contract's objectives
     * @param cutOff    whether it's cut off from its supply
     * @param detail    what the player knows of its tier, condition and garrison; blank for nothing
     */
    record Site(int x, int y, String name, @Nullable FacilityType type, @Nullable FacilityTier tier, boolean allied,
          boolean objective, boolean cutOff, String detail) {
    }

    /**
     * A scenario.
     *
     * @param daysLeft  the days until it's fought, 0 for today or past; -1 when it has no date
     * @param objective whether it's one of the contract's objectives
     * @param briefing  its short briefing; blank for none
     * @param map       the settings its board is made from, or null when it has none on the ground
     */
    record Beacon(int x, int y, String name, int daysLeft, boolean objective, String briefing,
          @Nullable MapSettings map) {
    }

    /**
     * A force of the player's.
     *
     * @param force the force's ID
     * @param icon  its TO&amp;E icon, at most {@link #ICON_SIZE} pixels across, or null for none; never drawn on after
     *              it's copied
     * @param hold  why it can't be recalled now; blank when it can
     */
    record Token(int force, int x, int y, String name, @Nullable BufferedImage icon, String hold) {
    }

    /**
     * A force of the player's that could be sent onto the sector.
     *
     * @param force its ID
     * @param icon  its TO&amp;E icon, as a {@link Token}'s, or null for none
     * @param kind  what it mostly fields
     * @param group the name of the force it belongs to in the TO&amp;E, blank for none
     */
    record Reserve(int force, String name, @Nullable BufferedImage icon, Kind kind, String group) {
        Reserve(int force, String name, @Nullable BufferedImage icon) {
            this(force, name, icon, Kind.GROUND, "");
        }
    }

    /** What a force mostly fields, as the reserve sorts forces. */
    enum Kind {
        GROUND,
        AEROSPACE,
        INFANTRY
    }

    /** A link of a supply network, from one hex to the next towards its source. */
    record Link(int fromX, int fromY, int toX, int toY, boolean player) {
    }

    /** A point of interest. */
    record Mark(int x, int y, String name, String description, boolean allied) {
    }

    /**
     * Copies {@code track} as the player sees it on {@code today}; {@code forceName} names a force by its ID. Campaign
     * thread.
     */
    static Sector of(StratConTrackState track, LocalDate today, IntFunction<String> forceName) {
        return of(track, today, forceName, id -> null);
    }

    /**
     * Copies {@code track} as the player sees it on {@code today}; {@code forceName} names a force by its ID and
     * {@code forceIcon} gives its icon, or null. Campaign thread.
     */
    static Sector of(StratConTrackState track, LocalDate today, IntFunction<String> forceName,
          IntFunction<BufferedImage> forceIcon) {
        return of(track, today, new Forces() {
            @Override
            public String name(int force) {
                return forceName.apply(force);
            }

            @Override
            public @Nullable BufferedImage icon(int force) {
                return forceIcon.apply(force);
            }
        }, List.of());
    }

    /**
     * Copies {@code track} as the player sees it on {@code today}, its forces as {@code forces} knows them and
     * {@code reserve} the forces that could be sent onto it. Campaign thread.
     */
    static Sector of(StratConTrackState track, LocalDate today, Forces forces, List<Reserve> reserve) {
        boolean revealed = track.hasActiveTrackReveal() || track.isGmRevealed();
        StratConBiomeManifest biomes = StratConBiomeManifest.getInstance();
        Set<StratConCoords> roads = track.getRoads();
        Set<StratConCoords> cities = track.getCities();
        List<Hex> hexes = new ArrayList<>();
        for (int x = 0; x < track.getWidth(); x++) {
            for (int y = 0; y < track.getHeight(); y++) {
                StratConCoords coords = new StratConCoords(x, y);
                String type = track.getTerrainTile(coords);
                if (type == null || type.isBlank() || track.isOffTrack(coords)) {
                    continue;
                }
                String image = biomes.getBiomeImage(type);
                hexes.add(new Hex(x, y, type, Relief.of(biomes.getTerrainCategory(type)),
                      image == null || image.isBlank() ? null : image, revealed || track.coordsRevealed(x, y),
                      roads.contains(coords), cities.contains(coords),
                      StratConRulesManager.canManuallyDeployAnyForce(coords, track)));
            }
        }

        List<Site> sites = new ArrayList<>();
        for (Map.Entry<StratConCoords, StratConFacility> entry : track.getFacilities().entrySet()) {
            StratConFacility facility = entry.getValue();
            if (!StratConFacilityOperations.isKnownToPlayer(track, facility)) {
                continue;
            }
            StratConCoords coords = entry.getKey();
            boolean scouted = facility.getIntel() == StratConFacility.FacilityIntel.SCOUTED
                  || facility.getIntel() == StratConFacility.FacilityIntel.DETAILED;
            sites.add(new Site(coords.getX(), coords.getY(), facility.getDisplayableName(),
                  facility.getFacilityType(), scouted ? facility.getTier() : null,
                  facility.isOwnerAlliedToPlayer(), facility.isStrategicObjective(),
                  StratConFacilitySupply.isCutOff(track, coords), detail(facility)));
        }

        List<Beacon> beacons = new ArrayList<>();
        for (Map.Entry<StratConCoords, StratConScenario> entry : track.getScenarios().entrySet()) {
            StratConCoords coords = entry.getKey();
            StratConScenario scenario = entry.getValue();
            boolean known = scenario.getDeploymentDate() != null || revealed
                  || (scenario.isStrategicObjective() && track.getRevealedCoords().contains(coords));
            if (known) {
                beacons.add(beacon(coords, scenario, today));
            }
        }

        List<Token> tokens = new ArrayList<>();
        for (Map.Entry<StratConCoords, Set<Integer>> entry : track.getAssignedCoordForces().entrySet()) {
            for (int force : entry.getValue()) {
                tokens.add(new Token(force, entry.getKey().getX(), entry.getKey().getY(), forces.name(force),
                      forces.icon(force), forces.hold(force)));
            }
        }

        List<Link> supply = new ArrayList<>();
        for (StratConSupplyLink link : track.getPlayerSupplyNetwork().getLinks()) {
            supply.add(link(link, true));
        }
        for (StratConSupplyLink link : track.getEnemySupplyNetwork().getLinks()) {
            StratConCoords from = link.getFrom();
            if (revealed || track.coordsRevealed(from.getX(), from.getY())) {
                supply.add(link(link, false));
            }
        }

        List<Spot> cuts = new ArrayList<>();
        for (StratConRoadCut cut : track.getRoadCuts()) {
            cuts.add(new Spot(cut.getCoords().getX(), cut.getCoords().getY()));
        }

        List<Mark> marks = new ArrayList<>();
        for (StratConPointOfInterest point : track.getPointsOfInterest()) {
            if (point.isVisibleToPlayer(track) && point.getCoords() != null) {
                String description = point.getDescription();
                marks.add(new Mark(point.getCoords().getX(), point.getCoords().getY(), point.getDisplayableName(),
                      description == null ? "" : description, point.isOwnerAlliedToPlayer()));
            }
        }

        return new Sector(track.getDisplayableName(), track.getWidth(), track.getHeight(),
              track.getTemperature(), hexes, sites, beacons, tokens, supply, cuts, marks, reserve);
    }

    private static Link link(StratConSupplyLink link, boolean player) {
        return new Link(link.getFrom().getX(), link.getFrom().getY(), link.getTo().getX(), link.getTo().getY(),
              player);
    }

    private static Beacon beacon(StratConCoords coords, StratConScenario scenario, LocalDate today) {
        LocalDate date = scenario.getDeploymentDate() != null ? scenario.getDeploymentDate()
              : scenario.getActionDate();
        int daysLeft = date == null ? -1 : (int) Math.max(0, ChronoUnit.DAYS.between(today, date));
        String briefing = scenario.getScenarioTemplate() == null || scenario.getScenarioTemplate().shortBriefing == null
              ? "" : scenario.getScenarioTemplate().shortBriefing;
        AtBDynamicScenario backing = scenario.getBackingScenario();
        MapSettings map = backing == null || backing.getMap() == null ? null : ScenarioUtils.getMapSettings(backing);
        return new Beacon(coords.getX(), coords.getY(), scenario.getName(), daysLeft,
              scenario.isStrategicObjective(), briefing, map);
    }

    /**
     * A copy of a force's icon for the table, no more than {@link #ICON_SIZE} pixels across so its cutout stays light;
     * null for none.
     */
    static @Nullable BufferedImage icon(@Nullable Image image) {
        if (image == null) {
            return null;
        }
        ImageIcon loaded = new ImageIcon(image);
        int width = loaded.getIconWidth();
        int height = loaded.getIconHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        float scale = Math.min(1, ICON_SIZE / (float) Math.max(width, height));
        int w = Math.max(1, Math.round(width * scale));
        int h = Math.max(1, Math.round(height * scale));
        BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(loaded.getImage(), 0, 0, w, h, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static String detail(StratConFacility facility) {
        return switch (facility.getIntel()) {
            case DETAILED -> facility.getTier() + ", " + facility.getCondition() + ", " + facility.getGarrison() + "/"
                  + facility.getGarrisonMaximum();
            case SCOUTED -> facility.getTier() + ", " + facility.getCondition();
            default -> "";
        };
    }
}
