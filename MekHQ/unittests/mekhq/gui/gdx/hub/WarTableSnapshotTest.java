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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import mekhq.campaign.Campaign;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.digitalGM.stratCon.StratConCampaignState;
import mekhq.campaign.digitalGM.stratCon.StratConContractInitializer;
import mekhq.campaign.digitalGM.stratCon.StratConCoords;
import mekhq.campaign.digitalGM.stratCon.StratConTestData;
import mekhq.campaign.digitalGM.stratCon.StratConTrackState;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacility;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacilityFactory;
import mekhq.campaign.digitalGM.stratCon.sectorGeneration.LatitudeBand;
import mekhq.campaign.digitalGM.stratCon.sectorGeneration.PlanetProfile;
import mekhq.campaign.digitalGM.stratCon.sectorGeneration.SectorSpec;
import mekhq.campaign.mission.contract.AbstractContract;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.gui.gdx.hub.WarTableSnapshot.Hex;
import mekhq.gui.gdx.hub.WarTableSnapshot.Reserve;
import mekhq.gui.gdx.hub.WarTableSnapshot.Sector;
import mekhq.gui.gdx.hub.WarTableSnapshot.Token;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WarTableSnapshotTest {
    private static final LocalDate TODAY = LocalDate.of(3025, 6, 1);

    @BeforeAll
    static void loadStratConData() {
        StratConTestData.install();
    }

    private static StratConTrackState track() {
        StratConTrackState track = StratConContractInitializer.initializeTrackState(
              new SectorSpec(4, LatitudeBand.NORTH_TEMPERATE), PlanetProfile.neutral(25), new CampaignOptions(), true,
              0, 0);
        track.setDisplayableName("Kestrel");
        return track;
    }

    private static List<StratConCoords> land(StratConTrackState track) {
        List<StratConCoords> land = new ArrayList<>();
        for (int x = 0; x < track.getWidth(); x++) {
            for (int y = 0; y < track.getHeight(); y++) {
                StratConCoords coords = new StratConCoords(x, y);
                String terrain = track.getTerrainTile(coords);
                if (terrain != null && !terrain.isBlank() && !track.isOffTrack(coords)) {
                    land.add(coords);
                }
            }
        }
        return land;
    }

    @Test
    void sectorCopiesTheTrackAsThePlayerSeesIt() {
        StratConTrackState track = track();
        List<StratConCoords> land = land(track);
        assertFalse(land.isEmpty());
        StratConCoords scouted = land.get(0);
        track.setRevealedCoords(new HashSet<>(Set.of(scouted)));
        track.setAssignedCoordForces(Map.of(scouted, Set.of(3)));

        Sector sector = WarTableSnapshot.of(track, TODAY, id -> "Force " + id);

        assertEquals("Kestrel", sector.name());
        assertEquals(track.getWidth(), sector.width());
        assertEquals(track.getHeight(), sector.height());
        assertEquals(land.size(), sector.hexes().size());
        for (Hex hex : sector.hexes()) {
            boolean shouldBeScouted = hex.x() == scouted.getX() && hex.y() == scouted.getY();
            assertEquals(shouldBeScouted, hex.scouted(), "hex " + hex.x() + "," + hex.y());
        }
        assertEquals(1, sector.tokens().size());
        assertEquals("Force 3", sector.tokens().get(0).name());
        assertEquals(scouted.getX(), sector.tokens().get(0).x());
    }

    @Test
    void sectorTellsWhereForcesMayGoAndWhichMayLeave() {
        StratConTrackState track = track();
        List<StratConCoords> land = land(track);
        StratConCoords held = land.get(0);
        track.setAssignedCoordForces(Map.of(held, Set.of(3)));
        List<Reserve> reserve = List.of(new Reserve(8, "Reserve", null));

        Sector sector = WarTableSnapshot.of(track, TODAY, new WarTableSnapshot.Forces() {
            @Override
            public String name(int force) {
                return "Force " + force;
            }

            @Override
            public java.awt.image.BufferedImage icon(int force) {
                return null;
            }

            @Override
            public String hold(int force) {
                return "Fighting";
            }
        }, reserve);

        Token token = sector.tokens().get(0);
        assertEquals(3, token.force());
        assertEquals("Fighting", token.hold());
        assertEquals(reserve, sector.reserve());
        Hex taken = sector.hex(held.getX(), held.getY());
        assertFalse(taken.open(), "a hex a force holds takes no other");
        assertTrue(sector.hexes().stream().anyMatch(Hex::open), "some free land is open");
    }

    @Test
    void sectorLeavesOutFacilitiesThePlayerHasNotFound() {
        StratConTrackState track = track();
        List<StratConCoords> land = land(track);
        StratConFacility found = StratConFacilityFactory.getRandomHostileFacility();
        StratConFacility hidden = StratConFacilityFactory.getRandomHostileFacility();
        found.setVisible(true);
        hidden.setVisible(false);
        track.addFacility(land.get(0), found);
        track.addFacility(land.get(land.size() - 1), hidden);

        Sector sector = WarTableSnapshot.of(track, TODAY, id -> "");

        assertEquals(1, sector.sites().size());
        assertEquals(land.get(0).getX(), sector.sites().get(0).x());
        assertEquals(land.get(0).getY(), sector.sites().get(0).y());
        assertFalse(sector.sites().get(0).allied());
    }

    @Test
    void operationsPutTheOneAtTheChosenSystemFirstAndSkipContractsWithoutStratCon() {
        PlanetarySystem here = mock(PlanetarySystem.class);
        when(here.getId()).thenReturn("Here");
        PlanetarySystem there = mock(PlanetarySystem.class);
        when(there.getId()).thenReturn("There");
        AbstractContract atHere = contract("Garrison", here, track());
        AbstractContract atThere = contract("Raid", there, track());
        AbstractContract plain = mock(AbstractContract.class);
        when(plain.getName()).thenReturn("Plain");
        Campaign campaign = mock(Campaign.class);
        when(campaign.getActiveContracts(false)).thenReturn(List.of(atHere, plain, atThere));
        when(campaign.getCurrentSystem()).thenReturn(here);
        when(campaign.getLocalDate()).thenReturn(TODAY);

        List<WarTableSnapshot> chosen = WarTableSnapshot.operations(campaign, "There");
        List<WarTableSnapshot> local = WarTableSnapshot.operations(campaign, null);

        assertEquals(List.of("Raid", "Garrison"), chosen.stream().map(WarTableSnapshot::operation).toList());
        assertEquals(List.of("Garrison", "Raid"), local.stream().map(WarTableSnapshot::operation).toList());
        assertEquals(1, chosen.get(0).sectors().size());
        assertEquals(5, chosen.get(0).victoryPoints());
        assertEquals(atThere.getId(), chosen.get(0).contract());
        assertTrue(WarTableSnapshot.operations(mock(Campaign.class), null).isEmpty());
    }

    private static AbstractContract contract(String name, PlanetarySystem system, StratConTrackState track) {
        StratConCampaignState state = mock(StratConCampaignState.class);
        when(state.getTracks()).thenReturn(List.of(track));
        when(state.getVictoryPoints()).thenReturn(5);
        AbstractContract contract = mock(AbstractContract.class);
        when(contract.getName()).thenReturn(name);
        when(contract.getId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(contract.getTargetSystem()).thenReturn(system);
        when(contract.getStratConCampaignState()).thenReturn(state);
        return contract;
    }
}
