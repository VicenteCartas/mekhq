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

import megamek.client.ui.gdx.UiSignalFeed;
import mekhq.gui.model.view.Transmission;
import org.junit.jupiter.api.Test;

class TransmissionViewTest {
    @Test
    void feedTakesTheDialogsSignal() {
        Transmission.Signal degraded = new Transmission.Signal(2, .22f, 2, .3f, 42, .44f, 5, 2, 5, 20, 8, 32, .8f);

        UiSignalFeed.Signal feed = TransmissionView.signal(degraded);

        assertEquals(new UiSignalFeed.Signal(.22f, 2, .3f, 42 / 400f, .44f, .2f, 2, 5, 20, 8, 32, .8f), feed);
    }

    @Test
    void feedKeepsItsNumbersWorkable() {
        Transmission.Signal odd = new Transmission.Signal(0, 0, 0, 0, 900, 0, 0, 0, 0, 0, 6, 3, 0);

        UiSignalFeed.Signal feed = TransmissionView.signal(odd);

        assertEquals(2, feed.scanlineGap());
        assertEquals(1, feed.noiseDensity());
        assertEquals(0, feed.glitchChance());
        assertEquals(1, feed.glitchStepsMin());
        assertEquals(1, feed.glitchStepsMax());
        assertEquals(6, feed.glitchHeightMax());
    }
}
