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
package mekhq.gui.model.view;

import java.awt.Color;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import megamek.common.universe.FactionTag;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.CurrentLocation;
import mekhq.campaign.JumpDriveProfile;
import mekhq.campaign.JumpPath;
import mekhq.campaign.NavigationRouteAnalysis.PathAssessment;
import mekhq.campaign.NavigationRouteAnalysis.Severity;
import mekhq.campaign.RouteAlternativesPlanner.PlanningResult;
import mekhq.campaign.base.PlayerBase;
import mekhq.campaign.campaignOptions.CampaignOption;
import mekhq.campaign.events.LocationChangedEvent;
import mekhq.campaign.events.NewDayEvent;
import mekhq.campaign.events.PlanetarySystemsChangedEvent;
import mekhq.campaign.events.TransitStatusChangedEvent;
import mekhq.campaign.events.missions.MissionEvent;
import mekhq.campaign.events.scenarios.ScenarioEvent;
import mekhq.campaign.finances.Money;
import mekhq.campaign.mission.contract.AbstractContract;
import mekhq.campaign.mission.scenarios.Scenario;
import mekhq.campaign.personnel.skills.SkillType;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.enums.CapitalType;
import mekhq.campaign.universe.enums.HPGRating;

/**
 * The inner sphere as the campaign sees it today: every system where it lies, in its owner's colour, the system the
 * force is at and the route it is following.
 *
 * @param date    the campaign's date
 * @param stars   every system with a position
 * @param current the id of the system the force is at, or travelling from; null when nowhere
 * @param route   the ids of the systems on the force's route, the current one first; empty when it isn't travelling
 * @param voyage  how far the force is along its route; null when it isn't travelling
 * @param marks   what the campaign has at systems by their ids: {@link #MISSION}, {@link #SCENARIO} and {@link #BASE}
 *                together
 */
