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

import static mekhq.utilities.MHQInternationalization.getText;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import jakarta.annotation.Nullable;
import megamek.common.units.Entity;
import megamek.common.units.UnitType;
import mekhq.campaign.Campaign;
import mekhq.campaign.personnel.Person;
import mekhq.campaign.unit.Unit;
import mekhq.gui.enums.MHQTabType;

/**
 * Builds what the command palette searches, on the Swing thread: the campaign's screens, leaving for the classic
 * window, every item of the campaign window's menu bar, the active personnel and the units.
 */
final class PaletteIndex {
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final String CRUMB = " \u203A ";

    private PaletteIndex() {
    }

    /** Swing thread: the entries of {@code campaign}, with its {@code screens} and the window's {@code menus}. */
    static List<PaletteEntry> build(Campaign campaign, Set<MHQTabType> screens, @Nullable JMenuBar menus) {
        List<PaletteEntry> entries = new ArrayList<>();
        for (HubArea area : HubArea.values()) {
            String areaName = getText("HqHub.area." + area.name());
            for (MHQTabType tab : area.tabs(screens)) {
                entries.add(new PaletteEntry(PaletteEntry.Kind.SCREEN, tab.toString(), areaName,
                      new PaletteEntry.Screen(tab)));
            }
        }
        entries.add(new PaletteEntry(PaletteEntry.Kind.ACTION, getText("HqHub.classic"),
              getText("HqHub.palette.classic"), new PaletteEntry.Classic()));
        if (menus != null) {
            for (int index = 0; index < menus.getMenuCount(); index++) {
                JMenu menu = menus.getMenu(index);
                if (menu != null && menu.isVisible()) {
                    menuItems(menu, List.of(index), plain(menu.getText()), entries);
                }
            }
        }
        for (Person person : campaign.getPlayerForce().getHumanResources().getActivePersonnel(false, false)) {
            entries.add(new PaletteEntry(PaletteEntry.Kind.PERSON, person.getFullName(),
                  person.getPrimaryRoleDesc(), new PaletteEntry.PersonTarget(person.getId())));
        }
        for (Unit unit : campaign.getUnits()) {
            Entity entity = unit.getEntity();
            String type = entity == null ? "" : UnitType.getTypeDisplayableName(entity.getUnitType());
            entries.add(new PaletteEntry(PaletteEntry.Kind.UNIT, unit.getName(), type,
                  new PaletteEntry.UnitTarget(unit.getId())));
        }
        return List.copyOf(entries);
    }

    private static void menuItems(JMenu menu, List<Integer> path, String crumbs, List<PaletteEntry> entries) {
        Component[] items = menu.getMenuComponents();
        for (int index = 0; index < items.length; index++) {
            if (!(items[index] instanceof JMenuItem item) || !item.isVisible()) {
                continue;
            }
            String text = plain(item.getText());
            if (text.isBlank()) {
                continue;
            }
            List<Integer> itemPath = new ArrayList<>(path);
            itemPath.add(index);
            if (item instanceof JMenu submenu) {
                menuItems(submenu, itemPath, crumbs + CRUMB + text, entries);
            } else if (item.isEnabled()) {
                entries.add(new PaletteEntry(PaletteEntry.Kind.MENU, text, crumbs,
                      new PaletteEntry.MenuItem(itemPath, text)));
            }
        }
    }

    /**
     * Swing thread: the menu item {@code target} names on {@code menus}, when it is still there, with the same words,
     * and enabled; null otherwise.
     */
    static @Nullable JMenuItem menuItem(JMenuBar menus, PaletteEntry.MenuItem target) {
        List<Integer> path = target.path();
        if (path.isEmpty() || path.get(0) >= menus.getMenuCount()) {
            return null;
        }
        JMenuItem item = menus.getMenu(path.get(0));
        for (int depth = 1; depth < path.size(); depth++) {
            if (!(item instanceof JMenu menu) || path.get(depth) >= menu.getMenuComponentCount()
                  || !(menu.getMenuComponent(path.get(depth)) instanceof JMenuItem next)) {
                return null;
            }
            item = next;
        }
        return item != null && !(item instanceof JMenu) && item.isEnabled() && plain(item.getText()).equals(
              target.label()) ? item : null;
    }

    /** A menu's text without the HTML some items wrap it in. */
    static String plain(@Nullable String text) {
        return text == null ? "" : TAGS.matcher(text).replaceAll("").trim();
    }
}
