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

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import com.badlogic.gdx.ApplicationListener;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.GUIPreferences;
import megamek.client.ui.clientGUI.boardview.gpu.GpuHost;
import megamek.client.ui.clientGUI.boardview.gpu.GpuSceneDialogs;
import megamek.client.ui.clientGUI.boardview.gpu.GpuStartWindow;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;
import megamek.client.ui.gdx.DisplayScale;
import megamek.logging.MMLogger;
import mekhq.MHQConstants;
import mekhq.MekHQ;
import mekhq.campaign.AbstractLocation;
import mekhq.campaign.Campaign;
import mekhq.campaign.JumpPath;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.Systems;
import mekhq.gui.BeginTransit;
import mekhq.gui.CampaignGUI;
import mekhq.gui.StartStarChart;
import mekhq.gui.enums.MHQTabType;
import mekhq.gui.model.view.Borders;
import mekhq.gui.model.view.CampaignSession;
import mekhq.gui.model.view.StarMap;
import mekhq.gui.model.view.SystemDossier;
import mekhq.gui.model.view.TopBar;
import mekhq.gui.model.view.Transmission;

/**
 * The campaign's command post in the native window (docs/libgdx-ui-port-plan.md), which the start screen's HQ
 * (Experimental) entry opens a loaded campaign in. Its rail and command palette open the campaign window, built hidden
 * as the hub opens, on the screen, person or unit asked for, and run the window's menu items with their dialogs over the
 * hub; the window's close box hides it again. The rail's Map shows the campaign's {@link StarMap star map} in the hub
 * itself, watched only while it is open, and the realms' borders on whatever date its timeline shows, read on a thread
 * of their own and read again if the systems change. Classic View or Esc leaves the hub for the campaign window, and closing
 * the hub quits MekHQ, asking to save first.
 */
public final class CampaignHub implements GpuHost.Scene, HubScreen.Hub {
    private static final MMLogger LOGGER = MMLogger.create(CampaignHub.class);
    static final Path TABLE = Path.of("data", "models", "hq", "holotable.glb");
    private static final String FACTIONS = "data/universe/factions";
    /** The star map also takes in the mercenaries' hub, as the start screen's does. */
    private static final String FOCUS = "Galatea";

    private final MekHQ app;
    private final Campaign campaign;
    private final CampaignSession session;
    private final HubScreen screen;
    /** The borders on the dates the star map shows, from the universe's systems. */
    private final Borders.Realms realms = new Borders.Realms();
    private final BorderHistory history = new BorderHistory(date -> Borders.of(
          List.copyOf(Systems.getInstance().getSystems().values()), realms, date));
    private final String title;
    private final AtomicBoolean leaving = new AtomicBoolean();
    private final GpuSceneDialogs dialogs = new GpuSceneDialogs(new GpuSceneDialogs.Host() {
        @Override
        public @Nullable JFrame frame() {
            CampaignGUI shown = gui;
            return shown != null && !shown.getFrame().isVisible() ? shown.getFrame() : null;
        }

        @Override
        public void inputBlocked(boolean blocked) {
            screen.blocked(blocked);
        }

        @Override
        public boolean closing() {
            return leaving.get();
        }
    });
    /** Swing thread: the campaign window, hidden while the player stays in the hub; null until the hub opens. */
    private @Nullable CampaignGUI gui;
    /** The channels of the transmissions the hub shows or queues, so they can fall back to windows as it ends. */
    private final Set<Transmission.Channel> channels = ConcurrentHashMap.newKeySet();
    /** Swing thread: shows the campaign's immersive dialogs over the hub while it is in front. */
    private final Transmission.Presenter presenter = this::present;
    /** The screens the campaign has, as the rail last heard them. */
    private volatile Set<MHQTabType> screens = EnumSet.allOf(MHQTabType.class);
    /** The star map's watch while the map is open. */
    private @Nullable CampaignSession.Watch mapWatch;

    private CampaignHub(MekHQ app, Campaign campaign, @Nullable StarChart chart, float scale) {
        this.app = app;
        this.campaign = campaign;
        title = getFormattedText("HqHub.windowTitle", campaign.getPlayerForce().getName());
        session = new CampaignSession(campaign);
        screen = new HubScreen(TABLE, chart, campaign.getPlayerForce().getName(), scale, this,
              history);
    }

