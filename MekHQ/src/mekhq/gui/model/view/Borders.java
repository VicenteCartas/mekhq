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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import megamek.common.universe.FactionTag;
import mekhq.campaign.events.PlanetarySystemsChangedEvent;
import mekhq.campaign.universe.Faction;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.territory.InfluenceMap;

/**
 * The inner sphere as it stood on a date, for the star map to show history by: every system in its owner's colour
 * then, every claim a realm laid to space, a settled system it held, and the territory those claims spread to. It
 * reads only the systems and their factions, so it is built away from the Swing thread, and never changes once built.
 */
public final class Borders {
    private static final String INDEPENDENT = "IND";

    /**
     * A realm the map colours space for.
     *
     * @param shortName its faction's short name
     * @param rgb       its colour, 0xRRGGBB
     */
    public record Realm(String shortName, int rgb) {
        public Realm {
            Objects.requireNonNull(shortName);
        }
    }

    /**
     * Numbers the realms as they are first seen, the same on every date, so one date's territory can turn into
     * another's. Any thread.
     */
    public static final class Realms {
        private final Map<String, Integer> ids = new HashMap<>();
        private final List<Realm> realms = new ArrayList<>();

        /** The number of {@code faction}'s realm, numbering it if it is new. */
        synchronized int id(Faction faction) {
            Integer known = ids.get(faction.getShortName());
            if (known != null) {
                return known;
            }
            int id = realms.size();
            ids.put(faction.getShortName(), id);
            realms.add(new Realm(faction.getShortName(), StarMap.rgb(faction)));
            return id;
        }

        /** Every realm numbered so far, by number. */
        public synchronized List<Realm> all() {
            return List.copyOf(realms);
        }
    }

    private final LocalDate date;
    private final List<StarMap.Star> stars;
    private final float[] claimX;
    private final float[] claimY;
    private final int[] claimRealm;
    private final boolean[] disputed;
    private final List<Realm> realms;
    private final float minX;
    private final float minY;
    private final float maxX;
    private final float maxY;
    private final InfluenceMap land;

    private Borders(LocalDate date, List<StarMap.Star> stars, List<float[]> claims, List<Realm> realms,
          float[] bounds) {
        this.date = date;
        this.stars = List.copyOf(stars);
        int count = claims.size();
        claimX = new float[count];
        claimY = new float[count];
        claimRealm = new int[count];
        disputed = new boolean[count];
        for (int i = 0; i < count; i++) {
            float[] claim = claims.get(i);
            claimX[i] = claim[0];
            claimY[i] = claim[1];
            claimRealm[i] = (int) claim[2];
            disputed[i] = claim[3] > 0;
        }
        this.realms = List.copyOf(realms);
        minX = bounds[0];
        minY = bounds[1];
        maxX = bounds[2];
        maxY = bounds[3];
        int[] realm = new int[count];
        for (int i = 0; i < count; i++) {
            realm[i] = claimRealm[i] + 1;
        }
        land = InfluenceMap.of(count, claimX, claimY, realm, disputed, minX, minY, maxX, maxY);
    }

    /**
     * Something that is watched for the systems changing, so borders read before can be read again: a new value each
     * time they change.
     */
    public static ViewModel<Long> universe() {
        return ViewModel.of("Universe", campaign -> System.nanoTime(),
              List.of(PlanetarySystemsChangedEvent.class));
    }

    /**
     * The inner sphere on {@code date}: the stars of {@code systems} and the claims of whoever held them, their realms
     * numbered by {@code numbering}. A system holds no claim when no one or only the abandoned hold it; independents
     * claim it only when no realm shares it. Any thread.
     */
    public static Borders of(Collection<PlanetarySystem> systems, Realms numbering, LocalDate date) {
        List<StarMap.Star> stars = StarMap.stars(systems, date);
        List<float[]> claims = new ArrayList<>();
        float[] bounds = { Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
        List<Faction> owners = new ArrayList<>();
        for (PlanetarySystem system : systems) {
            if (system == null || system.getX() == null || system.getY() == null) {
                continue;
            }
            float x = system.getX().floatValue();
            float y = system.getY().floatValue();
            bounds[0] = Math.min(bounds[0], x);
            bounds[1] = Math.min(bounds[1], y);
            bounds[2] = Math.max(bounds[2], x);
            bounds[3] = Math.max(bounds[3], y);
            owners.clear();
            Set<Faction> held = system.getFactionSet(date);
            if (held != null) {
                for (Faction faction : held) {
                    if (faction != null && !faction.is(FactionTag.ABANDONED)) {
                        owners.add(faction);
                    }
                }
            }
            if (owners.size() > 1) {
                owners.removeIf(faction -> INDEPENDENT.equals(faction.getShortName()));
            }
            float contested = owners.size() > 1 ? 1 : 0;
            for (Faction owner : owners) {
                claims.add(new float[] { x, y, numbering.id(owner), contested });
            }
        }
        if (bounds[0] > bounds[2]) {
            bounds = new float[4];
        }
        return new Borders(date, stars, claims, numbering.all(), bounds);
    }

    /** The date it shows. */
    public LocalDate date() {
        return date;
    }

    /** Every system with a position, as it stood on the date, in no particular order. */
    public List<StarMap.Star> stars() {
        return stars;
    }

    /** The number of claims. */
    public int claims() {
        return claimX.length;
    }

    /** Light years spinward of Terra of the system claim {@code i} is for. */
    public float claimX(int i) {
        return claimX[i];
    }

    /** Light years coreward of Terra of the system claim {@code i} is for. */
    public float claimY(int i) {
        return claimY[i];
    }

    /** The number of the realm laying claim {@code i}, an index in {@link #realms()}. */
    public int claimRealm(int i) {
        return claimRealm[i];
    }

    /** Whether others claim the same system as claim {@code i}. */
    public boolean disputed(int i) {
        return disputed[i];
    }

    /** Every realm numbered by the time it was read, by number; more than those claiming on the date. */
    public List<Realm> realms() {
        return realms;
    }

    /** The territory the claims spread to; its realms are numbered one past their index in {@link #realms()}. */
    public InfluenceMap land() {
        return land;
    }

    /** The westernmost of all the systems, claimed or not, in light years. */
    public float minX() {
        return minX;
    }

    /** The rimmost of all the systems. */
    public float minY() {
        return minY;
    }

    /** The easternmost of all the systems. */
    public float maxX() {
        return maxX;
    }

    /** The coremost of all the systems. */
    public float maxY() {
        return maxY;
    }
}
