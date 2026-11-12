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

import java.io.File;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import megamek.client.ui.gdx.UiTheme;
import megamek.common.Configuration;
import mekhq.gui.gdx.hub.HqStations.Station;

/**
 * A developer's look at the command post's room without a campaign: writes shots of the holotable and each station to
 * build/hq-room and exits. Run through the hqRoomDemo task.
 */
public final class HqRoomDemo {
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    /** Frames to settle at each pose, past the camera's move. */
    private static final int SETTLE = 70;

    private HqRoomDemo() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("HQ room demo");
        configuration.setWindowedMode(WIDTH, HEIGHT);
        configuration.setInitialVisible(false);
        configuration.setForegroundFPS(60);
        configuration.setBackBufferConfig(8, 8, 8, 8, 24, 8, 4);
        configuration.disableAudio(true);
        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 4, 3);
        new Lwjgl3Application(new ApplicationAdapter() {
            private final Station[] poses = { Station.TABLE, Station.OPERATIONS, Station.COMMS, Station.STATUS,
                                              Station.TABLE };
            private HoloTableView holo;
            private int frames;

            @Override
            public void create() {
                UiTheme theme = new UiTheme(Configuration.fontsDir());
                holo = new HoloTableView(CampaignHub.TABLE, null, () -> { }, () -> { });
                holo.create();
                holo.fonts(theme.font("hq-board-label", UiTheme.ROBOTO, 9),
                      theme.font("hq-board-figure", UiTheme.ROBOTO, 30));
                holo.boards(new String[] { "Date \u00b7 Location", "Funds \u00b7 Command", "Status" },
                      "14 March 3067", "Galatea", "12,450,000 C-bills", "Brokenactivation", "2 alerts",
                      List.of("Scenario due", "Loan overdue"), true);
                holo.incoming(true);
                holo.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            }

            @Override
            public void render() {
                holo.render(0, 0);
                int pose = frames / SETTLE;
                if (frames % SETTLE == SETTLE - 1) {
                    shot(pose + "-" + poses[pose].name().toLowerCase(Locale.ROOT));
                    if (pose + 1 >= poses.length) {
                        Gdx.app.exit();
                    } else {
                        holo.focus(poses[pose + 1]);
                    }
                }
                frames++;
            }

            @Override
            public void dispose() {
                holo.dispose();
            }
        }, configuration);
    }

    private static void shot(String name) {
        File out = new File("build/hq-room");
        out.mkdirs();
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
        PixmapIO.writePNG(Gdx.files.absolute(new File(out, name + ".png").getAbsolutePath()), pixmap, -1, true);
        pixmap.dispose();
    }
}