    /**
     * EDT: shows {@code campaign}, just loaded over the start screen, in the command post in place of the start screen;
     * {@code anchor} is the start screen's hidden owner, disposed once the hidden campaign window keeps MekHQ running.
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
                CampaignHub hub = new CampaignHub(app, campaign, chart, scale);
                hub.dialogs.install();
                // The hub replaces the start screen in the same window
                GpuHost.show(hub);
                GpuStartWindow.uninstall(() -> { });
                app.activateCampaign(campaign, false);
                hub.gui = app.getCampaigngui();
                hub.gui.setCloseAction(hub::hideWindow);
                Transmission.register(hub.presenter);
                anchor.dispose();
                hub.session.watch(TopBar.model(), hub.screen::show);
                hub.session.watch(Borders.universe(), changed -> hub.history.invalidate());
                hub.session.watch(HubArea.screens(), shown -> {
                    hub.screens = shown;
                    hub.screen.screens(shown);
                });
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

    @Override
    public void go(PaletteEntry.Target target) {
        if (target instanceof PaletteEntry.Classic) {
            leave();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            CampaignGUI window = gui;
            if (leaving.get() || window == null || dialogs.blocked()) {
                return;
            }
            switch (target) {
                case PaletteEntry.Screen screenTarget -> {
                    window.setSelectedTab(screenTarget.tab());
                    showWindow(window);
                }
                case PaletteEntry.PersonTarget person -> {
                    window.focusOnPerson(person.id());
                    showWindow(window);
                }
                case PaletteEntry.UnitTarget unit -> {
                    window.focusOnUnit(unit.id());
                    showWindow(window);
                }
                case PaletteEntry.MenuItem menu -> {
                    // The item's dialogs open over the hub, owned by the hidden window
                    JMenuItem item = PaletteIndex.menuItem(window.getFrame().getJMenuBar(), menu);
                    if (item != null) {
                        item.doClick(0);
                    }
                }
                case PaletteEntry.Classic classic -> leave();
            }
        });
    }

    @Override
    public void palette() {
        session.dispatch(current -> {
            CampaignGUI window = gui;
            List<PaletteEntry> entries = PaletteIndex.build(current, screens,
                  window == null ? null : window.getFrame().getJMenuBar());
            GpuHost.post(() -> screen.palette(entries));
        });
    }

    @Override
    public void mapShown(boolean shown) {
        CampaignSession.Watch watched;
        synchronized (this) {
            watched = mapWatch;
            mapWatch = shown ? session.watch(StarMap.model(), screen::map) : null;
        }
        if (watched != null) {
            watched.close();
        }
    }

    @Override
    public void system(String id) {
        session.dispatch(current -> {
            StarMap.Detail read = StarMap.detail(current, id);
            if (read != null) {
                GpuHost.post(() -> screen.detail(read));
            }
        });
    }

    @Override
    public void travel(String id) {
        session.dispatch(current -> {
            if (leaving.get() || gui == null || dialogs.blocked()) {
                return;
            }
            JumpPath path = StarMap.plot(current, id);
            // The confirmation and any JumpShips unable to jump show over the hub
            if (path != null) {
                BeginTransit.begin(current, path, StarMap.assess(current, path));
            }
            system(id);
        });
    }

    @Override
    public void dossier(String id) {
        session.dispatch(current -> {
            SystemDossier read = SystemDossier.read(current, id);
            if (read != null) {
                GpuHost.post(() -> screen.dossier(read));
            }
        });
    }

    @Override
    public void cancelTrip() {
        session.dispatch(current -> {
            AbstractLocation location = current.getPlayerForce().getForceDetachment().getCurrentLocation();
            if (location != null && location.getJumpPath() != null) {
                location.setJumpPath(null);
            }
        });
    }

    @Override
    public void disposed() {
        dialogs.released();
    }

    /**
     * Swing thread: shows {@code transmission} over the hub, unless the hub is going, the campaign window is in front
     * of it or a Swing dialog holds it, where the dialog's window shows as it would over the campaign window.
     */
    private @Nullable Transmission.Handle present(Transmission transmission, Transmission.Channel channel) {
        CampaignGUI window = gui;
        if (leaving.get() || window == null || window.getFrame().isVisible() || dialogs.blocked()) {
            return null;
        }
        Transmission.Channel tracked = new Transmission.Channel() {
            @Override
            public void control(int index, int value) {
                channel.control(index, value);
            }

            @Override
            public void link(String href) {
                channel.link(href);
            }

            @Override
            public void answer(int choice, boolean responded) {
                channels.remove(this);
                channel.answer(choice, responded);
            }

            @Override
            public void fallBack() {
                channels.remove(this);
                channel.fallBack();
            }
        };
        channels.add(tracked);
        Transmission.Handle handle = screen.transmit(transmission, tracked);
        return new Transmission.Handle() {
            @Override
            public void update(List<Transmission.Control> controls) {
                handle.update(controls);
            }

            @Override
            public void withdraw() {
                channels.remove(tracked);
                handle.withdraw();
            }
        };
    }

    /** Swing thread: brings the campaign window up over the hub. */
    private static void showWindow(CampaignGUI window) {
        JFrame frame = window.getFrame();
        if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) {
            frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
        }
        frame.setVisible(true);
        frame.toFront();
    }

    /** Swing thread, from the campaign window's close box: back to the hub. */
    private void hideWindow() {
        CampaignGUI window = gui;
        if (window != null) {
            window.getFrame().setVisible(false);
        }
        GpuHost.focus();
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
    public void started(long windowHandle) {
        dialogs.started(windowHandle);
        SwingUtilities.invokeLater(dialogs::presented);
    }

    @Override
    public void focusGained() {
        if (dialogs.blocked()) {
            SwingUtilities.invokeLater(dialogs::frontModal);
        }
    }

    @Override
    public void closeRequested() {
        SwingUtilities.invokeLater(() -> {
            if (dialogs.blocked()) {
                dialogs.frontModal();
            } else if (!leaving.get()) {
                app.exit(true);
            }
        });
    }

    @Override
    public void ended(@Nullable Throwable failure) {
        Transmission.unregister(presenter);
        // A transmission the hub never answered shows as its dialog's window, so its caller stops waiting
        for (Transmission.Channel channel : List.copyOf(channels)) {
            channel.fallBack();
        }
        // Closes the top bar's watch too
        session.close();
        history.close();
        if (failure != null) {
            LOGGER.error(failure, "The command post failed; opening the campaign window");
        }
        SwingUtilities.invokeLater(() -> {
            dialogs.uninstall();
            CampaignGUI window = gui;
            if (window == null) {
                app.activateCampaign(campaign);
            } else {
                window.setCloseAction(null);
                showWindow(window);
            }
        });
    }
}
