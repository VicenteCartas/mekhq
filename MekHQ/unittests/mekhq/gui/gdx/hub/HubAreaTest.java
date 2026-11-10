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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import mekhq.campaign.Campaign;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.events.NewDayEvent;
import mekhq.campaign.events.OptionsChangedEvent;
import mekhq.gui.enums.MHQTabType;
import mekhq.gui.model.view.ViewModel;
import org.junit.jupiter.api.Test;

class HubAreaTest {
    @Test
    void everyTabIsInOneArea() {
        for (MHQTabType tab : MHQTabType.values()) {
            long holders = EnumSet.allOf(HubArea.class).stream()
                  .filter(area -> area.tabs(EnumSet.allOf(MHQTabType.class)).contains(tab))
                  .count();
            assertEquals(1, holders, tab.name());
            assertTrue(HubArea.of(tab).tabs(EnumSet.allOf(MHQTabType.class)).contains(tab));
        }
    }

    @Test
    void hotkeysRunOneToSevenDownTheRail() {
        HubArea[] areas = HubArea.values();
        assertEquals(7, areas.length);
        for (int index = 0; index < areas.length; index++) {
            assertEquals(index + 1, areas[index].hotkey());
        }
    }

    @Test
    void tabsKeepOnlyTheAvailableInOrder() {
        Set<MHQTabType> available = EnumSet.of(MHQTabType.INFIRMARY, MHQTabType.REPAIR_BAY);
        assertEquals(List.of(MHQTabType.REPAIR_BAY, MHQTabType.INFIRMARY), HubArea.LOGISTICS.tabs(available));
        assertTrue(HubArea.FINANCES.tabs(available).isEmpty());
    }

    @Test
    void areaOfOperationsOnlyWithStratCon() {
        ViewModel<Set<MHQTabType>> screens = HubArea.screens();
        assertFalse(screens.build(campaign(false)).contains(MHQTabType.STRAT_CON));
        Set<MHQTabType> withStratCon = screens.build(campaign(true));
        assertEquals(EnumSet.allOf(MHQTabType.class), withStratCon);
    }

    @Test
    void screensFollowTheCampaignOptions() {
        ViewModel<Set<MHQTabType>> screens = HubArea.screens();
        Campaign campaign = campaign(true);
        assertTrue(screens.isAffectedBy(new OptionsChangedEvent(campaign)));
        assertFalse(screens.isAffectedBy(new NewDayEvent(campaign)));
    }

    private static Campaign campaign(boolean stratCon) {
        Campaign campaign = mock(Campaign.class);
        CampaignOptions options = mock(CampaignOptions.class);
        when(campaign.getCampaignOptions()).thenReturn(options);
        when(options.isUseStratCon()).thenReturn(stratCon);
        return campaign;
    }
}
