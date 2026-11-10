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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import megamek.common.universe.FactionTag;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.events.LocationChangedEvent;
import mekhq.campaign.events.NewDayEvent;
import mekhq.campaign.events.PlanetarySystemsChangedEvent;
import mekhq.campaign.events.TransitStatusChangedEvent;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.enums.HPGRating;

/**
 * The inner sphere as the campaign sees it today: every system where it lies, in its owner's colour, the system the
 * force is at and the route it is following.
 *
 * @param date    the campaign's date
 * @param stars   every system with a position
 * @param current the id of the system the force is at, or travelling from; null when nowhere
 * @param route   the ids of the systems on the force's route, the current one first; empty when it isn't travelling
 */
public record StarMap(LocalDate date, List<Star> stars, @Nullable String current, List<String> route) {
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
     * @param settled whether anyone but the abandoned holds it
     */
    public record Star(String id, String name, double x, double y, int rgb, String owner, boolean settled) {
        public Star {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            Objects.requireNonNull(owner);
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
     * @param jumps     the jumps from the force's system on the fastest route, or -1 when there is none
     * @param here      whether the force is there
     */
    public record Detail(String id, String name, String owner, int rgb, long population, String hpg, String star,
          double recharge, double distance, int jumps, boolean here) {
        public Detail {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(hpg);
            Objects.requireNonNull(star);
        }
    }

    public StarMap {
        Objects.requireNonNull(date);
        stars = List.copyOf(stars);
        route = List.copyOf(route);
    }

    /** The map, rebuilt as the day, the force's whereabouts or the systems change. */
    public static ViewModel<StarMap> model() {
        return ViewModel.of("StarMap", StarMap::of, List.of(NewDayEvent.class, LocationChangedEvent.class,
              TransitStatusChangedEvent.class, PlanetarySystemsChangedEvent.class));
    }

    static StarMap of(Campaign campaign) {
        LocalDate date = campaign.getLocalDate();
        int year = date.getYear();
        List<Star> stars = new ArrayList<>();
        for (PlanetarySystem system : campaign.getSystems()) {
            if (system == null || system.getId() == null || system.getX() == null || system.getY() == null) {
                continue;
            }
            List<Faction> owners = owners(system, date);
            boolean settled = owners.stream().anyMatch(faction -> !faction.is(FactionTag.ABANDONED));
            stars.add(new Star(system.getId(), name(system, date), system.getX(), system.getY(), colour(owners),
                  ownerNames(owners, year), settled));
        }
        AbstractLocation location = campaign.getPlayerForce().getForceDetachment().getCurrentLocation();
        PlanetarySystem here = location == null ? null : location.getCurrentSystem();
        JumpPath path = location == null ? null : location.getJumpPath();
        List<String> route = new ArrayList<>();
        if (path != null && !path.isEmpty()) {
            for (PlanetarySystem system : path.getSystems()) {
                route.add(system.getId());
            }
        }
        return new StarMap(date, stars, here == null ? null : here.getId(), route);
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
        boolean atSystem = here != null && Objects.equals(here.getId(), system.getId());
        if (atSystem) {
            jumps = 0;
        } else if (here != null) {
            distance = here.getDistanceTo(system);
            JumpPath path = campaign.calculateJumpPath(here, system);
            jumps = path == null || path.isEmpty() ? -1 : path.getJumps();
        }
        HPGRating hpg = system.getHPG(date);
        return new Detail(system.getId(), name(system, date), ownerNames(owners, date.getYear()), colour(owners),
              system.getPopulation(date), hpg == null ? HPGRating.X.toString() : hpg.toString(),
              system.getStar() == null ? "" : system.getStar().toString(),
              system.getRechargeTime(date, campaign.isUseCommandCircuit()), distance, jumps, atSystem);
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
        Color colour = owners.isEmpty() ? null : owners.get(0).getColor();
        return colour == null ? UNCLAIMED : colour.getRGB() & 0xFFFFFF;
    }

    private static String ownerNames(List<Faction> owners, int year) {
        return owners.stream().map(faction -> faction.getFullName(year)).filter(Objects::nonNull)
              .collect(Collectors.joining(" / "));
    }

    private static String name(PlanetarySystem system, LocalDate date) {
        String name = system.getPrintableName(date);
        return name == null || name.isBlank() ? system.getId() : name;
    }
}
