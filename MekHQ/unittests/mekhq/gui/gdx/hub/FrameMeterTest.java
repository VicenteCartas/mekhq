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

import org.junit.jupiter.api.Test;

class FrameMeterTest {
    @Test
    void summarizesTheWindowsFrames() {
        // 30 frames over half a second, each 2 ms on the CPU; the GPU timed 25 of them at 1.25 ms
        String text = FrameMeter.summary(30, .5f, .0213f, 30 * 2_000_000L, 25 * 1_250_000L, 25);

        assertEquals("60 FPS  \u00b7  worst 21.3 ms  \u00b7  CPU 2.0 ms  \u00b7  GPU 1.3 ms", text);
    }

    @Test
    void leavesTheGpuOutBeforeItHasTimedAFrame() {
        String text = FrameMeter.summary(10, .5f, .05f, 10 * 3_000_000L, 0, 0);

        assertEquals("20 FPS  \u00b7  worst 50.0 ms  \u00b7  CPU 3.0 ms", text);
    }
}
