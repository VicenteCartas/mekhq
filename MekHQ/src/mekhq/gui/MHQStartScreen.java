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

import static mekhq.MHQConstants.LAUNCHER_NEW_PLAYER_QUICKSTART_PATH;
import static mekhq.utilities.MHQInternationalization.getFormattedText;
import static mekhq.utilities.MHQInternationalization.getText;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.swing.JFrame;
import javax.swing.SwingWorker;

import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.boardview.gpu.GpuStartWindow;
import megamek.client.ui.clientGUI.boardview.gpu.StartMenuItem;
import megamek.client.ui.clientGUI.boardview.gpu.StartMenuItem.Button;
import megamek.client.ui.clientGUI.boardview.gpu.StartMenuItem.Emphasis;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;
import megamek.common.preference.PreferenceManager;
import megamek.common.ui.AppIcons;
import megamek.logging.MMLogger;
import mekhq.MHQConstants;
import mekhq.MekHQ;
import mekhq.Utilities;
import mekhq.gui.dialog.DataLoadingDialog;
import mekhq.gui.dialog.MHQOptionsTreeDialog;
import mekhq.gui.dialog.NewPlayerQuickstartDialog;

/**
 * MekHQ's start screen, shown in the suite's native start window: the MekHQ art, the greeting and a menu to continue
 * the last campaign, start or load one, or start the new player quickstart. It ends once a campaign has loaded and the
 * campaign's own window takes over, and {@link MekHQ#restart} shows it again.
 */
public final class MHQStartScreen extends StartScreenContent {
    private static final MMLogger LOGGER = MMLogger.create(MHQStartScreen.class);

    /** Campaign saves: any .cpnx, .cpnx.gz or .xml file but the client settings. */
    public static final FilenameFilter SAVE_FILTER = (dir, name) -> {
        String lower = name.toLowerCase(Locale.ROOT);
        return (lower.endsWith(".cpnx") || lower.endsWith(".cpnx.gz") || lower.endsWith(".xml"))
              && !lower.equals(PreferenceManager.DEFAULT_CFG_FILE_NAME.toLowerCase(Locale.ROOT));
    };

    private static final String STAR_CHART_FACTIONS = "data/universe/factions";
    private static final String STAR_CHART_DATE = "3025-01-01";
    /** The mercenaries' hub. */
    private static final String STAR_CHART_FOCUS = "Galatea";
    private static @Nullable CompletableFuture<StarChart> starChart;

    private final MekHQ app;
    /** Hidden; owns the forms the menu opens, which the start window keeps above itself. */
    private final JFrame anchor;
    private final File quickstartFile = new File(LAUNCHER_NEW_PLAYER_QUICKSTART_PATH);
    private volatile @Nullable File lastSave;
    /** EDT: a campaign is loading, so the menu does nothing until it fails. */
    private boolean loading;

    private MHQStartScreen(MekHQ app) {
        this.app = app;
        anchor = new JFrame(MHQConstants.PROJECT_NAME);
        AppIcons.apply(anchor, "mekhq");
    }

    /** EDT: shows the start screen, with the greeting when {@code greet}, as MekHQ starts. */
    public static void show(MekHQ app, boolean greet) {
        chart();
        MHQStartScreen screen = new MHQStartScreen(app);
        GpuStartWindow.install(screen.anchor, screen);
        GpuStartWindow.open(greet);
        screen.findLastSave();
    }

    @Override
    public String title() {
        return MHQConstants.PROJECT_NAME;
    }

    @Override
    public @Nullable File art() {
        // The largest; the screen scales it down to the window
        return new File(app.getIconPackage().getStartupScreenImagesScreenImages().lastEntry().getValue());
    }

