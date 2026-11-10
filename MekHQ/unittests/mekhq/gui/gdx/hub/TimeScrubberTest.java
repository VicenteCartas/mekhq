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

import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;

import mekhq.campaign.universe.eras.Era;
import org.junit.jupiter.api.Test;

class TimeScrubberTest {
    @Test
    void erasBecomeBandsClippedToTheTimeline() {
        TreeMap<LocalDate, Era> eras = new TreeMap<>();
        eras.put(LocalDate.of(2004, 12, 31), era("Early Spaceflight", LocalDate.of(2004, 12, 31)));
        eras.put(LocalDate.of(2570, 12, 31), era("Age of War", LocalDate.of(2570, 12, 31)));
        eras.put(LocalDate.of(2780, 12, 31), era("Star League", LocalDate.of(2780, 12, 31)));
        eras.put(LocalDate.ofYearDay(9999, 1), era("ilClan", LocalDate.ofYearDay(9999, 1)));

        List<TimeScrubber.Band> bands = TimeScrubber.bands(eras, 2150, 3152);

        assertEquals(List.of(new TimeScrubber.Band(2150, 2570, "Age of War"),
              new TimeScrubber.Band(2571, 2780, "Star League"),
              new TimeScrubber.Band(2781, 3152, "ilClan")), bands);
    }

    private static Era era(String name, LocalDate end) {
        Era era = new Era();
        era.setName(name);
        era.setEnd(end);
        return era;
    }
}
