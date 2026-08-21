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
package mekhq.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.SwingUtilities;

import mekhq.gui.baseComponents.FramedCommandButton;
import org.junit.jupiter.api.Test;

class MapTabNavigationButtonTest {
    @Test
    void navigationFactoryAppliesSharedFrameStyleAndMetadata() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FramedCommandButton button = MapTab.createNavigationButton("Center on Fleet", "Center the map");

            assertEquals("Center on Fleet", button.getText());
            assertEquals("Center the map", button.getToolTipText());
            assertEquals("Center on Fleet", button.getAccessibleContext().getAccessibleName());
            assertEquals("Center the map", button.getAccessibleContext().getAccessibleDescription());
            assertTrue(button.isFocusable());
            assertTrue(button.isRolloverEnabled());
            assertFalse(button.isContentAreaFilled());
            assertFalse(button.isBorderPainted());
            assertFalse(button.isFocusPainted());
            assertTrue(button.getMargin().left > button.getMargin().top);
        });
    }
}