public record StarMap(LocalDate date, List<Star> stars, @Nullable String current, List<String> route,
      @Nullable Voyage voyage, Map<String, Integer> marks) {
    /** A mark: the target of one of the campaign's active contracts. */
    public static final int MISSION = 1;
    /** A mark: a scenario waiting to be fought. */
    public static final int SCENARIO = 2;
    /** A mark: one of the force's bases. */
    public static final int BASE = 4;
    /** The colour of a system no one holds. */
    static final int UNCLAIMED = 0x8A9099;

    /**
     * A system on the map.
     *
     * @param id      its id
     * @param name    its name today
     * @param x       light years spinward of Terra
     * @param y       light years coreward of Terra
     * @param rgb     its owner's colour, 0xRRGGBB
     * @param owner   its owners' names today, or blank when no one holds it
     * @param faction the name of the owner whose colour it wears, or blank
     * @param settled whether anyone but the abandoned holds it
     * @param lenses  every {@link MapLens}'s level for it, as {@link MapLens#pack} packs them
     * @param capital whose capital it is: 0 no one's, 1 a district's, 2 a region's, 3 a realm's
     * @param people  how many live there
     */
    public record Star(String id, String name, double x, double y, int rgb, String owner, String faction,
          boolean settled, long lenses, int capital, long people) {
        public Star {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(faction);
        }

        /** A system with no lenses, capital or people. */
        public Star(String id, String name, double x, double y, int rgb, String owner, String faction,
              boolean settled) {
            this(id, name, x, y, rgb, owner, faction, settled, 0, 0, 0);
        }

        /** {@code lens}'s level for it, 0 when the lens doesn't rate it. */
        public int level(MapLens lens) {
            return lens.level(lenses);
        }
    }

    /**
     * What the map shows of a system when it is chosen.
     *
     * @param id        its id
     * @param name      its name today
     * @param owner     its owners' names today, or blank
     * @param rgb       its owner's colour, 0xRRGGBB
     * @param population its population today
     * @param hpg       its HPG rating's name
     * @param star      its primary star's spectral type, or blank when unknown
     * @param recharge  the hours a jump drive takes to recharge there
     * @param distance  the light years from the force's system; 0 when the force is nowhere
     * @param jumps     the jumps from the force's system on the course there, or -1 when there is none
     * @param here      whether the force is there
     * @param course    the course there from the force's system; null when the force is there, nowhere or has none
     */
    public record Detail(String id, String name, String owner, int rgb, long population, String hpg, String star,
          double recharge, double distance, int jumps, boolean here, @Nullable Course course) {
        public Detail {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(hpg);
            Objects.requireNonNull(star);
        }
    }

    /** Whether the force can set out on a course now. */
    public enum Departure {
        /** It can. */
        READY,
        /** It is already following a route. */
        UNDERWAY,
        /** The course runs where the force may not go. */
        BLOCKED
    }

    /**
     * A course from the force's system to a chosen one, plotted as the Navigation tab plots it.
     *
     * @param route    the ids of the systems on it, the force's first
     * @param jumps    its jumps
     * @param days     the days it takes, rounded up
     * @param recharge the hours the drives spend recharging on the way
     * @param cost     what the journey costs, or blank when the campaign doesn't pay for transport
     * @param state    whether the force can set out on it now
     */
    public record Course(List<String> route, int jumps, int days, double recharge, String cost, Departure state) {
        public Course {
            route = List.copyOf(route);
            Objects.requireNonNull(cost);
            Objects.requireNonNull(state);
        }
    }

    /** What the force is doing on its route. */
    public enum Stage {
        /** Flying out to the jump point, its drives charging as it goes. */
        OUTBOUND,
        /** At the jump point, charging its drives for the next jump. */
        CHARGING,
        /** In the last system, flying in to its destination. */
        INBOUND
    }

    /**
     * How far the force is along its route.
     *
     * @param destination the name of the system the route ends at
     * @param jumps       the jumps still to make
     * @param days        the days still to go, rounded up
     * @param stage       what the force is doing
     * @param leg         how ready it is, 0 to 1, for the next jump (both at the jump point and charged), or on the
     *                    last leg how far in it has come
     */
    public record Voyage(String destination, int jumps, int days, Stage stage, double leg) {
        public Voyage {
            Objects.requireNonNull(destination);
            Objects.requireNonNull(stage);
        }
    }

    public StarMap {
        Objects.requireNonNull(date);
        stars = List.copyOf(stars);
        route = List.copyOf(route);
        marks = Map.copyOf(marks);
    }

    /** A map with nothing of the campaign's marked. */
    public StarMap(LocalDate date, List<Star> stars, @Nullable String current, List<String> route,
          @Nullable Voyage voyage) {
        this(date, stars, current, route, voyage, Map.of());
    }

    /** The map, rebuilt as the day, the force's whereabouts or the systems change. */
    public static ViewModel<StarMap> model() {
        return ViewModel.of("StarMap", StarMap::of, List.of(NewDayEvent.class, LocationChangedEvent.class,
              TransitStatusChangedEvent.class, PlanetarySystemsChangedEvent.class, MissionEvent.class,
              ScenarioEvent.class));
    }

    static StarMap of(Campaign campaign) {
        LocalDate date = campaign.getLocalDate();
        List<Star> stars = stars(campaign.getSystems(), date);
        AbstractLocation location = campaign.getPlayerForce().getForceDetachment().getCurrentLocation();
        PlanetarySystem here = location == null ? null : location.getCurrentSystem();
        JumpPath path = location == null ? null : location.getJumpPath();
        List<String> route = new ArrayList<>();
        Voyage voyage = null;
        if (path != null && !path.isEmpty()) {
            for (PlanetarySystem system : path.getSystems()) {
                route.add(system.getId());
            }
            voyage = voyage(campaign, location, path);
        }
        return new StarMap(date, stars, here == null ? null : here.getId(), route, voyage, marks(campaign));
    }

    /** What the campaign has where: its contracts' targets, its scenarios' and its bases. */
    private static Map<String, Integer> marks(Campaign campaign) {
        Map<String, Integer> marks = new HashMap<>();
        for (AbstractContract contract : campaign.getActiveContracts()) {
            mark(marks, contract.getTargetSystem(), MISSION);
        }
        for (Scenario scenario : campaign.getActiveScenarios()) {
            AbstractContract contract = campaign.getContract(scenario.getMissionId());
            if (contract != null) {
                mark(marks, contract.getTargetSystem(), SCENARIO);
            }
        }
        for (PlayerBase base : campaign.getCampaignLocationManager().getPlayerBases()) {
            AbstractLocation location = base.getCurrentLocation();
            mark(marks, location == null ? null : location.getCurrentSystem(), BASE);
        }
        return marks;
    }

    private static void mark(Map<String, Integer> marks, @Nullable PlanetarySystem system, int mark) {
        if (system != null && system.getId() != null) {
            marks.merge(system.getId(), mark, (had, more) -> had | more);
        }
    }

    /**
     * Every system of {@code systems} with a position as it stood on {@code date}. It reads only the systems, so it can
     * be called on any thread.
     */
    static List<Star> stars(Iterable<PlanetarySystem> systems, LocalDate date) {
        int year = date.getYear();
        List<Star> stars = new ArrayList<>();
        for (PlanetarySystem system : systems) {
            if (system == null || system.getId() == null || system.getX() == null || system.getY() == null) {
                continue;
            }
            List<Faction> owners = owners(system, date);
            boolean settled = false;
            for (Faction owner : owners) {
                settled |= !owner.is(FactionTag.ABANDONED);
            }
            String faction = owners.isEmpty() ? null : owners.get(0).getFullName(year);
            stars.add(new Star(system.getId(), name(system, date), system.getX(), system.getY(), colour(owners),
                  ownerNames(owners, year), faction == null ? "" : faction, settled,
                  settled ? MapLens.pack(system, date) : 0, settled ? capital(system, date) : 0,
                  settled ? system.getPopulation(date) : 0));
        }
        return stars;
    }

    /** Whose capital {@code system} is on {@code date}: 0 no one's, 1 a district's, 2 a region's, 3 a realm's. */
    private static int capital(PlanetarySystem system, LocalDate date) {
        CapitalType type = system.getCapitalType(date);
        return type == null ? 0 : switch (type) {
            case NATIONAL -> 3;
            case REGION -> 2;
            case DISTRICT -> 1;
            case NONE -> 0;
        };
    }

    /** Swing thread: the detail of the system {@code id}, or null when the campaign has no such system. */
    public static @Nullable Detail detail(Campaign campaign, String id) {
        PlanetarySystem system = campaign.getSystemById(id);
        if (system == null) {
            return null;
        }
        LocalDate date = campaign.getLocalDate();
        List<Faction> owners = owners(system, date);
        AbstractLocation location = campaign.getPlayerForce().getForceDetachment().getCurrentLocation();
        PlanetarySystem here = location == null ? null : location.getCurrentSystem();
        double distance = 0;
        int jumps = -1;
        Course course = null;
        boolean atSystem = here != null && Objects.equals(here.getId(), system.getId());
        if (atSystem) {
            jumps = 0;
        } else if (here != null) {
            distance = here.getDistanceTo(system);
            JumpPath path = plot(campaign, here, system);
            if (path != null) {
                course = course(campaign, location, path);
                jumps = course.jumps();
            }
        }
        HPGRating hpg = system.getHPG(date);
        return new Detail(system.getId(), name(system, date), ownerNames(owners, date.getYear()), colour(owners),
              system.getPopulation(date), hpg == null ? HPGRating.X.toString() : hpg.toString(),
              system.getStar() == null ? "" : system.getStar().toString(),
              system.getRechargeTime(date, campaign.isUseCommandCircuit()), distance, jumps, atSystem, course);
    }

    /**
     * Swing thread: the course from the force's system to the system {@code id} that its {@link Detail} shows, for
     * setting out on; null when the force is nowhere or already there, or there is no such system or course.
     */
    public static @Nullable JumpPath plot(Campaign campaign, String id) {
        PlanetarySystem system = campaign.getSystemById(id);
        AbstractLocation location = campaign.getPlayerForce().getForceDetachment().getCurrentLocation();
        PlanetarySystem here = location == null ? null : location.getCurrentSystem();
        if (system == null || here == null || Objects.equals(here.getId(), system.getId())) {
            return null;
        }
        return plot(campaign, here, system);
    }

    /** Swing thread: how the force's course {@code path} fares by the Navigation tab's rules. */
    public static PathAssessment assess(Campaign campaign, JumpPath path) {
        boolean commandCircuit = campaign.isUseCommandCircuit();
        return campaign.assessNavigationPath(path.getSystems(), List.of(path.getLastSystem()),
              destination -> commandCircuit);
    }

    /** The Navigation tab's course from {@code from} to {@code to}, or null when there is none. */
    private static @Nullable JumpPath plot(Campaign campaign, PlanetarySystem from, PlanetarySystem to) {
        PlanningResult planned = campaign.calculateJumpPathForPlanning(from, to);
        return planned == null || !planned.routeFound() ? null : planned.path();
    }

    private static Course course(Campaign campaign, AbstractLocation location, JumpPath path) {
        LocalDate date = campaign.getLocalDate();
        boolean commandCircuit = campaign.isUseCommandCircuit();
        JumpDriveProfile profile = campaign.getJumpDriveProfile();
        int days = (int) Math.ceil(path.getTotalTime(date, location.getTransitTime(), commandCircuit, profile));
        String cost = "";
        if (Boolean.TRUE.equals(campaign.getCampaignOptions().get(CampaignOption.PAY_FOR_TRANSPORT))) {
            Money journey = campaign.getTransportCostCalculation(SkillType.EXP_REGULAR)
                  .calculateCostForEntireJourney(days, path);
            cost = journey.toAmountAndSymbolString();
        }
        JumpPath active = location.getJumpPath();
        Departure state;
        if (active != null && !active.isEmpty()) {
            state = Departure.UNDERWAY;
        } else {
            PathAssessment assessment = assess(campaign, path);
            state = assessment == null || assessment.severity() == Severity.BLOCKED ? Departure.BLOCKED
                  : Departure.READY;
        }
        List<String> route = new ArrayList<>();
        for (PlanetarySystem system : path.getSystems()) {
            route.add(system.getId());
        }
        return new Course(route, path.getJumps(), days,
              path.getTotalRechargeTime(date, commandCircuit, profile) * 24, cost, state);
    }

    /** How far the force at {@code location} is along {@code path}, or null when it can't tell. */
    private static @Nullable Voyage voyage(Campaign campaign, AbstractLocation location, JumpPath path) {
        PlanetarySystem here = location.getCurrentSystem();
        PlanetarySystem end = path.getLastSystem();
        if (here == null || end == null) {
            return null;
        }
        LocalDate date = campaign.getLocalDate();
        double transit = location.getTransitTime();
        // On the last leg the time still to go is the flight in itself
        double remaining = path.size() > 1 ? path.getTotalTime(date, transit, campaign.isUseCommandCircuit(),
              campaign.getJumpDriveProfile()) : transit;
        int days = (int) Math.ceil(remaining);
        Stage stage;
        double leg;
        if (path.size() > 1) {
            double toJumpPoint = here.getTimeToJumpPoint(1.0);
            double outbound = toJumpPoint <= 0 ? 1 : transit / toJumpPoint;
            stage = outbound < 1 ? Stage.OUTBOUND : Stage.CHARGING;
            // It jumps once it is both at the jump point and charged
            leg = Math.min(outbound, charged(campaign, location));
        } else {
            double approach = path.isInSystemTransfer() ? path.getInSystemTransferTime() : path.getEndTime();
            stage = Stage.INBOUND;
            leg = approach <= 0 ? 1 : 1 - transit / approach;
        }
        return new Voyage(name(end, date), path.getJumps(), Math.max(0, days), stage,
              Math.max(0, Math.min(1, leg)));
    }

    /** How charged, 0 to 1, the force's drives are for their next jump. */
    private static double charged(Campaign campaign, AbstractLocation location) {
        if (!(location instanceof CurrentLocation travelling)) {
            return 1;
        }
        double needed = travelling.getNeededRechargeTime(campaign);
        return needed <= 0 ? 1 : travelling.getRechargeTime() / needed;
    }

    /** The system's owners today, settled ones first, each in name order. */
    private static List<Faction> owners(PlanetarySystem system, LocalDate date) {
        Set<Faction> factions = system.getFactionSet(date);
        if (factions == null || factions.isEmpty()) {
            return List.of();
        }
        List<Faction> sorted = new ArrayList<>(factions);
        sorted.removeIf(Objects::isNull);
        sorted.sort(Comparator.comparing((Faction faction) -> faction.is(FactionTag.ABANDONED))
              .thenComparing(Faction::getShortName, Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    private static int colour(List<Faction> owners) {
        return owners.isEmpty() ? UNCLAIMED : rgb(owners.get(0));
    }

    /** {@code faction}'s colour, 0xRRGGBB. */
    static int rgb(Faction faction) {
        Color colour = faction.getColor();
        return colour == null ? UNCLAIMED : colour.getRGB() & 0xFFFFFF;
    }

    static String ownerNames(List<Faction> owners, int year) {
        return owners.stream().map(faction -> faction.getFullName(year)).filter(Objects::nonNull)
              .collect(Collectors.joining(" / "));
    }

    private static String name(PlanetarySystem system, LocalDate date) {
        String name = system.getPrintableName(date);
        return name == null || name.isBlank() ? system.getId() : name;
    }
}
