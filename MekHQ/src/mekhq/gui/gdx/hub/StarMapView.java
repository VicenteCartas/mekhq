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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import jakarta.annotation.Nullable;
import mekhq.MHQConstants;
import mekhq.campaign.universe.territory.InfluenceMap;
import mekhq.gui.model.view.Borders;
import mekhq.gui.model.view.MapLens;
import mekhq.gui.model.view.StarMap;

/**
 * The campaign's star map, full screen: the systems laid out on the galactic plane (coreward away from the camera),
 * glowing in their owners' colours over the realms' territory. It shows them on the campaign's date or, scrubbed
 * through history, on any other ({@link #era}), fading from one date's borders to the next. It opens looking straight
 * down with coreward up, as the holotable's dive ends, and can be tilted from there. The force's system wears a pulsing ring with its jump
 * range around it, its route is drawn star to star, solid as far as it has come with a marker where it is and dashed
 * ahead, a course previewed to the chosen system is dashed too, and the system under the cursor or chosen is ringed.
 *
 * <p>Dragging pans, the wheel zooms towards the cursor, the right button turns and tilts the view, Home flies back to
 * the force, and a click chooses the system under the cursor ({@link #input}). GPU thread.</p>
 */
final class StarMapView {
    /** What the view tells the screen showing it. */
    interface Listener {
        /** The cursor came over {@code star}; null when it left every system. */
        void hovered(@Nullable StarMap.Star star);

        /** The player chose {@code star}; null when the click was on empty space. */
        void chosen(@Nullable StarMap.Star star);

        /**
         * The cursor came over {@code held}'s territory, away from any system, which {@code rival} disputes when not
         * null; null when it left.
         */
        default void realm(@Nullable Borders.Holding held, @Nullable Borders.Holding rival) {
        }

        /**
         * The player right-clicked ({@code x}, {@code y}) light years, on {@code star} or, null, on open space;
         * {@code screenX} and {@code screenY} are the input coordinates clicked.
         */
        default void context(@Nullable StarMap.Star star, double x, double y, int screenX, int screenY) {
        }
    }

    /** Takes the names to write over the map, the most important first; x and y are input coordinates. */
    interface Labels {
        /**
         * Writes {@code star}'s name at {@code alpha} of its strength, as prominent as {@code tier} (0 marked, then
         * {@link #labelTier}); when {@code wanted}, takes its room if free and returns whether it did.
         */
        boolean label(StarMap.Star star, float x, float y, int tier, float alpha, boolean wanted);
    }

    /** Takes the realms' names to write large over their territory; x and y are input coordinates. */
    interface RealmNames {
        /**
         * Writes {@code name} centred on (x, y) at {@code alpha} of its strength, in its realm's colour
         * {@code rgb}; its territory spans about {@code span} pixels either side.
         */
        void realm(String name, String code, int rgb, float x, float y, float span, float alpha);
    }

    /** A realm's name and where it goes: by its most central settled system, its territory {@code spread} wide. */
    record RealmName(String name, String code, int rgb, float x, float y, float spread) {
    }

    /** The holotable's, so its dive ends on the very view the map opens with. */
    private static final float FIELD_OF_VIEW = HoloTableView.FIELD_OF_VIEW;
    private static final float MIN_DISTANCE = 18;
    private static final float MAX_DISTANCE = 3200;
    /** How far over the plane the map opens, straight down, in light years. */
    static final float START_DISTANCE = 650;
    private static final float MIN_PITCH = 20;
    /** Systems are named once the camera is this near, in light years. */
    /** How near the camera must be, in light years, for each {@link #labelTier} of name to show. */
    private static final float[] LABEL_REACH = { Float.MAX_VALUE, Float.MAX_VALUE, 1500, 900, 330, 110 };
    /** How fast names fade in and out, per second. */
    private static final float LABEL_EASE = 7;
    /** How far off screen, in pixels, a system may be and still keep its name fading. */
    private static final float LABEL_MARGIN = 80;
    /** Realms are named over their territory from this far out, in light years, fully by {@link #REALM_FAR}. */
    private static final float REALM_NEAR = 420;
    private static final float REALM_FAR = 800;
    /** The fewest settled systems a realm needs for its name to be written over its territory. */
    private static final int REALM_SYSTEMS = 12;
    /** The tilt, in degrees, the camera eases to when a lens comes on looking straight down. */
    private static final float LENS_PITCH = 52;
    /** The ruler's colour, linear. */
    private static final Vector3 RULER = new Vector3(0.55f, 0.95f, 1.0f);
    /** How quickly, a share a second, the spotlight comes onto a realm or leaves it. */
    private static final float SPOT_RATE = 6;
    /** The tilt, in degrees, above which pillars and beacons fade, being seen end on. */
    private static final float UPRIGHT_PITCH = 86;
    /** How much of its strength the territory keeps under a lens. */
    private static final float LENS_TERRITORY = 0.3f;
    private static final Vector3 HPG_COLOUR = new Vector3(0.42f, 0.78f, 1.0f);
    /** A marker's shapes, added to its pulse: a diamond for the campaign's operations, a square for its bases. */
    private static final int DIAMOND = 10;
    private static final int SQUARE = 20;
    private static final Vector3 MISSION_COLOUR = new Vector3(1.0f, 0.55f, 0.12f);
    private static final Vector3 BATTLE_COLOUR = new Vector3(1.0f, 0.16f, 0.12f);
    private static final Vector3 BASE_COLOUR = new Vector3(0.3f, 1.0f, 0.55f);
    /** The most jumps the force's reach is followed out to. */
    private static final int REACH_JUMPS = 4;
    /** How fast the reach spreads out when shown, in light years a second along its jumps. */
    private static final float REACH_SPEED = 90;
    /** The reach's colours by jump, from the first. */
    private static final int[] REACH_COLOURS = { 0x7FF5FF, 0x58C8FF, 0x7A8CFF, 0xA070F0 };
    /** How widely, in light years, a realm's systems may scatter and still be named as one territory. */
    private static final double REALM_SCATTERED = 260;
    /** How far from a system, in pixels, the cursor may be to pick it. */
    static final float PICK_RADIUS = 14;
    /** How far a press may move, in pixels, and still be a click. */
    private static final float CLICK_SLOP = 6;
    private static final float GRID_STEP = 50;
    private static final float GRID_EXTENT = 2500;
    private static final float WORLD_EDGE = 2400;
    /** The map's dark, linear; the holotable fades its room into it. */
    static final Vector3 BACKGROUND = new Vector3(0.0028f, 0.0036f, 0.0062f);
    private static final Vector3 CYAN = new Vector3(0.25f, 0.75f, 1.0f);
    private static final Vector3 AMBER = new Vector3(1.0f, 0.62f, 0.18f);
    private static final Vector3 WHITE = new Vector3(1, 1, 1);
    private static final int GL_PROGRAM_POINT_SIZE = 0x8642;
    /** Floats per vertex: position, colour, and two parameters. */
    private static final int POINT_FLOATS = 9;
    private static final int LINE_FLOATS = 7;

