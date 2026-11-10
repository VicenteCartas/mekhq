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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import mekhq.campaign.events.OptionsChangedEvent;
import mekhq.gui.enums.MHQTabType;
import mekhq.gui.model.view.ViewModel;

/**
 * An area of the campaign on the command post's nav rail (docs/libgdx-ui-port-plan.md), in rail order, with its hotkey
 * and the Swing tabs it opens until it is ported. An area of several tabs offers them in a flyout.
 */
enum HubArea {
    COMMAND("military-tech", MHQTabType.COMMAND_CENTER, MHQTabType.BRIEFING_ROOM, MHQTabType.STRAT_CON),
    MAP("map", MHQTabType.NAVIGATION),
    FORCES("account-tree", MHQTabType.TOE),
    ROSTER("group", MHQTabType.PERSONNEL),
    HANGAR("unit", MHQTabType.HANGAR, MHQTabType.MEK_LAB),
    LOGISTICS("build", MHQTabType.REPAIR_BAY, MHQTabType.WAREHOUSE, MHQTabType.INFIRMARY),
    FINANCES("balance", MHQTabType.FINANCES);

    /** The rail's icon, a {@code UiTheme} icon name. */
    final String icon;
    private final List<MHQTabType> tabs;

    HubArea(String icon, MHQTabType... tabs) {
        this.icon = icon;
        this.tabs = List.of(tabs);
    }

    /** The area's hotkey, 1 for the first area on the rail. */
    int hotkey() {
        return ordinal() + 1;
    }

    /** The area's tabs that the campaign has, of {@code available}, in the order the flyout lists them. */
    List<MHQTabType> tabs(Set<MHQTabType> available) {
        List<MHQTabType> present = new ArrayList<>();
        for (MHQTabType tab : tabs) {
            if (available.contains(tab)) {
                present.add(tab);
            }
        }
        return present;
    }

    /** The area holding {@code tab}. */
    static HubArea of(MHQTabType tab) {
        for (HubArea area : values()) {
            if (area.tabs.contains(tab)) {
                return area;
            }
        }
        throw new IllegalArgumentException("No area holds " + tab);
    }

    /** The tabs the campaign has: every one, but the Area of Operations only while StratCon is in use. */
    static ViewModel<Set<MHQTabType>> screens() {
        return ViewModel.of("HubScreens", campaign -> {
            Set<MHQTabType> tabs = EnumSet.allOf(MHQTabType.class);
            if (!campaign.getCampaignOptions().isUseStratCon()) {
                tabs.remove(MHQTabType.STRAT_CON);
            }
            return Set.copyOf(tabs);
        }, List.of(OptionsChangedEvent.class));
    }
}
