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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import mekhq.campaign.universe.Factions;
import mekhq.campaign.universe.PlanetarySystem;
import mekhq.campaign.universe.Systems;
import mekhq.gui.model.view.Borders;
import mekhq.gui.model.view.MapLens;
import mekhq.gui.model.view.StarMap;

/**
 * A developer's look at the command post's star map without a campaign: renders the hub with the map open over the
 * shipped systems in a hidden window and writes each shot asked for to a PNG. Run through the starMapSnapshots task;
 * each argument is a shot, {@code name:year:x:y:height[:tilt[:turn[:layers]]]}, year 0 for the campaign's own;
 * the layers a lens's key and net for the HPG network, joined by +.
 */
public final class StarMapSnapshots {
    private static final LocalDate TODAY = LocalDate.of(3025, 6, 1);
    private static final String HOME = "Galatea";
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    /** Frames to let the morph, the chrome and the labels settle once a shot's date is charted. */
    private static final int SETTLE = 70;
    private static final int TIMEOUT = 1800;

    private record Shot(String name, int year, double x, double y, float height, float tilt, float turn,
          String layers) {
        Shot(String name, int year, double x, double y, float height, float tilt, float turn) {
            this(name, year, x, y, height, tilt, turn, "");
        }

        static Shot parse(String spec) {
            String[] part = spec.split(":");
            return new Shot(part[0], Integer.parseInt(part[1]), Double.parseDouble(part[2]),
                  Double.parseDouble(part[3]), Float.parseFloat(part[4]),
                  part.length > 5 ? Float.parseFloat(part[5]) : 90, part.length > 6 ? Float.parseFloat(part[6]) : 0,
                  part.length > 7 ? part[7] : "");
        }

        /** The lens named among the layers, by its key, or null. */
        MapLens lens() {
            for (String layer : layers.split("\\+")) {
                for (MapLens lens : MapLens.values()) {
                    if (lens.key().equals(layer)) {
                        return lens;
                    }
                }
            }
            return null;
        }
    }

    private StarMapSnapshots() {
    }