    private final Listener listener;
    private final Plane plane = new Plane(Vector3.Y, 0);
    private final Vector3 target = new Vector3();
    private final Vector3 grab = new Vector3();
    private final Vector3 ground = new Vector3();
    private final Vector3 flight = new Vector3();
    private final Vector3 scratch = new Vector3();
    private PerspectiveCamera camera;
    private ShaderProgram starShader;
    private ShaderProgram ringShader;
    private ShaderProgram lineShader;
    private ShaderProgram routeShader;
    /** Where the borders on each date are read; none until the screen hands it over. */
    private @Nullable BorderHistory history;
    /** The date the map shows history on; null for the campaign's own. */
    private @Nullable LocalDate era;
    /** The realms' territory on the date shown, once its borders are read. */
    private final TerritoryField territory = new TerritoryField();
    private final Backdrop backdrop = new Backdrop();
    /** The HPG network on the date shown, when it is. */
    private final Strokes network = new Strokes();
    /** The lens's pillars, rising from the systems it rates, their heights at 1 light year to the level. */
    private final Strokes pillars = new Strokes();
    /** The lens the systems are seen through; null for their owners. */
    private @Nullable MapLens lens;
    private boolean networkShown;
    /** The capitals' light shafts, their heights at 1 light year to a realm's. */
    private final Strokes beacons = new Strokes();
    /** The force's reach, jump by jump, out from its system. */
    private final Strokes reach = new Strokes();
    private boolean reachShown;
    /** When, in the view's time, the reach began spreading out. */
    private float reachStart;
    /** How far the lens has come in, 0 to 1, as the territory dims and the pillars rise. */
    private float lensIn;
    private boolean emptyShown = true;
    /** The distance the camera glides to, or NaN when it stays. */
    private float wantedDistance = Float.NaN;
    /** The ruler: whether one is laid, being drawn out, its ends on the plane and its strokes. */
    private boolean ruled;
    private boolean measuring;
    private boolean rulerStale;
    private final Vector3 rulerFrom = new Vector3();
    private final Vector3 rulerTo = new Vector3();
    private final Strokes ruler = new Strokes();
    /** Where the cursor points, in input coordinates; negative when it is off the map. */
    private int pointX = -1;
    private int pointY = -1;
    /** The realm the cursor is over, from 1; 0 for none. */
    private int spotWanted;
    /** The realm in the spotlight, fading in or out at {@link #spotIn}. */
    private int spot;
    private float spotIn;
    private boolean adminShown;
    private float adminIn;
    private @Nullable Borders.Holding spotHeld;
    private @Nullable Borders.Holding spotRival;
    /** How strongly ground must be disputed for the hover card to call it contested; the stripes start here too. */
    private static final float DISPUTED_ABOVE = 0.3f;
    /** The tilt the camera is easing to, in degrees; NaN when it isn't. */
    private float wantedPitch = Float.NaN;
    /** The systems as {@link #territory}'s date had them, in the map's order; the map's own until it is read. */
    private List<StarMap.Star> shown = List.of();
    private @Nullable Mesh stars;
    private @Nullable Mesh lines;
    /** The force's route, star to star. */
    private @Nullable Mesh route;
    /** Whether the voyage's route is drawn; the player can hide it. */
    private boolean routeShown = true;
    /** How many light years along {@link #route} the force has come. */
    private float travelled;
    /** Where the force is between the first two systems of its route; null when it isn't between them. */
    private final Vector3 ship = new Vector3();
    private boolean shipShown;
    /** The course previewed to the chosen system, star to star. */
    private @Nullable Mesh preview;
    /** The ids of the systems on the course previewed; empty for none. */
    private List<String> previewed = List.of();
    /** Whether {@link #preview} needs building again from {@link #previewed}. */
    private boolean previewStale;
    private Mesh grid;
    private Mesh rings;
    private float time;
    private float distance = START_DISTANCE;
    private float yaw;
    private float pitch = 90;
    /** Where the map opens: the holotable's focus, else the force's system. */
    private final Vector3 start = new Vector3();
    private boolean startSet;
    private boolean flying;
    /** Whether the flight turns the camera back to the opening view too. */
    private boolean posing;
    private @Nullable StarMap map;
    private @Nullable StarMap pending;
    /** Every system's position on screen last frame, x then y in input coordinates; NaN when behind the camera. */
    private float[] screen = new float[0];
    /** How strongly each system's name shows, 0 to 1, easing towards whether it has room. */
    private float[] labelAlpha = new float[0];
    private List<RealmName> realmNames = List.of();
    private final Map<String, Integer> index = new HashMap<>();
    private int current = -1;
    private int hovered = -1;
    private int chosen = -1;
    private int pressX;
    private int pressY;
    private int pressButton = -1;
    private boolean grabbed;
    private boolean dragged;
    private final InputProcessor input = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            pressX = screenX;
            pressY = screenY;
            pressButton = button;
            dragged = false;
            measuring = button == Input.Buttons.LEFT && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                  || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) && snap(screenX, screenY, rulerFrom);
            if (measuring) {
                rulerTo.set(rulerFrom);
                ruled = true;
                rulerStale = true;
            }
            grabbed = !measuring && button == Input.Buttons.LEFT && ground(screenX, screenY, grab);
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (Math.abs(screenX - pressX) + Math.abs(screenY - pressY) > CLICK_SLOP) {
                dragged = true;
            }
            if (!dragged) {
                return true;
            }
            if (measuring) {
                if (snap(screenX, screenY, rulerTo)) {
                    rulerStale = true;
                }
                return true;
            }
            flying = false;
            posing = false;
            if (pressButton == Input.Buttons.RIGHT) {
                wantedPitch = Float.NaN;
                yaw -= Gdx.input.getDeltaX() * 0.25f;
                pitch = MathUtils.clamp(pitch + Gdx.input.getDeltaY() * 0.2f, MIN_PITCH, 90);
                aim();
            } else if (grabbed && ground(screenX, screenY, ground)) {
                target.add(grab).sub(ground);
                clampTarget();
                aim();
            }
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (measuring) {
                measuring = false;
                // A shift-click without a drag measures nothing
                ruled = dragged;
                rulerStale = true;
            } else if (!dragged && button == Input.Buttons.LEFT && button == pressButton) {
                int picked = nearest(screen, screenX, screenY, PICK_RADIUS);
                choose(picked);
            } else if (!dragged && button == Input.Buttons.RIGHT && button == pressButton) {
                StarMap.Star on = star(nearest(screen, screenX, screenY, PICK_RADIUS));
                if (on != null) {
                    listener.context(on, on.x(), on.y(), screenX, screenY);
                } else if (ground(screenX, screenY, scratch)) {
                    listener.context(null, scratch.x, -scratch.z, screenX, screenY);
                }
            }
            pressButton = -1;
            return true;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            point(screenX, screenY);
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            zoom(amountY, Gdx.input.getX(), Gdx.input.getY());
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.HOME) {
                home();
                return true;
            }
            return false;
        }
    };

    StarMapView(Listener listener) {
        this.listener = listener;
    }

    /** The map's controls, for the screen showing the view to put behind its own. */
    InputProcessor input() {
        return input;
    }

    /** The campaign's map changed; it shows from the next frame. */
    void show(StarMap changed) {
        pending = changed;
    }

    /** Previews the course through the systems {@code ids}, the force's first; null or empty clears it. */
    void preview(@Nullable List<String> ids) {
        List<String> wanted = ids == null ? List.of() : List.copyOf(ids);
        if (!wanted.equals(previewed)) {
            previewed = wanted;
            previewStale = true;
        }
    }

    /** Reads the borders on the dates the map shows through {@code borders}. */
    void history(BorderHistory borders) {
        history = borders;
    }

    /**
     * Shows the systems and territory on {@code date}, or on the campaign's date when null, once that date's borders
     * are read; until then the map keeps showing the date it did.
     */
    void era(@Nullable LocalDate date) {
        era = date;
    }

    /** The date whose owners and borders the map shows; null until the first are read. */
    @Nullable LocalDate shownDate() {
        Borders showing = territory.shown();
        return showing == null ? null : showing.date();
    }

    /** Whether the view has a map to show yet. */
    boolean loaded() {
        return map != null || pending != null;
    }

    /** The system chosen, or null. */
    @Nullable StarMap.Star chosen() {
        return star(chosen);
    }

    /** Draws the voyage's route, its stops, their names and the force's ship on it, or hides them all. */
    void showRoute(boolean shown) {
        routeShown = shown;
    }

    /** Whether the voyage's route is drawn. */
    boolean routeShown() {
        return routeShown;
    }

    /** Lets go of the chosen system. */
    void clearChoice() {
        choose(-1);
    }

    /** Flies the camera back to the force's system. */
    void home() {
        StarMap.Star here = star(current);
        if (here != null) {
            flyTo(here);
        }
    }

    /** Flies the camera over ({@code x}, {@code y}) light years. */
    void flyTo(double x, double y) {
        world(x, y, flight);
        flying = true;
        posing = false;
    }

    /** Flies the camera over the systems {@code ids}, far enough out to see them all. */
    void frame(List<String> ids) {
        double west = Double.MAX_VALUE;
        double east = -Double.MAX_VALUE;
        double south = Double.MAX_VALUE;
        double north = -Double.MAX_VALUE;
        for (String id : ids) {
            StarMap.Star star = system(id);
            if (star != null) {
                west = Math.min(west, star.x());
                east = Math.max(east, star.x());
                south = Math.min(south, star.y());
                north = Math.max(north, star.y());
            }
        }
        if (west > east) {
            return;
        }
        flyTo((west + east) / 2, (south + north) / 2);
        float aspect = Math.max(1, (float) Gdx.graphics.getWidth() / Math.max(1, Gdx.graphics.getHeight()));
        float span = (float) Math.max(north - south, (east - west) / aspect) + 30;
        wantedDistance = MathUtils.clamp(span * 0.75f / MathUtils.tanDeg(FIELD_OF_VIEW / 2), MIN_DISTANCE,
              MAX_DISTANCE);
    }

    /** Glides the camera {@code factor} times as far from the plane, within the zoom's limits. */
    void zoomBy(float factor) {
        float from = Float.isNaN(wantedDistance) ? distance : wantedDistance;
        wantedDistance = MathUtils.clamp(from * factor, MIN_DISTANCE, MAX_DISTANCE);
    }

    /** The force's system, or null when the force is between systems or unknown. */
    @Nullable StarMap.Star current() {
        return star(current);
    }

    /** The system {@code id}, as the map shows it, or null when it shows none such. */
    @Nullable StarMap.Star system(String id) {
        Integer at = index.get(id);
        return at == null ? null : star(at);
    }

    /** Shows the systems no one has settled, or hides them; the force's and the chosen stay. */
    void empty(boolean shown) {
        if (shown != emptyShown) {
            emptyShown = shown;
            restyle();
        }
    }

    /** Whether the systems no one has settled show. */
    boolean empty() {
        return emptyShown;
    }

    /** Whether a ruler is laid. */
    boolean ruled() {
        return ruled;
    }

    /** Lays the ruler from the force's system to {@code star}. */
    void measureFromForce(StarMap.Star star) {
        StarMap.Star here = star(current);
        if (here != null) {
            measure(here.x(), here.y(), star.x(), star.y());
        }
    }

    /** Flies the camera to {@code star} and chooses it. */
    void focus(StarMap.Star star) {
        Integer at = index.get(star.id());
        if (at != null) {
            flyTo(star);
            choose(at);
        }
    }

    /** Opens the map over ({@code x}, {@code y}) light years, where the holotable centres its own. */
    void startAt(double x, double y) {
        world(x, y, start);
        startSet = true;
    }

    /** Flies the camera back to the view the map opened with, for the holotable to take over from. */
    void returnToStart() {
        flight.set(start);
        flying = true;
        posing = true;
    }

    /** Puts the camera on the view the map opens with at once. */
    void snapToStart() {
        wantedPitch = Float.NaN;
        wantedDistance = Float.NaN;
        target.set(start);
        distance = START_DISTANCE;
        yaw = 0;
        pitch = 90;
        flying = false;
        posing = false;
        aim();
    }

    /**
     * Puts the camera over ({@code x}, {@code y}) light years at once, {@code height} light years away, tilted
     * {@code tilt} degrees over the plane (90 straight down) and turned {@code turn} degrees from coreward.
     */
    void look(double x, double y, float height, float tilt, float turn) {
        world(x, y, target);
        distance = MathUtils.clamp(height, MIN_DISTANCE, MAX_DISTANCE);
        pitch = MathUtils.clamp(tilt, MIN_PITCH, 90);
        yaw = turn;
        flying = false;
        posing = false;
        aim();
    }

    /** Whether the camera is still, not flying anywhere. */
    boolean settled() {
        return !flying;
    }

    /** Which way the camera faces over the plane, in degrees from coreward, anticlockwise. */
    float yaw() {
        return yaw;
    }

    /**
     * Looks at the systems through {@code wanted}, or at their owners when null; looking down on the map, the camera
     * tilts for the lens's pillars to show.
     */
    void lens(@Nullable MapLens wanted) {
        if (wanted == lens) {
            return;
        }
        lens = wanted;
        if (wanted != null && pitch > LENS_PITCH + 5) {
            wantedPitch = LENS_PITCH;
        }
        restyle();
    }

    /** The lens the systems are seen through; null for their owners. */
    @Nullable MapLens lens() {
        return lens;
    }

    /** Shows or hides the HPG network. */
    void network(boolean shown) {
        if (shown != networkShown) {
            networkShown = shown;
            restyle();
        }
    }

    /** Whether the HPG network shows. */
    boolean network() {
        return networkShown;
    }

    /** Shows or hides how far the force reaches, jump by jump; shown, it spreads out from the force's system. */
    void reach(boolean shown) {
        if (shown != reachShown) {
            reachShown = shown;
            reachStart = time;
            restyle();
        }
    }

    /** Whether the force's reach shows. */
    boolean reach() {
        return reachShown;
    }

    /** Shows or hides where the realms' administrative regions meet, fading them in or out. */
    void admin(boolean shown) {
        adminShown = shown;
    }

    /** Whether the realms' administrative regions show. */
    boolean admin() {
        return adminShown;
    }

    /** How many settled systems on screen the lens rates at each level, from 1 to its levels; empty without one. */
    int[] lensCounts() {
        MapLens looking = lens;
        if (looking == null || camera == null) {
            return new int[0];
        }
        int[] counts = new int[looking.levels() + 1];
        List<StarMap.Star> all = shown;
        for (int i = 0; i < all.size() && i * 2 + 1 < screen.length; i++) {
            float x = screen[i * 2];
            float y = screen[i * 2 + 1];
            if (Float.isNaN(x) || x < 0 || y < 0 || x > camera.viewportWidth || y > camera.viewportHeight) {
                continue;
            }
            counts[looking.level(all.get(i).lenses())]++;
        }
        return counts;
    }

    /** How many input pixels a light year spans across the middle of the screen; 0 before the view is made. */
    float pixelsPerLightYear() {
        if (camera == null) {
            return 0;
        }
        Vector3 near = camera.project(scratch.set(target));
        float x = near.x;
        float y = near.y;
        Vector3 side = scratch.set(camera.direction).crs(camera.up).nor().scl(10).add(target);
        camera.project(side);
        return Vector2.dst(x, y, side.x, side.y) / 10;
    }

    /** The systems shown, in the map's order, as the date shown had them; empty before it loads. */
    List<StarMap.Star> stars() {
        return shown;
    }

    /** The factions with the most systems on screen, at most {@code limit} of them. */
    List<Legend> legend(int limit) {
        return legend(stars(), screen, camera == null ? 0 : camera.viewportWidth,
              camera == null ? 0 : camera.viewportHeight, limit);
    }

    void create() {
        starShader = HoloTableView.compile(StarMapShaders.STAR_VERTEX, StarMapShaders.STAR_FRAGMENT);
        ringShader = HoloTableView.compile(StarMapShaders.RING_VERTEX, StarMapShaders.RING_FRAGMENT);
        lineShader = HoloTableView.compile(StarMapShaders.LINE_VERTEX, StarMapShaders.LINE_FRAGMENT);
        routeShader = HoloTableView.compile(StarMapShaders.ROUTE_VERTEX, StarMapShaders.ROUTE_FRAGMENT);
        territory.create();
        backdrop.create();
        network.create();
        beacons.create();
        reach.create();
        ruler.create();
        pillars.create();
        grid = grid();
        rings = new Mesh(false, 512, 0, pointAttributes());
        camera = new PerspectiveCamera(FIELD_OF_VIEW, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 1;
        camera.far = MAX_DISTANCE * 5;
        aim();
    }

    void resize(int width, int height) {
        if (camera != null && width > 0 && height > 0) {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
            aim();
        }
    }

    void render() {
        render(1, true);
    }

    /**
     * Draws the map at {@code fade} of its brightness, 0 to 1; over what is already drawn unless {@code clear}, as the
     * holotable's room fades into it.
     */
    void render(float fade, boolean clear) {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        time += delta;
        if (pending != null) {
            load(pending);
            pending = null;
        }
        history(delta);
        if (previewStale && map != null) {
            if (preview != null) {
                preview.dispose();
            }
            preview = routeMesh(previewed, CYAN, 0.8f);
            previewStale = false;
        }
        if (flying) {
            fly(delta);
        }
        if (!Float.isNaN(wantedDistance)) {
            distance = (float) Math.exp(MathUtils.lerp((float) Math.log(distance), (float) Math.log(wantedDistance),
                  1 - (float) Math.exp(-delta * 6)));
            if (Math.abs(distance - wantedDistance) < 0.05f) {
                distance = wantedDistance;
                wantedDistance = Float.NaN;
            }
            aim();
        }
        if (!Float.isNaN(wantedPitch)) {
            pitch = MathUtils.lerp(pitch, wantedPitch, 1 - (float) Math.exp(-delta * 4));
            if (Math.abs(pitch - wantedPitch) < 0.05f) {
                pitch = wantedPitch;
                wantedPitch = Float.NaN;
            }
            aim();
        }
        project();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        if (clear) {
            Gdx.gl.glClearColor(HoloTableView.display(BACKGROUND.x), HoloTableView.display(BACKGROUND.y),
                  HoloTableView.display(BACKGROUND.z), 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        }
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        backdrop.render(camera, fade, time, Gdx.graphics.getBackBufferHeight() / (float) Math.max(1,
              Gdx.graphics.getHeight()));
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        lensIn = MathUtils.clamp(lensIn + (lens == null ? -delta : delta) * 3, 0, 1);
        spotlight(delta);
        territory.render(camera.combined, fade * MathUtils.lerp(1, LENS_TERRITORY, lensIn), time);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);

        lineShader.bind();
        lineShader.setUniformMatrix("u_projView", camera.combined);
        lineShader.setUniformf("u_focus", target);
        lineShader.setUniformf("u_fade", fade);
        lineShader.setUniformf("u_reach", Math.max(distance * 1.8f, 120));
        grid.render(lineShader, GL20.GL_LINES);
        if (lines != null) {
            lineShader.setUniformf("u_reach", 1e6f);
            lines.render(lineShader, GL20.GL_LINES);
        }
        if (route != null || preview != null) {
            routeShader.bind();
            routeShader.setUniformMatrix("u_projView", camera.combined);
            routeShader.setUniformf("u_time", time);
            routeShader.setUniformf("u_fade", fade);
            routeShader.setUniformf("u_dash", Math.max(distance * 0.012f, 0.4f));
            if (preview != null) {
                routeShader.setUniformf("u_done", -1f);
                preview.render(routeShader, GL20.GL_LINES);
            }
            if (route != null && routeShown) {
                routeShader.setUniformf("u_done", travelled);
                route.render(routeShader, GL20.GL_LINES);
            }
        }

        network.render(camera, fade, time, 1);
        if (rulerStale) {
            rulerStale = false;
            rulerStrokes();
        }
        ruler.render(camera, fade, time, 1);
        if (reachShown) {
            reach.render(camera, fade, time, 1, (time - reachStart) * REACH_SPEED);
        }
        // Seen from straight above, upright strokes are only streaks; they show as the map tilts
        float upright = MathUtils.clamp((UPRIGHT_PITCH - pitch) / 20, 0, 1);
        beacons.render(camera, fade * (1 - lensIn) * upright, time, beaconHeight());
        if (lensIn > 0) {
            pillars.render(camera, fade * upright, time, lensIn * lensIn * (3 - 2 * lensIn) * pillarHeight());
        }
        Gdx.gl.glEnable(GL_PROGRAM_POINT_SIZE);
        if (stars != null) {
            starShader.bind();
            starShader.setUniformMatrix("u_projView", camera.combined);
            starShader.setUniformf("u_camera", camera.position);
            starShader.setUniformf("u_time", time);
            starShader.setUniformf("u_fade", fade);
            starShader.setUniformf("u_pixels",
                  Gdx.graphics.getBackBufferHeight() / (2 * MathUtils.tanDeg(FIELD_OF_VIEW / 2)));
            stars.render(starShader, GL20.GL_POINTS);
        }
        int count = rings();
        if (count > 0) {
            ringShader.bind();
            ringShader.setUniformMatrix("u_projView", camera.combined);
            ringShader.setUniformf("u_time", time);
            ringShader.setUniformf("u_fade", fade);
            rings.render(ringShader, GL20.GL_POINTS, 0, count);
        }
        Gdx.gl.glDisable(GL_PROGRAM_POINT_SIZE);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Hands {@code labels} the names to write this frame, the most important first: the marked systems, then the
     * realms' capitals, then the most populous worlds nearest the middle. Each name fades in and out as it comes and
     * goes, and one already shown keeps its place ahead of one that would push it out.
     */
    void labels(Labels labels) {
        StarMap today = map;
        List<StarMap.Star> all = shown;
        if (today == null || all.size() != labelAlpha.length) {
            return;
        }
        float ease = Math.min(1, Math.min(Gdx.graphics.getDeltaTime(), 0.1f) * LABEL_EASE);
        boolean[] marked = new boolean[all.size()];
        boolean[] quiet = new boolean[all.size()];
        for (int at : new int[] { chosen, hovered, current }) {
            if (at >= 0) {
                marked[at] = true;
            }
        }
        List<String> stops = routeShown ? today.route() : List.of();
        for (List<String> ids : List.of(stops, previewed, List.copyOf(today.marks().keySet()))) {
            for (String id : ids) {
                Integer at = index.get(id);
                if (at != null) {
                    marked[at] = true;
                }
            }
        }
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        long[] order = new long[all.size()];
        int count = 0;
        for (int i = 0; i < all.size(); i++) {
            float x = screen[i * 2];
            float y = screen[i * 2 + 1];
            if (Float.isNaN(x) || x < -LABEL_MARGIN || y < -LABEL_MARGIN || x > width + LABEL_MARGIN
                  || y > height + LABEL_MARGIN) {
                labelAlpha[i] = 0;
                continue;
            }
            StarMap.Star star = all.get(i);
            int tier = marked[i] ? 0 : labelTier(star);
            // Through a lens only the best rated systems keep their names, so the columns read clearly
            quiet[i] = !marked[i] && lens != null && lens.level(star.lenses()) * 4 < lens.levels() * 3;
            boolean wanted = marked[i] || (!quiet[i] && distance <= LABEL_REACH[tier]);
            if (!wanted && labelAlpha[i] <= 0) {
                continue;
            }
            float dx = x - width / 2;
            float dy = y - height / 2;
            long near = Math.min((long) (dx * dx + dy * dy), Integer.MAX_VALUE);
            long people = 15 - Math.min(15, (long) Math.log10(Math.max(1, star.people())));
            long stay = labelAlpha[i] > 0.5f ? 0 : 1;
            order[count++] = ((long) tier << 56) | (stay << 55) | (people << 51) | (near << 20) | i;
        }
        Arrays.sort(order, 0, count);
        for (int n = 0; n < count; n++) {
            int i = (int) (order[n] & 0xFFFFF);
            int tier = (int) (order[n] >>> 56);
            boolean wanted = tier == 0 || (!quiet[i] && distance <= LABEL_REACH[tier]);
            float alpha = tier == 0 ? 1 : labelAlpha[i];
            boolean placed = labels.label(all.get(i), screen[i * 2], screen[i * 2 + 1], tier, alpha, wanted);
            labelAlpha[i] = tier == 0 ? 1 : alpha + ((placed ? 1 : 0) - alpha) * ease;
            if (labelAlpha[i] < 0.01f && !placed) {
                labelAlpha[i] = 0;
            }
        }
    }

    /** Hands {@code names} the realms' names to write over their territory, fading in as the camera pulls out. */
    void realmNames(RealmNames names) {
        float alpha = MathUtils.clamp((distance - REALM_NEAR) / (REALM_FAR - REALM_NEAR), 0, 1);
        if (alpha <= 0 || camera == null) {
            return;
        }
        float pixels = pixelsPerLightYear();
        for (RealmName realm : realmNames) {
            Vector3 at = camera.project(world(realm.x(), realm.y(), scratch));
            names.realm(realm.name(), realm.code(), realm.rgb(), at.x, camera.viewportHeight - at.y,
                  realm.spread() * pixels, alpha * alpha * (3 - 2 * alpha));
        }
    }

    /**
     * The realms holding at least {@link #REALM_SYSTEMS} settled systems of {@code all}, each named by its most central
     * settled system.
     */
    static List<RealmName> realmNames(List<StarMap.Star> all) {
        Map<String, double[]> sums = new HashMap<>();
        Map<String, String> codes = new HashMap<>();
        for (StarMap.Star star : all) {
            if (star.settled() && !star.faction().isBlank()) {
                double[] sum = sums.computeIfAbsent(star.faction(), name -> new double[5]);
                sum[0] += star.x();
                sum[1] += star.y();
                sum[2] += star.x() * star.x() + star.y() * star.y();
                sum[3]++;
                sum[4] = star.rgb();
                codes.put(star.faction(), star.code());
            }
        }
        List<RealmName> names = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : sums.entrySet()) {
            double[] sum = entry.getValue();
            if (sum[3] < REALM_SYSTEMS) {
                continue;
            }
            double cx = sum[0] / sum[3];
            double cy = sum[1] / sum[3];
            double spread = Math.sqrt(Math.max(0, sum[2] / sum[3] - cx * cx - cy * cy));
            // Worlds scattered across the map, the independents', hold no one territory to name
            if (spread > REALM_SCATTERED) {
                continue;
            }
            StarMap.Star centre = null;
            double best = Double.MAX_VALUE;
            for (StarMap.Star star : all) {
                if (star.settled() && entry.getKey().equals(star.faction())) {
                    double d = (star.x() - cx) * (star.x() - cx) + (star.y() - cy) * (star.y() - cy);
                    if (d < best) {
                        best = d;
                        centre = star;
                    }
                }
            }
            if (centre != null) {
                names.add(new RealmName(entry.getKey(), codes.get(entry.getKey()), (int) sum[4], (float) centre.x(), (float) centre.y(),
                      (float) spread));
            }
        }
        return List.copyOf(names);
    }

    /** How prominent a system's name is: 1 a realm's capital, 2 a region's, 3 a district's, 4 settled, 5 empty. */
    static int labelTier(StarMap.Star star) {
        if (!star.settled()) {
            return 5;
        }
        return switch (star.capital()) {
            case 3 -> 1;
            case 2 -> 2;
            case 1 -> 3;
            default -> 4;
        };
    }

    void dispose() {
        for (Disposable resource : new Disposable[] { starShader, ringShader, lineShader, routeShader,
              stars, lines, route, preview, grid, rings, territory, backdrop, network, pillars, beacons, reach,
              ruler }) {
            if (resource != null) {
                resource.dispose();
            }
        }
    }

    /**
     * The index of the point in {@code xy} (x then y, NaN when hidden) nearest to ({@code x}, {@code y}) within
     * {@code radius}, or -1 when none is.
     */
    static int nearest(float[] xy, float x, float y, float radius) {
        int best = -1;
        float bestDistance = radius * radius;
        for (int i = 0; i + 1 < xy.length; i += 2) {
            float dx = xy[i] - x;
            float dy = xy[i + 1] - y;
            float squared = dx * dx + dy * dy;
            if (squared <= bestDistance) {
                bestDistance = squared;
                best = i / 2;
            }
        }
        return best;
    }

    /** Where a system at ({@code x}, {@code y}) light years lies in the view's world: coreward is -z. */
    static Vector3 world(double x, double y, Vector3 out) {
        return out.set((float) x, 0, (float) -y);
    }

    /** A faction in the legend: its name, colour (0xRRGGBB) and how many of its systems are on screen. */
    record Legend(String name, int rgb, int systems) {
    }

    /**
     * The systems in {@code all} whose names hold {@code query}, ignoring case, at most {@code limit}: those starting
     * with it first, then the settled before the empty, then by name. None for a blank query.
     */
    static List<StarMap.Star> search(List<StarMap.Star> all, String query, int limit) {
        String wanted = query.strip().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty() || limit <= 0) {
            return List.of();
        }
        return all.stream()
              .filter(star -> star.name().toLowerCase(Locale.ROOT).contains(wanted))
              .sorted(Comparator.comparing((StarMap.Star star) -> !star.name().toLowerCase(Locale.ROOT)
                          .startsWith(wanted))
                    .thenComparing(star -> !star.settled())
                    .thenComparing(StarMap.Star::name, String.CASE_INSENSITIVE_ORDER))
              .limit(limit)
              .toList();
    }

    /**
     * The factions holding the most settled systems of {@code all} on a {@code width} by {@code height} screen, where
     * {@code screen} holds each system's position (x then y, NaN when hidden); at most {@code limit}, the most systems
     * first, then by name.
     */
    static List<Legend> legend(List<StarMap.Star> all, float[] screen, float width, float height, int limit) {
        Map<String, int[]> counts = new HashMap<>();
        for (int i = 0; i < all.size() && i * 2 + 1 < screen.length; i++) {
            StarMap.Star star = all.get(i);
            float x = screen[i * 2];
            float y = screen[i * 2 + 1];
            if (!star.settled() || star.faction().isBlank() || Float.isNaN(x) || x < 0 || y < 0 || x > width
                  || y > height) {
                continue;
            }
            counts.computeIfAbsent(star.faction(), name -> new int[] { star.rgb(), 0 })[1]++;
        }
        return counts.entrySet().stream()
              .map(entry -> new Legend(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
              .sorted(Comparator.comparingInt(Legend::systems).reversed().thenComparing(Legend::name))
              .limit(Math.max(limit, 0))
              .toList();
    }

    private void load(StarMap changed) {
        StarMap.Star wasChosen = star(chosen);
        boolean first = map == null;
        map = changed;
        index.clear();
        List<StarMap.Star> all = changed.stars();
        for (int i = 0; i < all.size(); i++) {
            index.put(all.get(i).id(), i);
        }
        screen = new float[all.size() * 2];
        labelAlpha = new float[all.size()];
        Arrays.fill(screen, Float.NaN);
        current = changed.current() == null ? -1 : index.getOrDefault(changed.current(), -1);
        chosen = wasChosen == null ? -1 : index.getOrDefault(wasChosen.id(), -1);
        hovered = -1;
        align();
        if (lines != null) {
            lines.dispose();
        }
        lines = lineMesh();
        if (route != null) {
            route.dispose();
        }
        route = routeMesh(changed.route(), AMBER, 0.9f);
        voyage(changed);
        previewStale = true;
        if (first) {
            StarMap.Star here = star(current);
            if (!startSet && here != null) {
                world(here.x(), here.y(), start);
            }
            snapToStart();
        }
    }

    private @Nullable StarMap.Star star(int at) {
        List<StarMap.Star> all = shown;
        return at < 0 || at >= all.size() ? null : all.get(at);
    }

    /**
     * Turns the territory towards the date wanted, {@link #era} or the campaign's, once its borders are read, and the
     * turning {@code delta} seconds on.
     */
    private void history(float delta) {
        territory.update(delta);
        StarMap today = map;
        BorderHistory borders = history;
        if (today == null || borders == null) {
            return;
        }
        LocalDate wanted = era == null ? today.date() : era;
        Borders showing = territory.shown();
        if (showing != null && showing.date().equals(wanted)) {
            return;
        }
        Borders read = borders.get(wanted);
        if (read == null) {
            return;
        }
        territory.show(read);
        align();
        if (hovered >= 0) {
            listener.hovered(star(hovered));
        }
    }

    /** Takes the systems shown from {@link #territory}'s date, in the map's order, and draws them so. */
    private void align() {
        StarMap today = map;
        if (today == null) {
            return;
        }
        Borders showing = territory.shown();
        if (showing == null || showing.date().equals(today.date())) {
            shown = today.stars();
        } else {
            Map<String, StarMap.Star> then = new HashMap<>();
            for (StarMap.Star star : showing.stars()) {
                then.put(star.id(), star);
            }
            List<StarMap.Star> aligned = new ArrayList<>(today.stars().size());
            for (StarMap.Star star : today.stars()) {
                aligned.add(then.getOrDefault(star.id(), star));
            }
            shown = aligned;
        }
        realmNames = realmNames(shown);
        restyle();
    }

    /** Draws the systems shown again as the lens and overlays now have them. */
    private void restyle() {
        if (stars != null) {
            stars.dispose();
        }
        stars = starMesh(shown, lens, emptyShown);
        pillars.clear();
        if (lens != null) {
            pillars(pillars, shown, lens);
        }
        pillars.upload();
        network.clear();
        Borders showing = territory.shown();
        if ((networkShown || lens == MapLens.HPG) && showing != null) {
            float[] links = showing.hpgLinks();
            for (int i = 0; i + 4 < links.length; i += 5) {
                boolean a = links[i + 4] >= MapLens.HPG.levels();
                float bright = a ? 0.5f : 0.22f;
                network.add(links[i], 0, -links[i + 1], links[i + 2], 0, -links[i + 3], HPG_COLOUR.x * bright,
                      HPG_COLOUR.y * bright, HPG_COLOUR.z * bright, 1, a ? 2.0f : 1.3f, 0, a ? 30 : 0);
            }
        }
        network.upload();
        beacons.clear();
        for (StarMap.Star star : shown) {
            if (star.capital() >= 2) {
                beacon(beacons, star);
            }
        }
        beacons.upload();
        reach.clear();
        if (reachShown) {
            reach(reach, shown, current);
        }
        reach.upload();
    }

    /** A capital's light shaft in its realm's colour, brightest at its foot; a realm's stands twice a region's. */
    private static void beacon(Strokes into, StarMap.Star star) {
        int rgb = star.rgb();
        float r = linear((rgb >> 16) & 0xFF);
        float g = linear((rgb >> 8) & 0xFF);
        float b = linear(rgb & 0xFF);
        boolean realm = star.capital() >= 3;
        float top = realm ? 1 : 0.5f;
        float bright = realm ? 1 : 0.6f;
        float x = (float) star.x();
        float z = (float) -star.y();
        float hr = r * 0.6f + 0.4f;
        float hg = g * 0.6f + 0.4f;
        float hb = b * 0.6f + 0.4f;
        // A wide soft haze round a narrow hot core, both fading smoothly as they rise
        into.add(x, 0, z, x, top, z, r, g, b, 0.9f * bright, 0, realm ? 22 : 13, 0, 0);
        into.add(x, 0, z, x, top * 0.75f, z, hr, hg, hb, 2.2f * bright, 0, realm ? 4.5f : 3, 0, 0);
    }

    /** How tall, in light years, a realm's capital's beacon stands: taller the further out the camera is. */
    private float beaconHeight() {
        return MathUtils.clamp(distance * 0.08f, 10, 160);
    }

    /**
     * The jumps out from {@code from} in {@code all}, up to {@value #REACH_JUMPS}, each system reached by its shortest
     * count of jumps of {@link MHQConstants#MAX_JUMP_RADIUS} light years at most, its stroke from the system it was
     * reached from coloured by the count and measured along from {@code from}, so the reach spreads out from it.
     */
    private static void reach(Strokes into, List<StarMap.Star> all, int from) {
        if (from < 0 || from >= all.size()) {
            return;
        }
        double radius = MHQConstants.MAX_JUMP_RADIUS;
        Map<Long, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            StarMap.Star star = all.get(i);
            cells.computeIfAbsent(reachCell(Math.floor(star.x() / radius), Math.floor(star.y() / radius)),
                  key -> new ArrayList<>()).add(i);
        }
        int[] jumps = new int[all.size()];
        float[] along = new float[all.size()];
        Arrays.fill(jumps, -1);
        jumps[from] = 0;
        List<Integer> ring = List.of(from);
        for (int jump = 1; jump <= REACH_JUMPS && !ring.isEmpty(); jump++) {
            List<Integer> next = new ArrayList<>();
            int rgb = REACH_COLOURS[Math.min(jump, REACH_COLOURS.length) - 1];
            float fade = 1.0f - 0.18f * (jump - 1);
            for (int at : ring) {
                StarMap.Star star = all.get(at);
                double cx = Math.floor(star.x() / radius);
                double cy = Math.floor(star.y() / radius);
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        for (int other : cells.getOrDefault(reachCell(cx + i, cy + j), List.of())) {
                            if (jumps[other] >= 0) {
                                continue;
                            }
                            StarMap.Star there = all.get(other);
                            double dx = there.x() - star.x();
                            double dy = there.y() - star.y();
                            double length = Math.sqrt(dx * dx + dy * dy);
                            if (length > radius) {
                                continue;
                            }
                            jumps[other] = jump;
                            along[other] = along[at] + (float) length;
                            next.add(other);
                            float bright = 1.2f * fade;
                            into.add((float) star.x(), 0, (float) -star.y(), (float) there.x(), 0, (float) -there.y(),
                                  linear((rgb >> 16) & 0xFF) * bright, linear((rgb >> 8) & 0xFF) * bright,
                                  linear(rgb & 0xFF) * bright, 1, jump == 1 ? 3.0f : 2.2f, along[at], 18);
                        }
                    }
                }
            }
            ring = next;
        }
    }

    private static long reachCell(double i, double j) {
        return ((long) i << 32) ^ ((long) j & 0xFFFFFFFFL);
    }

    /** The point on the plane under {@code screenX}, {@code screenY}, on the system there if any, into {@code out}. */
    private boolean snap(int screenX, int screenY, Vector3 out) {
        StarMap.Star near = star(nearest(screen, screenX, screenY, PICK_RADIUS));
        if (near != null) {
            world(near.x(), near.y(), out);
            return true;
        }
        return ground(screenX, screenY, out);
    }

    /** Lays the ruler from ({@code x1}, {@code y1}) to ({@code x2}, {@code y2}) light years. */
    void measure(double x1, double y1, double x2, double y2) {
        world(x1, y1, rulerFrom);
        world(x2, y2, rulerTo);
        ruled = true;
        rulerStale = true;
    }

    /** Takes the ruler away; false when none was laid. */
    boolean clearRuler() {
        if (!ruled) {
            return false;
        }
        ruled = false;
        measuring = false;
        rulerStale = true;
        return true;
    }

    /**
     * The ruler's reading, or null when none is laid: where to write it, in input coordinates, then the light years it
     * spans.
     */
    @Nullable float[] ruler() {
        if (!ruled || camera == null) {
            return null;
        }
        float span = rulerFrom.dst(rulerTo);
        if (span < 0.05f) {
            return null;
        }
        Vector3 mid = camera.project(scratch.set(rulerFrom).add(rulerTo).scl(0.5f));
        return new float[] { mid.x, camera.viewportHeight - mid.y, span };
    }

    /** Lays the ruler's strokes: a flowing line with a tick at each jump's reach along it and a bar at each end. */
    private void rulerStrokes() {
        ruler.clear();
        float span = rulerFrom.dst(rulerTo);
        if (ruled && span > 0.05f) {
            float dx = (rulerTo.x - rulerFrom.x) / span;
            float dz = (rulerTo.z - rulerFrom.z) / span;
            ruler.add(rulerFrom.x, 0, rulerFrom.z, rulerTo.x, 0, rulerTo.z, RULER.x, RULER.y, RULER.z, 1.0f, 3.4f,
                  0, -6);
            float tick = Math.max(1.2f, span * 0.012f);
            float jump = (float) MHQConstants.MAX_JUMP_RADIUS;
            for (float at = jump; at < span; at += jump) {
                float x = rulerFrom.x + dx * at;
                float z = rulerFrom.z + dz * at;
                ruler.add(x - dz * tick, 0, z + dx * tick, x + dz * tick, 0, z - dx * tick, RULER.x, RULER.y,
                      RULER.z, 1.2f, 2.0f, 0, 0);
            }
            for (Vector3 end : new Vector3[] { rulerFrom, rulerTo }) {
                ruler.add(end.x - dz * tick * 2, 0, end.z + dx * tick * 2, end.x + dz * tick * 2, 0,
                      end.z - dx * tick * 2, 1, 1, 1, 1.4f, 2.6f, 0, 0);
            }
        }
        ruler.upload();
    }

    /** The cursor is at {@code screenX}, {@code screenY}, in input coordinates; negative when it left the map. */
    void point(int screenX, int screenY) {
        pointX = screenX;
        pointY = screenY;
        hover(screenX < 0 ? -1 : nearest(screen, screenX, screenY, PICK_RADIUS));
    }

    /**
     * Finds the realm whose territory lies under the cursor, away from any system, and eases the spotlight onto it
     * over {@code delta} seconds.
     */
    private void spotlight(float delta) {
        Borders showing = territory.shown();
        int wanted = 0;
        Borders.Holding held = null;
        Borders.Holding rival = null;
        StarMap.Star over = star(hovered);
        // Over a system the realm it lies in lights up too, but the card stays the system's
        boolean atStar = over != null;
        if (showing != null && pointX >= 0 && pressButton < 0 && !flying && lens == null
              && (atStar || ground(pointX, pointY, scratch))) {
            InfluenceMap land = showing.land();
            int column = land.column(atStar ? (float) over.x() : scratch.x);
            int row = land.row(atStar ? (float) over.y() : -scratch.z);
            if (column >= 0 && row >= 0 && column < land.width() && row < land.height()) {
                wanted = land.owner(column, row);
                held = wanted > 0 ? showing.holding(wanted - 1) : null;
                if (held == null) {
                    wanted = 0;
                }
                int partner = land.partner(column, row);
                if (held != null && partner > 0 && partner != wanted && land.dispute(column, row) >= DISPUTED_ABOVE) {
                    rival = showing.holding(partner - 1);
                }
            }
        }
        if (atStar) {
            held = null;
            rival = null;
        }
        if (!Objects.equals(held, spotHeld) || !Objects.equals(rival, spotRival)) {
            spotHeld = held;
            spotRival = rival;
            listener.realm(held, rival);
        }
        spotWanted = wanted;
        if (spot == spotWanted) {
            spotIn = Math.min(1, spotIn + delta * SPOT_RATE);
        } else {
            spotIn = Math.max(0, spotIn - delta * SPOT_RATE);
            if (spotIn <= 0) {
                spot = spotWanted;
            }
        }
        territory.spotlight(spot, spotIn * spotIn * (3 - 2 * spotIn));
        adminIn = MathUtils.clamp(adminIn + (adminShown ? delta : -delta) * SPOT_RATE / 2, 0, 1);
        territory.admin(adminIn * adminIn * (3 - 2 * adminIn));
    }

    private void hover(int at) {
        if (at != hovered) {
            hovered = at;
            listener.hovered(star(at));
        }
    }

    private void choose(int at) {
        chosen = at;
        listener.chosen(star(at));
    }

    private void flyTo(StarMap.Star star) {
        world(star.x(), star.y(), flight);
        flying = true;
        posing = false;
    }

    /** Moves the camera {@code delta} seconds further on its flight. */
    private void fly(float delta) {
        float step = 1 - (float) Math.exp(-delta * 6);
        target.lerp(flight, step);
        boolean there = target.dst2(flight) < 0.01f;
        if (posing) {
            float turn = ((yaw % 360) + 540) % 360 - 180;
            yaw = turn + (0 - turn) * step;
            pitch = MathUtils.lerp(pitch, 90, step);
            distance = (float) Math.exp(MathUtils.lerp((float) Math.log(distance), (float) Math.log(START_DISTANCE),
                  step));
            there &= Math.abs(yaw) < 0.05f && 90 - pitch < 0.05f && Math.abs(distance - START_DISTANCE) < 0.5f;
        }
        if (there) {
            if (posing) {
                snapToStart();
            } else {
                target.set(flight);
                flying = false;
            }
        }
        aim();
    }

    private void zoom(float amount, int screenX, int screenY) {
        wantedDistance = Float.NaN;
        boolean anchored = ground(screenX, screenY, grab);
        distance = MathUtils.clamp(distance * (1 + amount * 0.12f), MIN_DISTANCE, MAX_DISTANCE);
        aim();
        // The point under the cursor stays there
        if (anchored && !flying && ground(screenX, screenY, ground)) {
            target.add(grab).sub(ground);
            clampTarget();
            aim();
        }
    }

    private void clampTarget() {
        target.x = MathUtils.clamp(target.x, -WORLD_EDGE, WORLD_EDGE);
        target.z = MathUtils.clamp(target.z, -WORLD_EDGE, WORLD_EDGE);
        target.y = 0;
    }

    /** Points the camera at {@link #target} from {@link #distance} at the view's turn and tilt. */
    private void aim() {
        if (camera == null) {
            return;
        }
        float cosPitch = MathUtils.cosDeg(pitch);
        camera.position.set(MathUtils.sinDeg(yaw) * cosPitch, MathUtils.sinDeg(pitch), MathUtils.cosDeg(yaw) * cosPitch)
              .scl(distance).add(target);
        // Up is the way the camera faces over the plane, which stays defined looking straight down
        camera.up.set(-MathUtils.sinDeg(yaw), 0, -MathUtils.cosDeg(yaw));
        camera.lookAt(target);
        camera.near = Math.max(0.5f, distance * 0.02f);
        camera.update();
    }

    /** The point on the galactic plane under the input coordinates, into {@code out}; false when there is none. */
    private boolean ground(int screenX, int screenY, Vector3 out) {
        if (camera == null) {
            return false;
        }
        Ray ray = camera.getPickRay(screenX, screenY);
        return Intersector.intersectRayPlane(ray, plane, out);
    }

    /** Projects every system onto the screen for picking and labels. */
    private void project() {
        List<StarMap.Star> all = shown;
        if (all.size() * 2 != screen.length) {
            return;
        }
        float[] m = camera.combined.val;
        float width = camera.viewportWidth;
        float height = camera.viewportHeight;
        for (int i = 0; i < all.size(); i++) {
            StarMap.Star star = all.get(i);
            float x = (float) star.x();
            float z = (float) -star.y();
            float w = m[Matrix4.M30] * x + m[Matrix4.M32] * z + m[Matrix4.M33];
            if (w <= camera.near || !emptyShown && !star.settled() && i != current && i != chosen) {
                screen[i * 2] = Float.NaN;
                screen[i * 2 + 1] = Float.NaN;
                continue;
            }
            float clipX = (m[Matrix4.M00] * x + m[Matrix4.M02] * z + m[Matrix4.M03]) / w;
            float clipY = (m[Matrix4.M10] * x + m[Matrix4.M12] * z + m[Matrix4.M13]) / w;
            screen[i * 2] = (clipX + 1) / 2 * width;
            screen[i * 2 + 1] = (1 - clipY) / 2 * height;
        }
    }

    /** Fills {@link #rings} with this frame's markers; returns how many. */
    private int rings() {
        FloatArray vertices = new FloatArray(POINT_FLOATS * 8);
        StarMap today = map;
        if (today != null) {
            for (String id : routeShown ? today.route() : List.<String>of()) {
                Integer at = index.get(id);
                if (at != null && at != current) {
                    ring(vertices, star(at), AMBER, 0.55f, 12, 0);
                }
            }
            for (Map.Entry<String, Integer> mark : today.marks().entrySet()) {
                StarMap.Star star = star(index.getOrDefault(mark.getKey(), -1));
                int what = mark.getValue();
                if ((what & StarMap.BASE) != 0) {
                    ring(vertices, star, BASE_COLOUR, 1.1f, 18, SQUARE);
                }
                if ((what & StarMap.SCENARIO) != 0) {
                    ring(vertices, star, BATTLE_COLOUR, 1.5f, 26, DIAMOND + 1);
                } else if ((what & StarMap.MISSION) != 0) {
                    ring(vertices, star, MISSION_COLOUR, 1.2f, 24, DIAMOND);
                }
            }
        }
        ring(vertices, star(current), CYAN, 1.2f, 30, 1);
        if (shipShown && routeShown) {
            float scale = Gdx.graphics.getBackBufferHeight() / (float) Math.max(1, Gdx.graphics.getHeight());
            vertices.addAll(ship.x, 0, ship.z, WHITE.x * 1.3f, WHITE.y * 1.3f, WHITE.z * 1.3f, 1, 14 * scale, 1);
        }
        ring(vertices, star(chosen), AMBER, 1.4f, 34, 0);
        if (hovered != chosen) {
            ring(vertices, star(hovered), WHITE, 0.7f, 24, 0);
        }
        int count = Math.min(vertices.size / POINT_FLOATS, rings.getMaxVertices());
        if (count > 0) {
            rings.setVertices(vertices.items, 0, count * POINT_FLOATS);
        }
        return count;
    }

    private void ring(FloatArray vertices, @Nullable StarMap.Star star, Vector3 colour, float bright, float pixels,
          float pulse) {
        if (star == null) {
            return;
        }
        float scale = Gdx.graphics.getBackBufferHeight() / (float) Math.max(1, Gdx.graphics.getHeight());
        vertices.addAll((float) star.x(), 0, (float) -star.y(), colour.x * bright, colour.y * bright,
              colour.z * bright, 1, pixels * scale, pulse);
    }

    /** The light of the systems {@code all}: in their owners' colours, or their levels' through {@code lens}. */
    private static @Nullable Mesh starMesh(List<StarMap.Star> all, @Nullable MapLens lens, boolean empty) {
        if (all.isEmpty()) {
            return null;
        }
        float[] vertices = new float[all.size() * POINT_FLOATS];
        int at = 0;
        for (StarMap.Star star : all) {
            float bright = star.settled() ? 1.6f : 0.3f;
            int rgb = star.settled() ? star.rgb() : 0x8A9099;
            float size = star.settled() ? 1.0f : 0.65f;
            if (lens != null) {
                int level = lens.level(star.lenses());
                rgb = level > 0 ? lens.rgb(level) : 0x5A6070;
                bright = level > 0 ? 2.2f : 0.14f;
                // Rated systems glow wide at the columns' feet, the best the widest
                size = level > 0 ? 1.3f + 1.6f * level / lens.levels() : 0.5f;
            }
            if (!empty && !star.settled()) {
                size = 0;
                bright = 0;
            }
            vertices[at++] = (float) star.x();
            vertices[at++] = 0;
            vertices[at++] = (float) -star.y();
            vertices[at++] = linear((rgb >> 16) & 0xFF) * bright;
            vertices[at++] = linear((rgb >> 8) & 0xFF) * bright;
            vertices[at++] = linear(rgb & 0xFF) * bright;
            vertices[at++] = 1;
            vertices[at++] = size;
            vertices[at++] = HoloTableView.hash(star.x(), star.y());
        }
        Mesh mesh = new Mesh(true, all.size(), 0, pointAttributes());
        mesh.setVertices(vertices);
        return mesh;
    }

    /**
     * Raises a column from each system of {@code all} that {@code lens} rates, at most a light year tall so the
     * strokes' lift sets their height: the higher the level, the taller, steeply so the best stand out. Each column
     * brightens from its foot to a glowing cap.
     */
    private static void pillars(Strokes into, List<StarMap.Star> all, MapLens lens) {
        for (StarMap.Star star : all) {
            int level = lens.level(star.lenses());
            if (level <= 0) {
                continue;
            }
            int rgb = lens.rgb(level);
            float r = linear((rgb >> 16) & 0xFF);
            float g = linear((rgb >> 8) & 0xFF);
            float b = linear(rgb & 0xFF);
            float x = (float) star.x();
            float z = (float) -star.y();
            float share = (float) level / lens.levels();
            float top = 0.08f + 0.92f * share * share;
            float width = 7 + 5 * share;
            into.add(x, 0, z, x, top * 0.5f, z, r * 0.45f, g * 0.45f, b * 0.45f, 1, width, 0, 0);
            into.add(x, top * 0.5f, z, x, top, z, r * 0.9f, g * 0.9f, b * 0.9f, 1, width + 1, 0, 0);
            float cap = 0.014f;
            into.add(x, top - cap, z, x, top + cap, z, 0.5f + r, 0.5f + g, 0.5f + b, 1, width * 2, 0, 0);
        }
    }

    /** How tall, in light years, the lens's highest pillars stand: taller the further out the camera is. */
    private float pillarHeight() {
        return MathUtils.clamp(distance * 0.05f, 6, 90);
    }

    /** The force's jump range around its system. */
    private @Nullable Mesh lineMesh() {
        FloatArray vertices = new FloatArray();
        StarMap.Star here = star(current);
        if (here != null) {
            int segments = 96;
            float radius = MHQConstants.MAX_JUMP_RADIUS;
            for (int i = 0; i < segments; i++) {
                for (int end = 0; end < 2; end++) {
                    float angle = MathUtils.PI2 * (i + end) / segments;
                    vertices.addAll((float) here.x() + MathUtils.cos(angle) * radius, 0,
                          (float) -here.y() + MathUtils.sin(angle) * radius, CYAN.x * 0.35f, CYAN.y * 0.35f,
                          CYAN.z * 0.35f, 1);
                }
            }
        }
        if (vertices.size == 0) {
            return null;
        }
        Mesh mesh = new Mesh(true, vertices.size / LINE_FLOATS, 0, lineAttributes());
        mesh.setVertices(vertices.toArray());
        return mesh;
    }

    /**
     * A route through the systems {@code ids}, star to star in {@code colour} at {@code bright}, each vertex carrying
     * how many light years along it it lies; null when it has no leg.
     */
    private @Nullable Mesh routeMesh(List<String> ids, Vector3 colour, float bright) {
        FloatArray vertices = new FloatArray();
        StarMap.Star previous = null;
        float along = 0;
        for (String id : ids) {
            Integer at = index.get(id);
            StarMap.Star next = at == null ? null : star(at);
            if (next == null) {
                continue;
            }
            if (previous != null) {
                float leg = (float) Math.hypot(next.x() - previous.x(), next.y() - previous.y());
                vertices.addAll((float) previous.x(), 0, (float) -previous.y(), colour.x * bright,
                      colour.y * bright, colour.z * bright, 1, along, 0);
                along += leg;
                vertices.addAll((float) next.x(), 0, (float) -next.y(), colour.x * bright, colour.y * bright,
                      colour.z * bright, 1, along, 0);
            }
            previous = next;
        }
        if (vertices.size == 0) {
            return null;
        }
        Mesh mesh = new Mesh(true, vertices.size / POINT_FLOATS, 0, pointAttributes());
        mesh.setVertices(vertices.toArray());
        return mesh;
    }

    /** Places the force between the first two systems of its route as far as its voyage has come. */
    private void voyage(StarMap shown) {
        travelled = 0;
        shipShown = false;
        StarMap.Voyage voyage = shown.voyage();
        List<String> ids = shown.route();
        if (voyage == null || voyage.stage() == StarMap.Stage.INBOUND || ids.size() < 2) {
            return;
        }
        StarMap.Star from = star(index.getOrDefault(ids.get(0), -1));
        StarMap.Star to = star(index.getOrDefault(ids.get(1), -1));
        if (from == null || to == null) {
            return;
        }
        float leg = (float) voyage.leg();
        travelled = leg * (float) Math.hypot(to.x() - from.x(), to.y() - from.y());
        ship.set(MathUtils.lerp((float) from.x(), (float) to.x(), leg), 0,
              -MathUtils.lerp((float) from.y(), (float) to.y(), leg));
        shipShown = true;
    }

    /** A faint square grid every {@link #GRID_STEP} light years, brighter every tenth line. */
    private static Mesh grid() {
        FloatArray vertices = new FloatArray();
        int lines = (int) (GRID_EXTENT / GRID_STEP);
        for (int i = -lines; i <= lines; i++) {
            float at = i * GRID_STEP;
            float shade = i % 10 == 0 ? 0.05f : 0.022f;
            float r = shade * 0.5f;
            float g = shade * 0.85f;
            vertices.addAll(at, 0, -GRID_EXTENT, r, g, shade, 1);
            vertices.addAll(at, 0, GRID_EXTENT, r, g, shade, 1);
            vertices.addAll(-GRID_EXTENT, 0, at, r, g, shade, 1);
            vertices.addAll(GRID_EXTENT, 0, at, r, g, shade, 1);
        }
        Mesh mesh = new Mesh(true, vertices.size / LINE_FLOATS, 0, lineAttributes());
        mesh.setVertices(vertices.toArray());
        return mesh;
    }

    private static VertexAttributes pointAttributes() {
        return new VertexAttributes(VertexAttribute.Position(), VertexAttribute.ColorUnpacked(),
              VertexAttribute.TexCoords(0));
    }

    private static VertexAttributes lineAttributes() {
        return new VertexAttributes(VertexAttribute.Position(), VertexAttribute.ColorUnpacked());
    }

    private static float linear(int channel) {
        return (float) Math.pow(channel / 255.0, 2.2);
    }
}
