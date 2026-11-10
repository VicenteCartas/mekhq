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

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import jakarta.annotation.Nullable;
import mekhq.MHQConstants;
import mekhq.gui.model.view.StarMap;

/**
 * The campaign's star map, full screen: the systems laid out on the galactic plane (coreward away from the camera),
 * glowing in their owners' colours. It opens looking straight down with coreward up, as the holotable's dive ends,
 * and can be tilted from there. The force's system wears a pulsing ring with its jump
 * range around it, its route is drawn star to star, and the system under the cursor or chosen is ringed too.
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
    }

    /** Takes the names to write over the map, the most important first; x and y are input coordinates. */
    interface Labels {
        void label(StarMap.Star star, float x, float y, boolean marked);
    }

    /** The holotable's, so its dive ends on the very view the map opens with. */
    private static final float FIELD_OF_VIEW = HoloTableView.FIELD_OF_VIEW;
    private static final float MIN_DISTANCE = 18;
    private static final float MAX_DISTANCE = 3200;
    /** How far over the plane the map opens, straight down, in light years. */
    static final float START_DISTANCE = 650;
    private static final float MIN_PITCH = 20;
    /** Systems are named once the camera is this near, in light years. */
    private static final float LABEL_DISTANCE = 700;
    private static final int MAX_LABELS = 240;
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
    private PerspectiveCamera camera;
    private ShaderProgram starShader;
    private ShaderProgram ringShader;
    private ShaderProgram lineShader;
    private @Nullable Mesh stars;
    private @Nullable Mesh lines;
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
            grabbed = button == Input.Buttons.LEFT && ground(screenX, screenY, grab);
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
            flying = false;
            posing = false;
            if (pressButton == Input.Buttons.RIGHT) {
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
            if (!dragged && button == Input.Buttons.LEFT && button == pressButton) {
                int picked = nearest(screen, screenX, screenY, PICK_RADIUS);
                choose(picked);
            }
            pressButton = -1;
            return true;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            hover(nearest(screen, screenX, screenY, PICK_RADIUS));
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

    /** Whether the view has a map to show yet. */
    boolean loaded() {
        return map != null || pending != null;
    }

    /** The system chosen, or null. */
    @Nullable StarMap.Star chosen() {
        return star(chosen);
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
        target.set(start);
        distance = START_DISTANCE;
        yaw = 0;
        pitch = 90;
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

    /** The systems shown, in the map's order; empty before it loads. */
    List<StarMap.Star> stars() {
        StarMap shown = map;
        return shown == null ? List.of() : shown.stars();
    }

    /** The factions with the most systems on screen, at most {@code limit} of them. */
    List<Legend> legend(int limit) {
        return legend(stars(), screen, camera == null ? 0 : camera.viewportWidth,
              camera == null ? 0 : camera.viewportHeight, limit);
    }

    void create() {
        starShader = HoloTableView.compile(StarMapShaders.STAR_VERTEX, HoloShaders.STAR_FRAGMENT);
        ringShader = HoloTableView.compile(StarMapShaders.RING_VERTEX, StarMapShaders.RING_FRAGMENT);
        lineShader = HoloTableView.compile(StarMapShaders.LINE_VERTEX, StarMapShaders.LINE_FRAGMENT);
        grid = grid();
        rings = new Mesh(false, 64, 0, pointAttributes());
        camera = new PerspectiveCamera(FIELD_OF_VIEW, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 1;
        camera.far = MAX_DISTANCE * 3;
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
        if (flying) {
            fly(delta);
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

    /** Hands {@code labels} the names to write this frame, the marked systems first, then the nearest the centre. */
    void labels(Labels labels) {
        StarMap shown = map;
        if (shown == null) {
            return;
        }
        List<StarMap.Star> all = shown.stars();
        boolean[] done = new boolean[all.size()];
        for (int marked : new int[] { chosen, hovered, current }) {
            if (marked >= 0 && !done[marked] && !Float.isNaN(screen[marked * 2])) {
                done[marked] = true;
                labels.label(all.get(marked), screen[marked * 2], screen[marked * 2 + 1], true);
            }
        }
        for (String id : shown.route()) {
            Integer at = index.get(id);
            if (at != null && !done[at] && !Float.isNaN(screen[at * 2])) {
                done[at] = true;
                labels.label(all.get(at), screen[at * 2], screen[at * 2 + 1], true);
            }
        }
        if (distance > LABEL_DISTANCE) {
            return;
        }
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        long[] order = new long[all.size()];
        int count = 0;
        for (int i = 0; i < all.size(); i++) {
            float x = screen[i * 2];
            float y = screen[i * 2 + 1];
            if (done[i] || Float.isNaN(x) || x < 0 || y < 0 || x > width || y > height || !all.get(i).settled()) {
                continue;
            }
            float dx = x - width / 2;
            float dy = y - height / 2;
            order[count++] = ((long) (dx * dx + dy * dy) << 32) | i;
        }
        Arrays.sort(order, 0, count);
        for (int n = 0; n < Math.min(count, MAX_LABELS); n++) {
            int i = (int) order[n];
            labels.label(all.get(i), screen[i * 2], screen[i * 2 + 1], false);
        }
    }

    void dispose() {
        for (Disposable resource : new Disposable[] { starShader, ringShader, lineShader, stars, lines, grid,
              rings }) {
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
        Arrays.fill(screen, Float.NaN);
        current = changed.current() == null ? -1 : index.getOrDefault(changed.current(), -1);
        chosen = wasChosen == null ? -1 : index.getOrDefault(wasChosen.id(), -1);
        hovered = -1;
        if (stars != null) {
            stars.dispose();
        }
        stars = starMesh(all);
        if (lines != null) {
            lines.dispose();
        }
        lines = lineMesh(changed);
        if (first) {
            StarMap.Star here = star(current);
            if (!startSet && here != null) {
                world(here.x(), here.y(), start);
            }
            snapToStart();
        }
    }

    private @Nullable StarMap.Star star(int at) {
        StarMap shown = map;
        return shown == null || at < 0 || at >= shown.stars().size() ? null : shown.stars().get(at);
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
        StarMap shown = map;
        if (shown == null) {
            return;
        }
        float[] m = camera.combined.val;
        float width = camera.viewportWidth;
        float height = camera.viewportHeight;
        List<StarMap.Star> all = shown.stars();
        for (int i = 0; i < all.size(); i++) {
            StarMap.Star star = all.get(i);
            float x = (float) star.x();
            float z = (float) -star.y();
            float w = m[Matrix4.M30] * x + m[Matrix4.M32] * z + m[Matrix4.M33];
            if (w <= camera.near) {
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
        StarMap shown = map;
        if (shown != null) {
            for (String id : shown.route()) {
                Integer at = index.get(id);
                if (at != null && at != current) {
                    ring(vertices, star(at), AMBER, 0.55f, 12, 0);
                }
            }
        }
        ring(vertices, star(current), CYAN, 1.2f, 30, 1);
        ring(vertices, star(chosen), AMBER, 1.4f, 34, 0);
        if (hovered != chosen) {
            ring(vertices, star(hovered), new Vector3(1, 1, 1), 0.7f, 24, 0);
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

    private static @Nullable Mesh starMesh(List<StarMap.Star> all) {
        if (all.isEmpty()) {
            return null;
        }
        float[] vertices = new float[all.size() * POINT_FLOATS];
        int at = 0;
        for (StarMap.Star star : all) {
            float bright = star.settled() ? 1.6f : 0.3f;
            int rgb = star.settled() ? star.rgb() : 0x8A9099;
            vertices[at++] = (float) star.x();
            vertices[at++] = 0;
            vertices[at++] = (float) -star.y();
            vertices[at++] = linear((rgb >> 16) & 0xFF) * bright;
            vertices[at++] = linear((rgb >> 8) & 0xFF) * bright;
            vertices[at++] = linear(rgb & 0xFF) * bright;
            vertices[at++] = 1;
            vertices[at++] = star.settled() ? 1.0f : 0.65f;
            vertices[at++] = HoloTableView.hash(star.x(), star.y());
        }
        Mesh mesh = new Mesh(true, all.size(), 0, pointAttributes());
        mesh.setVertices(vertices);
        return mesh;
    }

    /** The force's jump range around its system and its route, star to star. */
    private @Nullable Mesh lineMesh(StarMap shown) {
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
        StarMap.Star previous = null;
        for (String id : shown.route()) {
            Integer at = index.get(id);
            StarMap.Star next = at == null ? null : star(at);
            if (next == null) {
                continue;
            }
            if (previous != null) {
                for (StarMap.Star end : new StarMap.Star[] { previous, next }) {
                    vertices.addAll((float) end.x(), 0, (float) -end.y(), AMBER.x * 0.9f, AMBER.y * 0.9f,
                          AMBER.z * 0.9f, 1);
                }
            }
            previous = next;
        }
        if (vertices.size == 0) {
            return null;
        }
        Mesh mesh = new Mesh(true, vertices.size / LINE_FLOATS, 0, lineAttributes());
        mesh.setVertices(vertices.toArray());
        return mesh;
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
