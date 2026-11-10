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
package mekhq.campaign.universe.territory;

/**
 * A cell of the territory grid: flat-topped hexes {@link TerritoryGrid#HEX_SIZE} light years across, odd columns
 * sitting half a cell off the even ones.
 */
public record TerritoryHex(int column, int row) {
    /**
     * Spreads the cells over the hash: a record's own hash, 31 times the column plus the row, gives a grid's many
     * thousand cells only a few thousand values, packed together, which slows the maps keyed by them to a crawl.
     */
    @Override
    public int hashCode() {
        int hash = column * 0x9E3779B1 + row * 0x85EBCA77;
        return hash ^ (hash >>> 15);
    }
}