    public static void main(String[] args) throws Exception {
        File out = new File(System.getProperty("mekhq.snapshots", "build/star-map-snapshots"));
        out.mkdirs();
        List<Shot> shots = new ArrayList<>();
        for (String arg : args) {
            shots.add(Shot.parse(arg));
        }
        if (shots.isEmpty()) {
            shots.add(new Shot("overview", 0, 0, 0, 1800, 90, 0));
            shots.add(new Shot("region", 0, 0, 0, 650, 90, 0));
            shots.add(new Shot("close", 0, 0, 0, 120, 90, 0));
        }
        Factions.setInstance(Factions.loadDefault(false));
        Systems.setInstance(Systems.loadDefault());
        List<PlanetarySystem> systems = List.copyOf(Systems.getInstance().getSystems().values());
        Borders.Realms realms = new Borders.Realms();
        BorderHistory history = new BorderHistory(date -> Borders.of(systems, realms, date));
        Borders today = Borders.of(systems, realms, TODAY);
        String home = today.stars().stream().filter(star -> star.name().equals(HOME)).map(StarMap.Star::id)
              .findFirst().orElse(null);
        Map<String, Integer> marks = new HashMap<>();
        for (StarMap.Star star : today.stars()) {
            switch (star.name()) {
                case "Tharkad" -> marks.put(star.id(), StarMap.MISSION);
                case "Luthien" -> marks.put(star.id(), StarMap.MISSION | StarMap.SCENARIO);
                case HOME -> marks.put(star.id(), StarMap.BASE);
                default -> {
                }
            }
        }
        StarMap campaign = new StarMap(TODAY, today.stars(), home, List.of(), null, marks);

        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Star map snapshots");
        configuration.setWindowedMode(WIDTH, HEIGHT);
        configuration.setInitialVisible(false);
        configuration.setResizable(false);
        configuration.useVsync(false);
        configuration.setForegroundFPS(60);
        configuration.setIdleFPS(60);
        configuration.setBackBufferConfig(8, 8, 8, 8, 24, 8, 0);
        configuration.disableAudio(true);
        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 4, 3);
        new Lwjgl3Application(new ApplicationAdapter() {
            private HubScreen screen;
            private StarMapView view;
            private int shot;
            private int frames;
            private int settled;

            @Override
            public void create() {
                screen = new HubScreen(CampaignHub.TABLE, null, "Snapshot", 1, new Idle(), history);
                screen.create();
                screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                screen.map(campaign);
                call("showMap", new Class<?>[] { boolean.class }, true);
                call("skip", new Class<?>[0]);
                view = (StarMapView) field("map");
            }

            @Override
            public void render() {
                if (shot >= shots.size()) {
                    Gdx.app.exit();
                    return;
                }
                Shot wanted = shots.get(shot);
                if (frames == 0) {
                    TimeScrubber scrubber = (TimeScrubber) field("scrubber");
                    scrubber.set(wanted.year() == 0 ? TODAY.getYear() : wanted.year(), false);
                    call("era", new Class<?>[0]);
                    view.lens(wanted.lens());
                    view.network(List.of(wanted.layers().split("\\+")).contains("net"));
                    view.reach(List.of(wanted.layers().split("\\+")).contains("reach"));
                    view.admin(List.of(wanted.layers().split("\\+")).contains("admin"));
                    view.empty(!List.of(wanted.layers().split("\\+")).contains("noempty"));
                    call("rebuildLenses", new Class<?>[0]);
                }
                view.look(wanted.x(), wanted.y(), wanted.height(), wanted.tilt(), wanted.turn());
                if (List.of(wanted.layers().split("\\+")).contains("ruler")) {
                    view.measure(-20, 10, 160, 120);
                }
                if (List.of(wanted.layers().split("\\+")).contains("menu") && menuShot != shot
                      && view.system("Tharkad") != null) {
                    menuShot = shot;
                    StarMap.Star tharkad = view.system("Tharkad");
                    view.measure(-20, 10, 160, 120);
                    call("openMapMenu", new Class<?>[] { StarMap.Star.class, double.class, double.class, int.class,
                          int.class }, tharkad, tharkad.x(), tharkad.y(), 700, 400);
                }
                if (List.of(wanted.layers().split("\\+")).contains("spot")) {
                    view.point(Gdx.graphics.getWidth() * 2 / 5, Gdx.graphics.getHeight() / 2);
                }
                screen.render();
                frames++;
                LocalDate shown = view.shownDate();
                int year = wanted.year() == 0 ? TODAY.getYear() : wanted.year();
                if (shown != null && shown.getYear() == year) {
                    settled++;
                }
                if (settled >= SETTLE || frames >= TIMEOUT) {
                    capture(new File(out, wanted.name() + ".png"));
                    System.out.println("Snapshot " + wanted.name() + (settled >= SETTLE ? "" : " (timed out)"));
                    shot++;
                    frames = 0;
                    settled = 0;
                }
            }

            private Object field(String name) {
                try {
                    Field found = HubScreen.class.getDeclaredField(name);
                    found.setAccessible(true);
                    return found.get(screen);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            }

            private int menuShot = -1;

            private void call(String name, Class<?>[] types, Object... values) {
                try {
                    Method found = HubScreen.class.getDeclaredMethod(name, types);
                    found.setAccessible(true);
                    found.invoke(screen, values);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException(ex);
                }
            }

            @Override
            public void dispose() {
                screen.dispose();
                history.close();
            }
        }, configuration);
        System.exit(0);
    }

    private static void capture(File file) {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
        for (int i = 3; i < pixels.length; i += 4) {
            pixels[i] = (byte) 255;
        }
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
        PixmapIO.writePNG(new FileHandle(file), pixmap);
        pixmap.dispose();
    }

    /** The hub's requests, which the snapshots ignore. */
    private static final class Idle implements HubScreen.Hub {
        @Override
        public void go(PaletteEntry.Target target) {
        }

        @Override
        public void palette() {
        }

        @Override
        public void mapShown(boolean shown) {
        }

        @Override
        public void system(String id) {
        }

        @Override
        public void travel(String id) {
        }

        @Override
        public void cancelTrip() {
        }

        @Override
        public void disposed() {
        }
    }
}
