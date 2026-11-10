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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import mekhq.gui.enums.MHQTabType;

/**
 * One thing the command palette can find: its words and where it leads. Immutable, so the palette reads it on the GPU
 * thread; the command post carries out its {@link Target} on the Swing thread.
 *
 * @param kind   what it is, which also ranks equally good matches
 * @param label  what the palette shows and matches first
 * @param detail the smaller words beside it, such as a person's role, which also match
 * @param target where it leads
 */
record PaletteEntry(Kind kind, String label, String detail, Target target) {
    /** What an entry is, in the order equally good matches list. */
    enum Kind {
        SCREEN,
        ACTION,
        MENU,
        PERSON,
        UNIT
    }

    /** Where an entry leads. */
    sealed interface Target {
    }

    /** Opens the campaign window on {@code tab}. */
    record Screen(MHQTabType tab) implements Target {
        Screen {
            Objects.requireNonNull(tab);
        }
    }

    /** Opens the personnel tab on the person {@code id}. */
    record PersonTarget(UUID id) implements Target {
        PersonTarget {
            Objects.requireNonNull(id);
        }
    }

    /** Opens the hangar on the unit {@code id}. */
    record UnitTarget(UUID id) implements Target {
        UnitTarget {
            Objects.requireNonNull(id);
        }
    }

    /**
     * Runs the campaign window's menu item at {@code path}: the menu's index on the bar, then the item's in each menu
     * down to it; {@code label} checks it is still the same item.
     */
    record MenuItem(List<Integer> path, String label) implements Target {
        MenuItem {
            path = List.copyOf(path);
            Objects.requireNonNull(label);
        }
    }

    /** Leaves the command post for the classic campaign window. */
    record Classic() implements Target {
    }

    PaletteEntry {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(label);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(target);
    }
}
