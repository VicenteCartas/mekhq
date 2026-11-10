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

import static mekhq.utilities.MHQInternationalization.getFormattedText;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.badlogic.gdx.ApplicationListener;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.GUIPreferences;
import megamek.client.ui.clientGUI.boardview.gpu.GpuHost;
import megamek.client.ui.clientGUI.boardview.gpu.GpuStartWindow;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;
import megamek.client.ui.gdx.DisplayScale;
import megamek.logging.MMLogger;
import mekhq.MHQConstants;
import mekhq.MekHQ;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.gui.StartStarChart;
import mekhq.gui.model.view.CampaignSession;
import mekhq.gui.model.view.TopBar;

/**
 * The campaign's command post in the native window (docs/libgdx-ui-port-plan.md), which the start screen's HQ
 * (Experimental) entry opens a loaded campaign in. It only shows the campaign for now: Classic View or Esc hands the
 * campaign to its Swing window, and closing the window quits MekHQ.
 */
public final class CampaignHub implements GpuHost.Scene {
    private static final MMLogger LOGGER = MMLogger.create(CampaignHub.class);
    static final Path TABLE = Path.of("data", "models", "hq", "holotable.glb");
    private static final String FACTIONS = "data/universe/factions";
    /** The star map also takes in the mercenaries' hub, as the start screen's does. */
    private static final String FOCUS = "Galatea";

    private final MekHQ app;
    private final Campaign campaign;
    /** The start screen's hidden owner; it keeps Swing, and so MekHQ, running while only the native window shows. */
    private final JFrame anchor;
    private final CampaignSession session;
    private final HubScreen screen;
    private final String title;
    private final AtomicBoolean leaving = new AtomicBoolean();

    private CampaignHub(MekHQ app, Campaign campaign, JFrame anchor, @Nullable StarChart chart, float scale) {
        this.app = app;
        this.campaign = campaign;
        this.anchor = anchor;
        title = getFormattedText("HqHub.windowTitle", campaign.getPlayerForce().getName());
        session = new CampaignSession(campaign);
        screen = new HubScreen(TABLE, chart, campaign.getPlayerForce().getName(), scale, this::leave);
    }

    /**
     * EDT: shows {@code campaign}, just loaded over the start screen, in the command post in place of the start screen;
     * {@code anchor} is the start screen's hidden owner, disposed as the campaign window takes over.
     */
    public static void open(MekHQ app, Campaign campaign, JFrame anchor) {
        String date = campaign.getLocalDate().toString();
        String system = currentSystem(campaign);
        float scale = DisplayScale.read(GUIPreferences.getInstance().getGUIScale(), contentScale());
        CompletableFuture.supplyAsync(() -> chart(date, system)).whenComplete((chart, failure) -> {
            if (failure != null) {
                LOGGER.warn("The command post has no star chart: {}", failure.getMessage());
            }
            SwingUtilities.invokeLater(() -> {
                CampaignHub hub = new CampaignHub(app, campaign, anchor, chart, scale);
                // The hub replaces the start screen in the same window, which then stays open only for the hub
                GpuHost.show(hub);
                GpuStartWindow.uninstall(() -> { });
                hub.session.watch(TopBar.model(), hub.screen::show);
            });
        });
    }

    private static @Nullable String currentSystem(Campaign campaign) {
        AbstractLocation location = campaign.getPlayerForce().getForceDetachment().getCurrentLocation();
        PlanetarySystem system = location == null ? null : location.getCurrentSystem();
        return system == null ? null : system.getId();
    }

    private static @Nullable StarChart chart(String date, @Nullable String system) {
        String[] focus = system == null ? new String[] { FOCUS } : new String[] { system, FOCUS };
        return StartStarChart.read(new File(MHQConstants.PLANETARY_SYSTEM_DIRECTORY_PATH, "canon_systems.zip"),
              new File(FACTIONS), date, focus);
    }

    /** The main screen's content scale, which the GUI scale multiplies, as the native window's screens read it. */
    private static float contentScale() {
        if (GraphicsEnvironment.isHeadless()) {
            return 1;
        }
        return (float) GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
              .getDefaultConfiguration().getDefaultTransform().getScaleX();
    }

    /** Any thread: leaves the command post for the campaign's Swing window. */
    private void leave() {
        if (leaving.compareAndSet(false, true)) {
            GpuHost.end(this);
        }
    }

    @Override
    public ApplicationListener listener() {
        return screen;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public void closeRequested() {
        // The hub changes nothing, so there is nothing to save
        SwingUtilities.invokeLater(() -> app.exit(false));
    }

    @Override
    public void ended(@Nullable Throwable failure) {
        // Closes the top bar's watch too
        session.close();
        if (failure != null) {
            LOGGER.error(failure, "The command post failed; opening the campaign window");
        }
        SwingUtilities.invokeLater(() -> {
            anchor.dispose();
            app.activateCampaign(campaign);
        });
    }
}
