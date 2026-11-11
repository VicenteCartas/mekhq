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

import java.util.concurrent.atomic.AtomicInteger;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import org.junit.jupiter.api.Test;

class TransmissionTubeTest {
    private static final float FRAME = 1 / 60f;

    private final TransmissionTube tube = new TransmissionTube(new TextureRegion(), Color.CYAN);
    private final Actor panel = new Actor();

    private void run(float seconds) {
        for (float time = 0; time < seconds; time += FRAME) {
            tube.act(FRAME);
        }
    }

    @Test
    void powerOnHidesThePanelUntilTheLineThenUnfoldsIt() {
        tube.powerOn(panel, .15f);

        assertEquals(0, panel.getColor().a);
        run(.15f + TransmissionTube.DOT + TransmissionTube.LINE * .5f);
        assertEquals(1, panel.getColor().a);
        assertTrue(panel.getScaleY() < .05f, "a line before it unfolds");
        run(TransmissionTube.LINE + TransmissionTube.UNFOLD + TransmissionTube.SETTLE);
        assertEquals(1, panel.getScaleX());
        assertEquals(1, panel.getScaleY());
        assertFalse(tube.opening());
    }

    @Test
    void finishShowsTheOpeningPanelAtOnce() {
        tube.powerOn(panel, .15f);
        run(.2f);

        tube.finish();

        assertEquals(1, panel.getScaleY());
        assertEquals(1, panel.getColor().a);
        assertFalse(tube.opening());
    }

    @Test
    void powerOffSqueezesThePanelAndLetsItGoOnce() {
        AtomicInteger gone = new AtomicInteger();

        tube.powerOff(panel, .1f, gone::incrementAndGet);
        run(.1f + TransmissionTube.SQUASH + TransmissionTube.COLLAPSE * .5f);
        assertTrue(panel.getScaleY() < .05f, "squeezed into a line");
        assertEquals(0, gone.get());
        run(TransmissionTube.COLLAPSE + TransmissionTube.AFTERGLOW);

        assertEquals(1, gone.get());
        assertEquals(0, panel.getColor().a);
    }

    @Test
    void cutShowsTheNextPanelFullSize() {
        panel.setScale(.5f);

        tube.cut(panel);
        assertTrue(tube.opening());
        run(TransmissionTube.CUT + .05f);

        assertEquals(1, panel.getScaleX());
        assertEquals(1, panel.getColor().a);
        assertFalse(tube.opening());
    }
}