    @Override
    public @Nullable StarChart starChart() {
        try {
            return chart().get();
        } catch (ExecutionException e) {
            LOGGER.warn("The start screen has no star chart: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** The Inner Sphere in 3025 around Galatea, read once on a thread of its own as the start screen first shows. */
    private static synchronized CompletableFuture<StarChart> chart() {
        if (starChart == null) {
            starChart = CompletableFuture.supplyAsync(() -> StartStarChart.read(
                  new File(MHQConstants.PLANETARY_SYSTEM_DIRECTORY_PATH, "canon_systems.zip"),
                  new File(STAR_CHART_FACTIONS), STAR_CHART_DATE, STAR_CHART_FOCUS));
        }
        return starChart;
    }

    @Override
    public @Nullable BufferedImage logo() {
        // The art carries MekHQ's logo
        return null;
    }

    @Override
    public String greetingOverline() {
        return getFormattedText("StartScreen.link", MHQConstants.VERSION);
    }

    @Override
    public String greeting() {
        return getText("StartScreen.welcome");
    }

    @Override
    public List<String> tips() {
        return List.of();
    }

    @Override
    public String version() {
        return getFormattedText("StartScreen.version", MHQConstants.VERSION);
    }

    @Override
    public boolean tipAtTop() {
        return true;
    }

    @Override
    public Ambience ambience() {
        return Ambience.COMMAND;
    }

    @Override
    public List<StartMenuItem> menu() {
        List<StartMenuItem> menu = new ArrayList<>();
        File save = lastSave;
        if (save != null) {
            menu.add(new Button(getText("StartScreen.continue"), saveTitle(save.getName()) + "  \u00b7  "
                  + ago(save.lastModified()), Emphasis.PRIMARY, () -> startCampaign(save)));
        }
        menu.add(new Button(getText("btnNewCampaign.text"), save == null ? Emphasis.PRIMARY : Emphasis.SECONDARY,
              () -> startCampaign(null)));
        menu.add(new Button(getText("btnLoadCampaign.text"), Emphasis.SECONDARY, this::loadCampaign));
        if (quickstartFile.isFile()) {
            menu.add(new Button(getText("StartScreen.quickstart"), Emphasis.SECONDARY, this::quickstart));
        }
        return menu;
    }

    @Override
    public void settings() {
        new MHQOptionsTreeDialog(anchor).setVisible(true);
    }

    @Override
    public void quit() {
        System.exit(0);
    }

    /** A save's file name without its extension, as Continue names it. */
    static String saveTitle(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : List.of(".cpnx.gz", ".cpnx", ".xml")) {
            if (lower.endsWith(extension)) {
                return fileName.substring(0, fileName.length() - extension.length());
            }
        }
        return fileName;
    }

    /** Searches for the newest save off the EDT, as a large campaigns directory takes a while, then offers it. */
    private void findLastSave() {
        String directory = MekHQ.getCampaignsDirectory().getValue();
        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() {
                return Utilities.lastFileModified(directory, SAVE_FILTER);
            }

            @Override
            protected void done() {
                try {
                    lastSave = get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    LOGGER.error(ex, "Unable to find the last campaign save in {}", directory);
                }
                if (lastSave != null) {
                    GpuStartWindow.menuChanged();
                }
            }
        }.execute();
    }

    private void loadCampaign() {
        if (!loading) {
            FileDialogs.openCampaign(anchor).ifPresent(this::startCampaign);
        }
    }

    private void quickstart() {
        if (!loading && !new NewPlayerQuickstartDialog(anchor).wasCanceled()) {
            startCampaign(quickstartFile);
        }
    }

    /**
     * EDT: loads the campaign in {@code file}, or starts a new one when null, over the start screen, which then gives way
     * to the campaign's window; the screen stays when loading fails.
     */
    private void startCampaign(@Nullable File file) {
        if (loading) {
            return;
        }
        loading = true;
        new DataLoadingDialog(anchor, app, file, false, campaign -> {
            if (campaign == null) {
                loading = false;
                return;
            }
            GpuStartWindow.uninstall(() -> {
                anchor.dispose();
                app.activateCampaign(campaign);
            });
        }).setVisible(true);
    }
}
