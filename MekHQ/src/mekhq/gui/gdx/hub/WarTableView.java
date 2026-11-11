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

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.boardview.gpu.GpuStandee;
import megamek.logging.MMLogger;
import mekhq.gui.gdx.hub.WarTableAssets.Ground;
import mekhq.gui.gdx.hub.WarTableAssets.Kit;
import mekhq.gui.gdx.hub.WarTableSnapshot.Beacon;
import mekhq.gui.gdx.hub.WarTableSnapshot.Hex;
import mekhq.gui.gdx.hub.WarTableSnapshot.Link;
import mekhq.gui.gdx.hub.WarTableSnapshot.Mark;
import mekhq.gui.gdx.hub.WarTableSnapshot.Sector;
import mekhq.gui.gdx.hub.WarTableSnapshot.Site;
import mekhq.gui.gdx.hub.WarTableSnapshot.Spot;
import mekhq.gui.gdx.hub.WarTableSnapshot.Token;

/**
 * The command display's operations map of a sector: its ground in gentle relief ({@link WarTableGround}), scouted hexes
 * as the real thing (their surfaces blending into each other, roads, open water, city blocks, the small things that
 * grow or lie on them and the facilities' models), what nobody has scouted as a green hologram of its relief. On it
 * stand the player's forces as standees, their force icons cut out and extruded; over it the staff's tracking, the
 * supply lines flowing, the scenarios' beacons counting down, cut roads and points of interest. Plotted ({@link #plot}),
 * the map lies flat in a printed map's colours with nothing but the forces and the tracking on it.
 *
 * <p>Shown, the display draws the sector's hologram at once and a scan turns what's scouted into ground; dragging
 * turns the camera about the map, the wheel moves it in or out, and a click picks a hex ({@link #input}). The camera
 * can dive down onto the picked hex ({@link #render(float)}) for the board under it to take over.</p>
 *
 * <p>The table's space is y up, a hex {@link #radius} across its corners.</p>
 */
final class WarTableView implements Disposable {
    private static final MMLogger LOGGER = MMLogger.create(WarTableView.class);
    private static final float SQRT3 = (float) Math.sqrt(3);
    /** The plane the map lies on, its playing area's largest width and depth, in metres. */
    static final float TABLE_TOP = 0.86f;
    private static final float AREA_WIDTH = 1.9f;
    private static final float AREA_DEPTH = 1.15f;
    /** The colours of the player's side and the enemy's, and of the scenarios, linear. */
    private static final float[] ALLIED = { 0.25f, 0.75f, 1.0f };
    private static final float[] HOSTILE = { 1.0f, 0.22f, 0.12f };
    private static final float[] BATTLE = { 1.0f, 0.55f, 0.15f };
    private static final float[] POINT = { 0.65f, 0.4f, 1.0f };
    /** When the overlays come up, in seconds after a sector shows, at the earliest. */
    private static final float HOLO_AT = 1.5f;
    static final float FIELD_OF_VIEW = 40;
    /** How steeply the camera looks down on the map when a sector shows, in degrees. */
    private static final float DEFAULT_PITCH = 58;
    private static final float CLICK_SLOP = 6;
    /** How long the sector shows as a hologram alone, and the opening scan then takes to paint the ground in, in s. */
    private static final float INTRO_SECONDS = 0.35f;
    private static final float OPEN_SECONDS = 1.1f;
    /** How long the map takes to lie flat when plotted, or rise again, in seconds. */
    private static final float PLOT_SECONDS = 0.45f;
    /** How many hexes' radii a surface's picture covers on the ground. */
    private static final float SURFACE_TILE = 1.1f;
    /** How many hexes' radii a rock's texture covers, and what keys a scattered model's batch by its rock too. */
    private static final float ROCK_TILE = 0.3f;
    private static final String DETAIL = "#";
    /** How large the small things scattered over a hex are, and a facility's main model, in hexes' radii. */
    private static final float SCATTER_SIZE = 0.26f;
    private static final float FACILITY_SIZE = 1.05f;
    /** How long a newly scouted hex's fog takes to burn off, and how far apart neighbouring ones start, in seconds. */
    private static final float REVEAL_SECONDS = 1.6f;
    private static final float REVEAL_STAGGER = 0.09f;
    /** When hexes never scouted, and those scouted before the table last showed them, were scouted. */
    private static final float NEVER = 1e9f;
    private static final float ALWAYS = -1e9f;
    /** How long a new scenario's strike lasts, and how far apart several strike, in seconds. */
    private static final float STRIKE_SECONDS = 1.1f;
    private static final float STRIKE_GAP = 1.4f;
    /** How far into its strike a scenario's beam reaches the ground. */
    private static final float IMPACT = 0.14f;
    /** How long a scenario gone from the map takes to fade, in seconds. */
    private static final float FADE_SECONDS = 1.2f;
    /** How large a standee is, its longer side, and how thick, in hexes' radii. */
    private static final float STANDEE_SIZE = 1.25f;
    /**
     * A standee's hex base, across and tall, and the rod it stands on, as shares of its size; a facility's plate,
     * across and tall, in hexes' radii.
     */
    /**
     * The sun on the things standing on the ground: higher than the ground's ({@link WarTableGround#SUN}) and from the
     * front, so their faces turned to the camera don't go dark.
     */
    private static final Vector3 PROP_SUN = new Vector3(-0.35f, -1f, -0.45f).nor();
    /** The most forces fanned out on a hex; more stand as one standee on a stack of bases. */
    private static final int MOST_FANNED = 3;
    /** The most bases drawn in a stack. */
    private static final int MOST_LAYERS = 6;
    /** How far forward of a facility's hex's middle its forces stand, and how far back the facility then steps. */
    private static final float HELD_FRONT = 0.42f;
    private static final float HELD_BACK = 0.2f;
    /** How far apart the bases in a stack are, in bases' heights. */
    private static final float LAYER_STEP = 1.65f;
    private static final float BASE_ACROSS = 0.3f;
    private static final float BASE_TALL = 0.05f;
    private static final float ROD_ACROSS = 0.022f;
    private static final float ROD_TALL = 0.22f;
    private static final float PLATE_ACROSS = 0.66f;
    private static final float PLATE_TALL = 0.035f;

    /** How much of the camera's pitch a standee leans back by, for its picture to read from high up. */
    private static final float STANDEE_LEAN = 0.45f;
    private static final float STANDEE_DEPTH = 0.06f;
    /** How far above its hex a standee being set down or recalled travels, in hexes' radii. */
    private static final float STANDEE_TRAVEL = 0.9f;
    /** How long a force sent onto the map takes to settle onto its hex, and a recalled one to lift off, in seconds. */
    private static final float LAND_SECONDS = 0.5f;
    private static final float LIFT_SECONDS = 0.45f;
    /** How long a landing's ring takes to spread and fade, in seconds. */
    private static final float SHOCK_SECONDS = 0.7f;
    /** Holo floats per vertex: position, uv, colour and a_kind. */
    private static final int HOLO_STRIDE = 11;

    /** A scenario whose beacon strikes the map {@code delay} seconds from now, new since the table last showed it. */
    record Contact(Beacon beacon, float delay) {
    }

    /** A scenario's beacon striking the map, or fading off it, from {@code at} on the view's clock. */
    private record Change(Beacon beacon, float at) {
    }

    /** A ring of light spreading over the map from ({@code x}, {@code y}, {@code z}). */
    private static final class Shock {
        private final float x;
        private final float y;
        private final float z;
        private final float size;
        private final float[] colour;
        private final float seconds;
        /** 0 to 1 as it spreads; below 0 while it waits to start. */
        private float age;

        Shock(float x, float y, float z, float size, float[] colour, float seconds, float delay) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.size = size;
            this.colour = colour;
            this.seconds = seconds;
            age = -delay / seconds;
        }
    }

    /** A facility's plate under its models, on its side's colour. */
    private record Plate(float x, float y, float z, float[] colour) {
    }

    /** The copies of one of the models placed over the sector, merged, and what's mapped over its bare parts. */
    private record Batch(GlbProp mesh, @Nullable Texture detail) {
    }

    /** A force icon cut out and extruded: its faces, its walls, the picture and the colour its walls are painted. */
    private record Standee(Mesh face, Mesh back, Mesh walls, Texture art, float[] paint, float width, float height,
          float depth) {
    }

    /**
     * Where force {@code force}'s standee stands: the middle of its foot and how large it shows; {@code stack} the
     * stack it's in when its hex holds too many to fan out, -1 for none.
     */
    private record Figure(Standee standee, int force, float x, float y, float z, float scale, int stack) {
    }

    private final Consumer<Hex> picked;
    private PerspectiveCamera camera;
    private ShaderProgram terrainShader;
    private ShaderProgram fieldShader;
    private ShaderProgram overlayShader;
    private ShaderProgram holoShader;
    private ShaderProgram propShader;
    private final WarTableAssets assets = new WarTableAssets();
    /** The sector's relief and its meshes; null for none. */
    private @Nullable WarTableGround ground;
    private final List<Mesh> groundMeshes = new ArrayList<>();
    /** The surfaces the sector's hexes are painted with, as texture arrays (see WarTableAssets#surfaces). */
    private int[] surfaces = { 0, 0 };
    /** When the opening scan starts, on {@link #clock}. */
    private float readyAt;
    /** How far the map lies flat, plotted, 0 to 1, and whether it's going flat. */
    private float plot;
    private boolean plotting;
    private Mesh screen;
    /** A hexagonal prism a unit across its corners and tall, standing on its middle: bases and plates. */
    private Mesh prism;
    private final List<Plate> plates = new ArrayList<>();
    private final Matrix4 pedestal = new Matrix4();
    private final List<Batch> batches = new ArrayList<>();
    /** The models of the small things scattered over the scouted ground, merged by model. */
    private final List<Batch> scatter = new ArrayList<>();
    private final List<Standee> standees = new ArrayList<>();
    private final List<Figure> figures = new ArrayList<>();
    private final Matrix4 identity = new Matrix4();
    private final Matrix4 placed = new Matrix4();
    private final Vector3 corner3 = new Vector3();
    private @Nullable Mesh hologram;
    private @Nullable Sector sector;
    private float radius = 0.06f;
    private float originX;
    private float originZ;
    private float scanFrom;
    private float scanTo;
    private float[] tops = new float[0];
    private float time;
    /** Seconds since the sector showed. */
    private float clock;
    private float yaw;
    private float pitch = 52;
    private float distance = 2.15f;
    private final Vector3 target = new Vector3(0, TABLE_TOP, 0);
    private final Vector3 focus = new Vector3(0, TABLE_TOP, 0);
    private int hovered = -1;
    private int selected = -1;
    private int pressX;
    private int pressY;
    private boolean dragged;
    private boolean panning;
    /** The forces to set down onto the map when the next update shows them on it. */
    private final Set<Integer> expected = new HashSet<>();
    /** Seconds since each force being set down onto the map started down. */
    private final Map<Integer, Float> landing = new HashMap<>();
    /** Seconds since each force lifting off the map started up, and what to run once it's gone. */
    private final Map<Integer, Float> lifting = new HashMap<>();
    private final Map<Integer, Runnable> lifted = new HashMap<>();
    /** The forces gone off the map that the sector shown still has on it, until the next update. */
    private final Set<Integer> gone = new HashSet<>();
    /** The rings spreading from landings, strikes, reveals and facilities changing hands. */
    private final List<Shock> shocks = new ArrayList<>();
    /** When each hex of the sector shown was scouted, on {@link #clock}. */
    private float[] seenAt = new float[0];
    /** The scenarios new since the table last showed the sector, and those gone from it. */
    private final List<Change> arrivals = new ArrayList<>();
    private final List<Change> departures = new ArrayList<>();
    /** The texture of the sector's hexes the map reads its state from (see WarTableShaders), 0 for none. */
    private int cells;
    private int cellMinX;
    private int cellMinY;
    private int cellColumns;
    private int cellRows;
    /** The standee carried over the map, null for none; whether it was made for carrying, to go with it. */
    private @Nullable Standee carried;
    private boolean carriedMade;
    /** The force on the map being carried off it, hidden meanwhile; -1 for one from the reserve. */
    private int carriedForce = -1;
    /** The hex the carried standee is held over, whatever the pointer does; -1 to follow the pointer. */
    private int heldOver = -1;
    private @Nullable Mesh effects;
    private final FloatArray effect = new FloatArray();
    private final InputProcessor input = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            pressX = screenX;
            pressY = screenY;
            dragged = false;
            panning = button == Input.Buttons.RIGHT || button == Input.Buttons.MIDDLE;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (Math.abs(screenX - pressX) + Math.abs(screenY - pressY) > CLICK_SLOP) {
                dragged = true;
            }
            if (panning) {
                float step = distance * 0.0012f;
                float dx = -Gdx.input.getDeltaX() * step;
                float dz = -Gdx.input.getDeltaY() * step;
                float sin = MathUtils.sinDeg(yaw);
                float cos = MathUtils.cosDeg(yaw);
                focus.add(dx * cos + dz * sin, 0, -dx * sin + dz * cos);
                focus.x = MathUtils.clamp(focus.x, -AREA_WIDTH / 2, AREA_WIDTH / 2);
                focus.z = MathUtils.clamp(focus.z, -AREA_DEPTH / 2, AREA_DEPTH / 2);
            } else {
                yaw -= Gdx.input.getDeltaX() * 0.25f;
                pitch = MathUtils.clamp(pitch + Gdx.input.getDeltaY() * 0.2f, 18, 88);
            }
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (!dragged && button == Input.Buttons.LEFT) {
                int hex = pick(screenX, screenY);
                selected = hex;
                if (hex >= 0 && sector != null) {
                    picked.accept(sector.hexes().get(hex));
                }
            }
            return true;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            hovered = pick(screenX, screenY);
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            distance = MathUtils.clamp(distance * (1 + amountY * 0.08f), 0.45f, 4.5f);
            return true;
        }
    };

    /** A click on a hex hands it to {@code picked}. */
    WarTableView(Consumer<Hex> picked) {
        this.picked = picked;
    }

    void create() {
        terrainShader = HoloTableView.compile(WarTableShaders.TERRAIN_VERTEX, WarTableShaders.TERRAIN_FRAGMENT);
        fieldShader = HoloTableView.compile(WarTableShaders.SCREEN_VERTEX, WarTableShaders.FIELD_FRAGMENT);
        overlayShader = HoloTableView.compile(WarTableShaders.SCREEN_VERTEX, WarTableShaders.OVERLAY_FRAGMENT);
        holoShader = HoloTableView.compile(WarTableShaders.HOLO_VERTEX, WarTableShaders.HOLO_FRAGMENT);
        propShader = HoloTableView.compile(WarTableShaders.PROP_VERTEX, WarTableShaders.PROP_FRAGMENT);
        camera = new PerspectiveCamera(FIELD_OF_VIEW, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.02f;
        camera.far = 12;
        screen = new Mesh(true, 4, 6, VertexAttribute.Position());
        screen.setVertices(new float[] { -1, -1, 0, 1, -1, 0, 1, 1, 0, -1, 1, 0 });
        screen.setIndices(new short[] { 0, 1, 2, 2, 3, 0 });
        prism = prism();
    }

    /** A flat-topped hexagonal prism, its corners a unit from its axis, from y 0 to 1; its top and sides. */
    private static Mesh prism() {
        float[] vertices = new float[(1 + 6 + 24) * 8];
        short[] indices = new short[6 * 3 + 6 * 6];
        int v = 0;
        int i = 0;
        // The top: a fan round its middle
        v = vertex(vertices, v, 0, 1, 0, 0, 1, 0);
        for (int k = 0; k < 6; k++) {
            v = vertex(vertices, v, MathUtils.cosDeg(k * 60), 1, MathUtils.sinDeg(k * 60), 0, 1, 0);
        }
        for (int k = 0; k < 6; k++) {
            indices[i++] = 0;
            indices[i++] = (short) (1 + (k + 1) % 6);
            indices[i++] = (short) (1 + k);
        }
        for (int k = 0; k < 6; k++) {
            float nx = MathUtils.cosDeg(k * 60 + 30);
            float nz = MathUtils.sinDeg(k * 60 + 30);
            float ax = MathUtils.cosDeg(k * 60);
            float az = MathUtils.sinDeg(k * 60);
            float bx = MathUtils.cosDeg(k * 60 + 60);
            float bz = MathUtils.sinDeg(k * 60 + 60);
            short first = (short) (v / 8);
            v = vertex(vertices, v, ax, 0, az, nx, 0, nz);
            v = vertex(vertices, v, bx, 0, bz, nx, 0, nz);
            v = vertex(vertices, v, bx, 1, bz, nx, 0, nz);
            v = vertex(vertices, v, ax, 1, az, nx, 0, nz);
            indices[i++] = first;
            indices[i++] = (short) (first + 2);
            indices[i++] = (short) (first + 1);
            indices[i++] = first;
            indices[i++] = (short) (first + 3);
            indices[i++] = (short) (first + 2);
        }
        Mesh mesh = new Mesh(true, vertices.length / 8, indices.length, new VertexAttributes(
              VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0)));
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }

    private static int vertex(float[] into, int at, float x, float y, float z, float nx, float ny, float nz) {
        into[at] = x;
        into[at + 1] = y;
        into[at + 2] = z;
        into[at + 3] = nx;
        into[at + 4] = ny;
        into[at + 5] = nz;
        into[at + 6] = 0;
        into[at + 7] = 0;
        return at + 8;
    }

    /** The camera's controls, for the screen showing the view to put behind its own. */
    InputProcessor input() {
        return input;
    }

    void resize(int width, int height) {
        if (camera != null && width > 0 && height > 0) {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
        }
    }

    /**
     * Shows {@code shown}, the camera back over it all; its hologram shows, then a scan paints the ground in. What's
     * changed since {@code before}, the sector as the table last showed it (null for nothing), plays out once it's up:
     * the fog burns off hexes newly scouted and new scenarios strike, given back in the order they do.
     */
    List<Contact> show(Sector shown, @Nullable Sector before) {
        clock = 0;
        hovered = -1;
        selected = -1;
        focus.set(0, TABLE_TOP, 0);
        yaw = 0;
        pitch = DEFAULT_PITCH;
        distance = 2.15f;
        lifted.values().forEach(Runnable::run);
        lifted.clear();
        lifting.clear();
        landing.clear();
        shocks.clear();
        arrivals.clear();
        departures.clear();
        readyAt = INTRO_SECONDS;
        return build(shown, before, Map.of(), HOLO_AT + 0.6f);
    }

    /**
     * Shows {@code shown}, the sector on the map as it is now, in place: the camera and the hex picked stay, the
     * forces {@link #land expected} are set down onto it, and what's changed plays out at once, as {@link #show}'s
     * does.
     */
    List<Contact> update(Sector shown) {
        Hex picked = selected < 0 || sector == null ? null : sector.hexes().get(selected);
        clock = Math.max(clock, HOLO_AT + 1);
        readyAt = Math.min(readyAt, clock - OPEN_SECONDS);
        Map<Long, Float> scouted = new HashMap<>();
        if (sector != null) {
            for (int i = 0; i < sector.hexes().size() && i < seenAt.length; i++) {
                scouted.put(key(sector.hexes().get(i).x(), sector.hexes().get(i).y()), seenAt[i]);
            }
        }
        List<Contact> contacts = build(shown, sector, scouted, clock + 0.15f);
        selected = picked == null ? -1 : indexOf(picked.x(), picked.y());
        hovered = -1;
        gone.clear();
        return contacts;
    }

    private List<Contact> build(Sector shown, @Nullable Sector before, Map<Long, Float> scouted, float from) {
        Sector was = sector;
        sector = shown;
        long started = System.nanoTime();
        disposeMeshes();
        layOut(shown);
        if (ground != null) {
            groundMeshes.addAll(ground.meshes());
        }
        List<Contact> contacts = changes(shown, before, was, scouted, from);
        cells(shown);
        facilities(shown);
        scatter(shown);
        stand(shown);
        hologram = holoMesh(hologram(shown));
        LOGGER.debug("War table: sector of {} hexes built in {} ms", shown.hexes().size(),
              (System.nanoTime() - started) / 1_000_000);
        return contacts;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFF_FFFFL);
    }

    /**
     * Works out what's changed since {@code before}: when each hex was scouted, those newly scouted burning clear from
     * {@code from} on, spreading out from the middle of them, with a ring of light each; the scenarios new to it,
     * striking one after another, and those gone, fading; and the facilities that changed hands, flashing their new
     * owner's colour. Those still in play from {@code previous}, the sector shown until now, carry on.
     */
    private List<Contact> changes(Sector shown, @Nullable Sector before, @Nullable Sector previous,
          Map<Long, Float> scouted, float from) {
        List<Hex> hexes = shown.hexes();
        seenAt = new float[hexes.size()];
        List<Integer> revealed = new ArrayList<>();
        for (int i = 0; i < hexes.size(); i++) {
            Hex hex = hexes.get(i);
            Hex then = before == null ? null : before.hex(hex.x(), hex.y());
            Float earlier = scouted.get(key(hex.x(), hex.y()));
            if (!hex.scouted()) {
                seenAt[i] = NEVER;
            } else if (then != null && !then.scouted()) {
                revealed.add(i);
            } else {
                seenAt[i] = earlier != null && earlier < NEVER ? earlier : ALWAYS;
            }
        }
        float middleX = 0;
        float middleZ = 0;
        for (int i : revealed) {
            middleX += centreX(hexes.get(i).x()) / revealed.size();
            middleZ += centreZ(hexes.get(i).x(), hexes.get(i).y()) / revealed.size();
        }
        float fromX = middleX;
        float fromZ = middleZ;
        revealed.sort(Comparator.comparingDouble(i -> Math.hypot(centreX(hexes.get(i).x()) - fromX,
              centreZ(hexes.get(i).x(), hexes.get(i).y()) - fromZ)));
        for (int k = 0; k < revealed.size(); k++) {
            int i = revealed.get(k);
            Hex hex = hexes.get(i);
            seenAt[i] = from + k * REVEAL_STAGGER;
            shocks.add(new Shock(centreX(hex.x()), tops[i] + 0.004f, centreZ(hex.x(), hex.y()), radius * 1.1f,
                  ALLIED, 0.9f, seenAt[i] - clock));
        }
        List<Change> striking = new ArrayList<>();
        for (Change arrival : arrivals) {
            if (clock < arrival.at() + STRIKE_SECONDS && previous != null
                  && indexOfBeacon(shown.beacons(), arrival.beacon()) >= 0) {
                striking.add(arrival);
            }
        }
        arrivals.clear();
        arrivals.addAll(striking);
        departures.removeIf(departure -> clock >= departure.at() + FADE_SECONDS);
        List<Contact> contacts = new ArrayList<>();
        if (before == null) {
            return contacts;
        }
        float strike = Math.max(from + (revealed.isEmpty() ? 0 : 0.5f), arrivals.stream()
              .map(arrival -> arrival.at() + STRIKE_GAP).max(Float::compare).orElse(0f));
        for (Beacon beacon : shown.beacons()) {
            if (indexOfBeacon(before.beacons(), beacon) < 0) {
                arrivals.add(new Change(beacon, strike));
                contacts.add(new Contact(beacon, strike - clock));
                strike += STRIKE_GAP;
            }
        }
        for (Beacon beacon : before.beacons()) {
            if (indexOfBeacon(shown.beacons(), beacon) < 0) {
                departures.add(new Change(beacon, from));
            }
        }
        for (Site site : shown.sites()) {
            for (Site then : before.sites()) {
                if (then.x() == site.x() && then.y() == site.y() && then.allied() != site.allied()) {
                    float y = topOf(site.x(), site.y()) + 0.004f;
                    for (int ring = 0; ring < 2; ring++) {
                        shocks.add(new Shock(centreX(site.x()), y, centreZ(site.x(), site.y()), radius * 2.2f,
                              site.allied() ? ALLIED : HOSTILE, 1.1f, from - clock + ring * 0.35f));
                    }
                }
            }
        }
        return contacts;
    }

    private static int indexOfBeacon(List<Beacon> beacons, Beacon beacon) {
        for (int i = 0; i < beacons.size(); i++) {
            Beacon other = beacons.get(i);
            if (other.x() == beacon.x() && other.y() == beacon.y() && other.name().equals(beacon.name())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Fills the texture the map reads the sector's hexes from: one texel each, with when it was scouted, its flags
     * (1 water, 2 road, 4 city), 1 + its plotted kind (0 for no hex) and its surface's layer; loads the surfaces.
     */
    private void cells(Sector shown) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Hex hex : shown.hexes()) {
            minX = Math.min(minX, hex.x());
            minY = Math.min(minY, hex.y());
            maxX = Math.max(maxX, hex.x());
            maxY = Math.max(maxY, hex.y());
        }
        if (minX > maxX) {
            minX = minY = maxX = maxY = 0;
        }
        cellMinX = minX;
        cellMinY = minY;
        cellColumns = maxX - minX + 1;
        cellRows = maxY - minY + 1;
        FloatBuffer data = BufferUtils.newFloatBuffer(cellColumns * cellRows * 4);
        for (int i = 0; i < cellColumns * cellRows * 4; i++) {
            data.put(i, 0);
        }
        List<Hex> hexes = shown.hexes();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < hexes.size(); i++) {
            Hex hex = hexes.get(i);
            Ground look = WarTableAssets.ground(hex);
            int layer = names.indexOf(look.surface());
            if (layer < 0) {
                layer = names.size();
                names.add(look.surface());
            }
            int flags = (look.water() ? 1 : 0) | (hex.road() ? 2 : 0)
                  | (hex.city() || look.plot() == WarTableAssets.PLOT_BUILT ? 4 : 0);
            int at = ((hex.y() - minY) * cellColumns + hex.x() - minX) * 4;
            data.put(at, seenAt[i]);
            data.put(at + 1, flags);
            data.put(at + 2, 1 + look.plot());
            data.put(at + 3, layer);
        }
        surfaces = assets.surfaces(names);
        if (cells == 0) {
            cells = Gdx.gl.glGenTexture();
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, cells);
        Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 4);
        Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, cellColumns, cellRows, 0, GL20.GL_RGBA,
              GL20.GL_FLOAT, data);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
    }

    /** Sets force {@code force} down onto its hex when an update next shows it on the map. */
    void land(int force) {
        expected.add(force);
    }

    /** Lifts force {@code force}'s standee off the map, then runs {@code done}. */
    void lift(int force, Runnable done) {
        if (figureOf(force) == null) {
            done.run();
            return;
        }
        lifting.put(force, 0f);
        lifted.put(force, done);
    }

    /**
     * Picks {@code icon}'s standee up to carry over the map: force {@code force}'s off the map, or, for -1, a new one
     * from the reserve, whose open hexes then light as targets.
     */
    void carry(@Nullable BufferedImage icon, int force) {
        drop();
        Figure figure = force < 0 ? null : figureOf(force);
        if (figure != null) {
            carried = figure.standee();
        } else if (icon != null) {
            carried = standee(icon, false);
            carriedMade = carried != null;
        }
        carriedForce = force;
    }

    /** Puts down what's carried, if anything; a force carried off the map lies back on its hex. */
    void drop() {
        if (carried != null && carriedMade) {
            disposeStandee(carried);
        }
        carried = null;
        carriedMade = false;
        carriedForce = -1;
        heldOver = -1;
    }

    /** Whether a standee is being carried. */
    boolean carrying() {
        return carried != null;
    }

    /** Holds the carried standee over {@code hex}, or lets it follow the pointer again when null. */
    void holdOver(@Nullable Hex hex) {
        heldOver = hex == null || sector == null ? -1 : sector.hexes().indexOf(hex);
    }

    /** Hovers no hex, as when the pointer is over a panel instead of the map. */
    void unpoint() {
        hovered = -1;
    }

    /** Hovers the hex under the pointer at ({@code screenX}, {@code screenY}) and gives it, or null for none. */
    @Nullable Hex point(int screenX, int screenY) {
        hovered = pick(screenX, screenY);
        return hovered();
    }

    /**
     * The force whose standee lies under the pointer at ({@code screenX}, {@code screenY}), the nearest if several
     * do; -1 for none.
     */
    int figureAt(int screenX, int screenY) {
        if (camera == null || !settled()) {
            return -1;
        }
        // Each standee's picture is boxed on the screen as it's drawn; the nearest box round the pointer wins
        float pointerY = Gdx.graphics.getHeight() - screenY;
        int best = -1;
        float nearest = Float.MAX_VALUE;
        for (Figure figure : figures) {
            if (gone.contains(figure.force()) || lifting.containsKey(figure.force())
                  || landing.containsKey(figure.force()) || !leads(figure)) {
                continue;
            }
            Standee standee = figure.standee();
            upright(standee, figure.x(), standY(figure) + lift(figure, layers(figure)), figure.z(), figure.scale());
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            float bottom = Float.MAX_VALUE;
            float top = -Float.MAX_VALUE;
            for (int corner = 0; corner < 4; corner++) {
                corner3.set((corner & 1) == 0 ? -standee.width() / 2 : standee.width() / 2,
                      (corner & 2) == 0 ? -standee.height() / 2 : standee.height() / 2, standee.depth())
                      .mul(placed);
                camera.project(corner3);
                left = Math.min(left, corner3.x);
                right = Math.max(right, corner3.x);
                bottom = Math.min(bottom, corner3.y);
                top = Math.max(top, corner3.y);
            }
            float distance = camera.position.dst2(figure.x(), figure.y(), figure.z());
            if (screenX >= left && screenX <= right && pointerY >= bottom && pointerY <= top && distance < nearest) {
                nearest = distance;
                best = figure.force();
            }
        }
        return best;
    }

    /** Where the middle of {@code force}'s standee shows, in input coordinates; null if it isn't on the map. */
    @Nullable Vector2 screenOf(int force) {
        Figure figure = figureOf(force);
        if (camera == null || figure == null) {
            return null;
        }
        Standee standee = figure.standee();
        upright(standee, figure.x(), standY(figure) + lift(figure, layers(figure)), figure.z(), figure.scale());
        corner3.set(0, 0, standee.depth()).mul(placed);
        camera.project(corner3);
        return new Vector2(corner3.x, Gdx.graphics.getHeight() - corner3.y);
    }

    private @Nullable Figure figureOf(int force) {
        for (Figure figure : figures) {
            if (figure.force() == force) {
                return figure;
            }
        }
        return null;
    }

    /** Whether {@code figure} is still on the map: not taken off it nor being carried. */
    private boolean present(Figure figure) {
        return !gone.contains(figure.force()) && figure.force() != carriedForce;
    }

    /** Whether {@code figure} shows: alone, or the first in its stack still on the map. */
    private boolean leads(Figure figure) {
        if (figure.stack() < 0) {
            return true;
        }
        for (Figure other : figures) {
            if (other.stack() == figure.stack() && present(other)) {
                return other == figure;
            }
        }
        return false;
    }

    /** How many bases {@code figure} stands on: one, or one for each force in its stack still on the map. */
    private int layers(Figure figure) {
        if (figure.stack() < 0) {
            return 1;
        }
        int count = 0;
        for (Figure other : figures) {
            if (other.stack() == figure.stack() && present(other)) {
                count++;
            }
        }
        return MathUtils.clamp(count, 1, MOST_LAYERS);
    }

    /** How far {@code layers} bases lift {@code figure}'s standee over one. */
    private float lift(Figure figure, int layers) {
        return (layers - 1) * figure.scale() * Math.max(figure.standee().width(), figure.standee().height()) * BASE_TALL
              * LAYER_STEP;
    }

    /** How high {@code figure}'s foot shows, coming down to the table as the map's plotted. */
    private float standY(Figure figure) {
        return MathUtils.lerp(figure.y(), TABLE_TOP, flatten());
    }

    /** Clears the map, leaving the display with no sector on it. */
    void clear() {
        sector = null;
        clock = 0;
        hovered = -1;
        selected = -1;
        disposeMeshes();
        ground = null;
        readyAt = 0;
    }

    @Nullable Sector sector() {
        return sector;
    }

    /** The hex under the cursor, or null. */
    @Nullable Hex hovered() {
        return hovered < 0 || sector == null ? null : sector.hexes().get(hovered);
    }

    /** Marks {@code hex} picked, or nothing when null, as a click on it would. */
    void select(@Nullable Hex hex) {
        selected = hex == null || sector == null ? -1 : sector.hexes().indexOf(hex);
    }

    /** Whether the sector's ground can be drawn; it's built as the sector shows, so always. */
    boolean ready() {
        return true;
    }

    /** Whether the sector's ground is painted in and its pieces are set, so its labels may show. */
    boolean settled() {
        return clock > HOLO_AT + 0.3f && opened() >= 1;
    }

    /** Skips the opening: the ground shows painted in. */
    void settle() {
        clock = Math.max(clock, HOLO_AT + 1);
        readyAt = Math.min(readyAt, clock - OPEN_SECONDS);
    }

    /** How far the opening scan has painted the ground in, 0 to 1. */
    private float opened() {
        return MathUtils.clamp((clock - readyAt) / OPEN_SECONDS, 0, 1);
    }

    /** Lays the map flat in a printed map's colours, with nothing but the forces and the tracking on it, or raises it. */
    void plot(boolean plotted) {
        plotting = plotted;
    }

    /** Whether the map is plotted, or going flat. */
    boolean plotted() {
        return plotting;
    }

    /** How far the map has gone flat, eased, 0 to 1. */
    private float flatten() {
        return Interpolation.smooth.apply(plot);
    }

    /** The centre of the top of the hex at ({@code x}, {@code y}) on the map, into {@code out}. */
    Vector3 top(int x, int y, Vector3 out) {
        return out.set(centreX(x), topOf(x, y), centreZ(x, y));
    }

    /** Projects {@code world} onto the screen, y up; z beyond 1 is behind the camera. */
    Vector3 project(Vector3 world) {
        return camera.project(world);
    }

    /** How large a hex shows at {@code world}, in pixels across. */
    float pixelsAcross(Vector3 world) {
        float far = camera.position.dst(world);
        return 2 * radius / far * Gdx.graphics.getBackBufferHeight() / (2 * MathUtils.tanDeg(FIELD_OF_VIEW / 2));
    }

    void render() {
        render(0);
    }

    /** Draws the map, the camera {@code dive} of the way from its orbit down onto the picked hex. */
    void render(float dive) {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        time += delta;
        clock += delta;
        advance(delta);
        target.lerp(focus, Math.min(1, delta * 8));
        Vector3 aim = new Vector3(target);
        float far = distance;
        float tilt = pitch;
        if (dive > 0 && selected >= 0 && sector != null) {
            Hex hex = sector.hexes().get(selected);
            Vector3 onto = top(hex.x(), hex.y(), new Vector3());
            float eased = Interpolation.pow2InInverse.apply(dive);
            aim.lerp(onto, eased);
            far = MathUtils.lerp(distance, radius * 2.2f, eased);
            tilt = MathUtils.lerp(pitch, 80, eased);
        }
        float cosPitch = MathUtils.cosDeg(tilt);
        camera.position.set(MathUtils.sinDeg(yaw) * cosPitch, MathUtils.sinDeg(tilt), MathUtils.cosDeg(yaw) * cosPitch)
              .scl(far).add(aim);
        camera.up.set(0, 1, 0);
        camera.lookAt(aim);
        camera.near = Math.min(0.02f, far * 0.1f);
        camera.far = far * 3 + 3;
        camera.update();

        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        plot = MathUtils.clamp(plot + (plotting ? delta : -delta) / PLOT_SECONDS, 0, 1);
        Gdx.gl.glViewport(0, 0, width, height);
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(false);
        drawField();
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        drawGround();

        float open = opened();
        float front = open >= 1 ? Float.MAX_VALUE : MathUtils.lerp(scanFrom, scanTo, open);
        drawProps(front);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        Gdx.gl.glDepthMask(false);
        holoShader.bind();
        holoShader.setUniformMatrix("u_projView", camera.combined);
        holoShader.setUniformf("u_camera", camera.position);
        holoShader.setUniformf("u_time", time);
        holoShader.setUniformf("u_sweep", 0f);
        if (hologram != null) {
            float settledAt = Math.max(HOLO_AT, readyAt + OPEN_SECONDS * 0.8f);
            holoShader.setUniformf("u_intensity", MathUtils.clamp((clock - settledAt) / 0.6f, 0, 1) * (1 - dive));
            hologram.render(holoShader, GL20.GL_TRIANGLES);
        }
        drawEffects();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        overlayShader.bind();
        set(overlayShader, "u_screen", width, height);
        screen.render(overlayShader, GL20.GL_TRIANGLES);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** The field behind the map: the display's depths, its grid fading off past the sector. */
    private void drawField() {
        fieldShader.bind();
        set(fieldShader, "u_inverse", camera.invProjectionView);
        set(fieldShader, "u_grid", originX, originZ, radius);
        set(fieldShader, "u_floor", TABLE_TOP - WarTableGround.SKIRT * radius);
        set(fieldShader, "u_time", time);
        screen.render(fieldShader, GL20.GL_TRIANGLES);
    }

    /** The sector's ground: scouted as the real thing, the rest a hologram of its relief. */
    private void drawGround() {
        if (sector == null || groundMeshes.isEmpty()) {
            return;
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
        Gdx.gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, surfaces[0]);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE2);
        Gdx.gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, surfaces[1]);
        terrainShader.bind();
        lights(terrainShader, WarTableGround.SUN, 1.75f);
        hexUniforms(terrainShader);
        set(terrainShader, "u_albedo", 1);
        set(terrainShader, "u_normals", 2);
        set(terrainShader, "u_tile", SURFACE_TILE);
        set(terrainShader, "u_plot", flatten());
        set(terrainShader, "u_flatten", flatten());
        set(terrainShader, "u_base", TABLE_TOP);
        Hex hover = hovered();
        Hex pick = selected < 0 ? null : sector.hexes().get(selected);
        set(terrainShader, "u_hover", hover == null ? -1e4f : hover.x(), hover == null ? -1e4f : hover.y());
        set(terrainShader, "u_selected", pick == null ? -1e4f : pick.x(), pick == null ? -1e4f : pick.y());
        for (Mesh mesh : groundMeshes) {
            mesh.render(terrainShader, GL20.GL_TRIANGLES);
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE2);
        Gdx.gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE1);
        Gdx.gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    /** The uniforms the map's shaders read the hexes by (WarTableShaders#HEX), the cells on texture unit 3. */
    private void hexUniforms(ShaderProgram shader) {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE3);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, cells);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        set(shader, "u_cells", 3);
        set(shader, "u_grid", originX, originZ, radius);
        set(shader, "u_cellBox", cellMinX, cellMinY, sector == null ? 0 : cellColumns, sector == null ? 0 : cellRows);
        set(shader, "u_tableTop", TABLE_TOP);
        set(shader, "u_clock", clock);
        set(shader, "u_time", time);
        set(shader, "u_reveal", REVEAL_SECONDS);
        set(shader, "u_open", opened());
        set(shader, "u_scan", scanFrom, scanTo);
    }

    /** Sets {@code shader}'s uniform {@code name} if it reads it: its compiler drops those it doesn't. */
    private static void set(ShaderProgram shader, String name, float... values) {
        if (!shader.hasUniform(name)) {
            return;
        }
        switch (values.length) {
            case 1 -> shader.setUniformf(name, values[0]);
            case 2 -> shader.setUniformf(name, values[0], values[1]);
            case 3 -> shader.setUniformf(name, values[0], values[1], values[2]);
            default -> shader.setUniformf(name, values[0], values[1], values[2], values[3]);
        }
    }

    private static void set(ShaderProgram shader, String name, int value) {
        if (shader.hasUniform(name)) {
            shader.setUniformi(name, value);
        }
    }

    private static void set(ShaderProgram shader, String name, Matrix4 value) {
        if (shader.hasUniform(name)) {
            shader.setUniformMatrix(name, value);
        }
    }

    /**
     * The facilities' models and the small things on the ground, shown once the opening scan has passed {@code front}
     * and gone while the map's plotted; then the forces' standees.
     */
    private void drawProps(float front) {
        if (batches.isEmpty() && plates.isEmpty() && scatter.isEmpty() && figures.isEmpty() && carried == null) {
            return;
        }
        propShader.bind();
        lights(propShader, PROP_SUN, 1.35f);
        hexUniforms(propShader);
        propShader.setUniformMatrix("u_model", identity);
        propShader.setUniformf("u_drop", 0f);
        set(propShader, "u_detailScale", 0f);
        set(propShader, "u_detail", 4);
        set(propShader, "u_front", front);
        set(propShader, "u_solid", 0f);
        if (plot < 0.5f) {
            Map<String, Float> glow = Map.of();
            for (Batch batch : batches) {
                batch.mesh().draw(propShader, glow);
            }
            prepareFlat();
            for (Plate plate : plates) {
                drawBase(plate.x(), plate.y() - radius * PLATE_TALL * 0.5f, plate.z(), radius * PLATE_ACROSS,
                      radius * PLATE_TALL * 1.5f, plate.colour(), 1);
            }
            for (Batch batch : scatter) {
                if (batch.detail() != null) {
                    batch.detail().bind(4);
                    set(propShader, "u_detailScale", 1 / (radius * ROCK_TILE));
                }
                batch.mesh().draw(propShader, glow);
                set(propShader, "u_detailScale", 0f);
            }
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        }
        set(propShader, "u_solid", 1f);
        propShader.setUniformf("u_cutoff", 0f);
        propShader.setUniformf("u_emissive", 0, 0, 0);
        propShader.setUniformf("u_metallic", 0f);
        propShader.setUniformf("u_roughness", 0.6f);
        propShader.setUniformi("u_base", 0);
        for (Figure figure : figures) {
            int force = figure.force();
            if (gone.contains(force) || force == carriedForce || !leads(figure)) {
                continue;
            }
            int layers = layers(figure);
            float y = standY(figure);
            Float landed = landing.get(force);
            Float leaving = lifting.get(force);
            if (landed != null) {
                y += radius * STANDEE_TRAVEL * (1 - Interpolation.pow3Out.apply(Math.min(1, landed / LAND_SECONDS)));
            } else if (leaving != null) {
                y += radius * STANDEE_TRAVEL * Interpolation.pow2In.apply(Math.min(1, leaving / LIFT_SECONDS));
            }
            drawPedestal(figure.standee(), figure.x(), y, figure.z(), figure.scale(), 1, layers);
            upright(figure.standee(), figure.x(), y + lift(figure, layers), figure.z(), figure.scale());
            drawStandee(figure.standee(), 1, 1, 1);
        }
        set(propShader, "u_front", Float.MAX_VALUE);
        drawCarried();
        propShader.setUniformMatrix("u_model", identity);
    }

    /** The standee being carried, held over the hex it's held or pointed over, tinted by whether it may go there. */
    private void drawCarried() {
        int over = heldOver >= 0 ? heldOver : hovered;
        if (carried == null || over < 0 || sector == null || over >= tops.length) {
            return;
        }
        Hex hex = sector.hexes().get(over);
        float scale = standeeScale(carried, 1);
        float y = topAt(over) + radius * (0.3f + 0.03f * MathUtils.sin(time * 2.2f));
        drawPedestal(carried, centreX(hex.x()), y, centreZ(hex.x(), hex.y()), scale, 0.8f, 1);
        upright(carried, centreX(hex.x()), y, centreZ(hex.x(), hex.y()), scale);
        if (carriedForce >= 0) {
            drawStandee(carried, 0.7f, 0.78f, 0.95f);
        } else if (hex.open()) {
            drawStandee(carried, 0.85f, 1.1f, 0.9f);
        } else {
            drawStandee(carried, 1.15f, 0.5f, 0.45f);
        }
    }

    /**
     * Stands {@code standee} upright with the middle of its foot at ({@code x}, {@code y}, {@code z}), {@code scale}
     * large, its picture turned to the camera.
     */
    private void upright(Standee standee, float x, float y, float z, float scale) {
        float size = scale * Math.max(standee.width(), standee.height());
        placed.setToTranslation(x, y + size * (BASE_TALL + ROD_TALL), z).rotate(Vector3.Y, yaw).rotate(Vector3.X, -pitch * STANDEE_LEAN)
              .scale(scale, scale, scale).translate(0, standee.height() / 2, -standee.depth() / 2);
    }

    /**
     * Draws the hex base on the player's side's colour and the rod that {@code standee}, {@code scale} large, stands on
     * at ({@code x}, {@code y}, {@code z}), {@code bright} as bright as it is; {@code layers} bases stacked, one for
     * each force in a stack.
     */
    private void drawPedestal(Standee standee, float x, float y, float z, float scale, float bright, int layers) {
        float size = scale * Math.max(standee.width(), standee.height());
        prepareFlat();
        for (int layer = 0; layer < layers; layer++) {
            drawBase(x, y - size * BASE_TALL * 0.4f + layer * size * BASE_TALL * LAYER_STEP, z,
                  size * BASE_ACROSS * (1 - layer * 0.04f), size * BASE_TALL * 1.4f, ALLIED, bright);
        }
        float lifted = y + (layers - 1) * size * BASE_TALL * LAYER_STEP;
        drawPrism(x, lifted + size * BASE_TALL * 0.9f, z, size * ROD_ACROSS, size * ROD_TALL, 0.12f, 0.13f, 0.14f, 0, 0,
              0);
        propShader.setUniformf("u_emissive", 0, 0, 0);
        propShader.setUniformf("u_metallic", 0f);
        propShader.setUniformf("u_roughness", 0.6f);
    }

    /** Sets the prop shader up for plain, untextured shapes: the bases and plates. */
    private void prepareFlat() {
        propShader.setUniformf("u_textured", 0f);
        propShader.setUniformf("u_cutoff", 0f);
        propShader.setUniformf("u_metallic", 0.4f);
        propShader.setUniformf("u_roughness", 0.35f);
        set(propShader, "u_detailScale", 0f);
    }

    /**
     * Draws a base of dark metal {@code across} wide and {@code tall}, its foot's middle at the point given, edged in a
     * thin glowing rim of {@code colour}.
     */
    private void drawBase(float x, float y, float z, float across, float tall, float[] colour, float bright) {
        float glow = 0.35f * bright;
        drawPrism(x, y, z, across, tall, colour[0] * 0.2f, colour[1] * 0.2f, colour[2] * 0.2f, colour[0] * glow,
              colour[1] * glow, colour[2] * glow);
        drawPrism(x, y, z, across * 0.88f, tall * 1.15f, 0.045f, 0.05f, 0.055f, 0, 0, 0);
    }

    /** Draws the prism {@code across} wide at its corners and {@code tall}, its foot's middle at the point given. */
    private void drawPrism(float x, float y, float z, float across, float tall, float r, float g, float b,
          float glowR, float glowG, float glowB) {
        pedestal.setToTranslation(x, y, z).scale(across, tall, across);
        propShader.setUniformMatrix("u_model", pedestal);
        propShader.setUniformf("u_baseFactor", r, g, b, 1);
        propShader.setUniformf("u_emissive", glowR, glowG, glowB);
        prism.render(propShader, GL20.GL_TRIANGLES);
    }

    /** Draws {@code standee} where {@link #lay} put it, its colours multiplied by ({@code r}, {@code g}, {@code b}). */
    private void drawStandee(Standee standee, float r, float g, float b) {
        propShader.setUniformMatrix("u_model", placed);
        propShader.setUniformf("u_textured", 1f);
        propShader.setUniformf("u_baseFactor", r, g, b, 1);
        standee.art().bind(0);
        standee.face().render(propShader, GL20.GL_TRIANGLES);
        standee.back().render(propShader, GL20.GL_TRIANGLES);
        float[] paint = standee.paint();
        propShader.setUniformf("u_textured", 0f);
        propShader.setUniformf("u_baseFactor", paint[0] * 0.6f * r, paint[1] * 0.6f * g, paint[2] * 0.6f * b, 1);
        standee.walls().render(propShader, GL20.GL_TRIANGLES);
    }

    /** How large {@code standee} shows, {@code share} of a hex's own when it shares one with others. */
    private float standeeScale(Standee standee, float share) {
        return radius * STANDEE_SIZE * share / Math.max(standee.width(), standee.height());
    }

    /** Moves the forces landing and leaving on, and the rings out. */
    private void advance(float delta) {
        for (Iterator<Map.Entry<Integer, Float>> entries = landing.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<Integer, Float> entry = entries.next();
            float after = entry.getValue() + delta;
            if (after >= LAND_SECONDS) {
                entries.remove();
                Figure figure = figureOf(entry.getKey());
                if (figure != null) {
                    shocks.add(new Shock(figure.x(), figure.y() + 0.004f, figure.z(), radius * 1.2f, ALLIED,
                          SHOCK_SECONDS, 0));
                }
            } else {
                entry.setValue(after);
            }
        }
        List<Runnable> done = new ArrayList<>();
        for (Iterator<Map.Entry<Integer, Float>> entries = lifting.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<Integer, Float> entry = entries.next();
            float after = entry.getValue() + delta;
            if (after >= LIFT_SECONDS) {
                entries.remove();
                gone.add(entry.getKey());
                Runnable then = lifted.remove(entry.getKey());
                if (then != null) {
                    done.add(then);
                }
            } else {
                entry.setValue(after);
            }
        }
        shocks.removeIf(shock -> (shock.age += delta / shock.seconds) >= 1);
        float before = clock - delta;
        for (Change arrival : arrivals) {
            float hit = arrival.at() + IMPACT * STRIKE_SECONDS;
            if (before < arrival.at() && clock >= arrival.at()) {
                glance(arrival.beacon());
            }
            if (before < hit && clock >= hit) {
                Beacon beacon = arrival.beacon();
                float x = centreX(beacon.x());
                float y = topOf(beacon.x(), beacon.y()) + 0.005f;
                float z = centreZ(beacon.x(), beacon.y());
                shocks.add(new Shock(x, y, z, radius * 2.4f, beacon.daysLeft() == 0 ? HOSTILE : BATTLE, 1.0f, 0));
            }
        }
        done.forEach(Runnable::run);
    }

    /** Turns the camera's aim towards {@code beacon} as it strikes, unless it's in plain view or the player's busy. */
    private void glance(Beacon beacon) {
        if (carried != null || dragged || camera == null) {
            return;
        }
        float x = centreX(beacon.x());
        float z = centreZ(beacon.x(), beacon.y());
        Vector3 seen = camera.project(new Vector3(x, topOf(beacon.x(), beacon.y()), z));
        float width = Gdx.graphics.getWidth();
        float height = Gdx.graphics.getHeight();
        if (seen.z < 1 && seen.x > width * 0.2f && seen.x < width * 0.8f && seen.y > height * 0.25f
              && seen.y < height * 0.8f) {
            return;
        }
        focus.set(MathUtils.clamp(x, -AREA_WIDTH / 2, AREA_WIDTH / 2), TABLE_TOP,
              MathUtils.clamp(z, -AREA_DEPTH / 2, AREA_DEPTH / 2));
    }

    /**
     * What lights the map from moment to moment, drawn additively: while a standee is carried, the hexes it may go
     * onto and the one it's over; and the rings.
     */
    private void drawEffects() {
        effect.clear();
        int over = heldOver >= 0 ? heldOver : hovered;
        if (carried != null && sector != null) {
            List<Hex> hexes = sector.hexes();
            for (int i = 0; i < hexes.size() && i < tops.length; i++) {
                Hex hex = hexes.get(i);
                boolean target = carriedForce < 0 && hex.open();
                if (!target && i != over) {
                    continue;
                }
                float[] colour = carriedForce >= 0 || hex.open() ? ALLIED : HOSTILE;
                flat(effect, centreX(hex.x()), tops[i] + 0.003f, centreZ(hex.x(), hex.y()), radius, colour,
                      i == over ? 1.1f : 0.45f, 6, i == over ? 1 : 0);
            }
        }
        for (Shock shock : shocks) {
            if (shock.age >= 0) {
                flat(effect, shock.x, shock.y, shock.z, shock.size, shock.colour, 0.9f, 7, shock.age);
            }
        }
        for (Change arrival : arrivals) {
            float t = (clock - arrival.at()) / STRIKE_SECONDS;
            if (t < 0) {
                continue;
            }
            Beacon beacon = arrival.beacon();
            float x = centreX(beacon.x());
            float y = topOf(beacon.x(), beacon.y());
            float z = centreZ(beacon.x(), beacon.y());
            if (t < 1) {
                quad(effect, x, y, z, -1, 0, x, y, z, 1, 0, x, y, z, 1, 1, x, y, z, -1, 1,
                      beacon.daysLeft() == 0 ? HOSTILE : BATTLE, 1.1f, 8, t);
            }
            beacon(effect, beacon, MathUtils.clamp((t - IMPACT) / 0.5f, 0, 1));
        }
        for (Change departure : departures) {
            float t = (clock - departure.at()) / FADE_SECONDS;
            if (t < 1) {
                beacon(effect, departure.beacon(), 1 - MathUtils.clamp(t, 0, 1));
            }
        }
        if (effect.size == 0) {
            return;
        }
        int count = effect.size / HOLO_STRIDE;
        if (effects == null || effects.getMaxVertices() < count) {
            if (effects != null) {
                effects.dispose();
            }
            effects = new Mesh(false, Math.max(count, 1024), 0, new VertexAttributes(VertexAttribute.Position(),
                  VertexAttribute.TexCoords(0), VertexAttribute.ColorUnpacked(),
                  new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_kind")));
        }
        effects.setVertices(effect.items, 0, effect.size);
        holoShader.setUniformf("u_intensity", 1f);
        effects.render(holoShader, GL20.GL_TRIANGLES, 0, count);
    }

    /**
     * The sun over the map, {@code sun} the way its light travels, {@code strength} as strong, with a cool fill from the
     * sky; no fog.
     */
    private void lights(ShaderProgram shader, Vector3 sun, float strength) {
        shader.setUniformMatrix("u_projView", camera.combined);
        shader.setUniformf("u_camera", camera.position);
        shader.setUniformf("u_keyDir", sun);
        shader.setUniformf("u_keyColor", strength, strength * 0.963f, strength * 0.889f);
        shader.setUniformf("u_rimDir", new Vector3(0.5f, -0.6f, 0.6f).nor());
        shader.setUniformf("u_rimColor", 0.22f, 0.25f, 0.32f);
        shader.setUniformf("u_lamp", 0, TABLE_TOP + 100, 0);
        shader.setUniformf("u_lampColor", 0, 0, 0);
        shader.setUniformf("u_fog", 0, 0, 0);
        shader.setUniformf("u_fogDensity", 0f);
    }

    /** The hex under the input coordinates: where the pick ray first meets the ground; -1 for none. */
    private int pick(int screenX, int screenY) {
        if (camera == null || sector == null || ground == null || !settled()) {
            return -1;
        }
        Ray ray = camera.getPickRay(screenX, screenY);
        if (ray.direction.y > -1e-4f) {
            return -1;
        }
        // Marched down from above the highest ground to below the lowest, then narrowed onto the surface
        float high = (TABLE_TOP + radius - ray.origin.y) / ray.direction.y;
        float low = (TABLE_TOP - radius * 0.3f - ray.origin.y) / ray.direction.y;
        float from = Math.max(0, high);
        if (low <= from) {
            return -1;
        }
        int steps = 96;
        float before = from;
        float hit = -1;
        for (int i = 1; i <= steps; i++) {
            float t = MathUtils.lerp(from, low, i / (float) steps);
            if (above(ray, t) <= 0) {
                float a = before;
                float b = t;
                for (int j = 0; j < 12; j++) {
                    float mid = (a + b) / 2;
                    if (above(ray, mid) > 0) {
                        a = mid;
                    } else {
                        b = mid;
                    }
                }
                hit = b;
                break;
            }
            before = t;
        }
        if (hit < 0) {
            return -1;
        }
        float x = ray.origin.x + ray.direction.x * hit;
        float z = ray.origin.z + ray.direction.z * hit;
        int best = -1;
        float nearest = radius * radius;
        List<Hex> hexes = sector.hexes();
        for (int i = 0; i < hexes.size(); i++) {
            Hex hex = hexes.get(i);
            float dx = x - centreX(hex.x());
            float dz = z - centreZ(hex.x(), hex.y());
            float distance = dx * dx + dz * dz;
            if (distance < nearest) {
                nearest = distance;
                best = i;
            }
        }
        return best;
    }

    /** How far above the ground, as it shows, the pick ray is {@code t} along it. */
    private float above(Ray ray, float t) {
        float x = ray.origin.x + ray.direction.x * t;
        float z = ray.origin.z + ray.direction.z * t;
        float surface = ground == null ? TABLE_TOP : MathUtils.lerp(ground.height(x, z), TABLE_TOP, flatten());
        return ray.origin.y + ray.direction.y * t - surface;
    }

    /**
     * Fits the sector to the map's playing area, centred: the hexes as large as both its width and depth allow; builds
     * its relief.
     */
    private void layOut(Sector shown) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Hex hex : shown.hexes()) {
            minX = Math.min(minX, hex.x());
            maxX = Math.max(maxX, hex.x());
            minY = Math.min(minY, hex.y());
            maxY = Math.max(maxY, hex.y());
        }
        if (minX > maxX) {
            minX = maxX = minY = maxY = 0;
        }
        int columns = maxX - minX + 1;
        int rows = maxY - minY + 1;
        float across = 1.5f * (columns - 1) + 2;
        float down = SQRT3 * (rows + 0.5f);
        radius = Math.min(AREA_WIDTH / across, AREA_DEPTH / down);
        originX = -(1.5f * (minX + maxX)) * radius / 2;
        originZ = -(SQRT3 * (minY + maxY + 0.5f)) * radius / 2;
        ground = new WarTableGround(shown, radius, originX, originZ, TABLE_TOP);
        tops = new float[shown.hexes().size()];
        for (int i = 0; i < tops.length; i++) {
            tops[i] = ground.top(i);
        }
        scanFrom = centreX(minX) - 1.2f * radius;
        scanTo = centreX(maxX) + 1.2f * radius;
    }

    private float centreX(int x) {
        return originX + 1.5f * radius * x;
    }

    private float centreZ(int x, int y) {
        return originZ + SQRT3 * radius * (y + ((x & 1) == 1 ? 0.5f : 0));
    }

    private int indexOf(int x, int y) {
        List<Hex> hexes = sector == null ? List.of() : sector.hexes();
        for (int i = 0; i < hexes.size(); i++) {
            if (hexes.get(i).x() == x && hexes.get(i).y() == y) {
                return i;
            }
        }
        return -1;
    }

    private float topOf(int x, int y) {
        return topAt(indexOf(x, y));
    }

    /** How high the middle of hex {@code index}'s ground shows, lying flatter as the map's plotted. */
    private float topAt(int index) {
        float top = index < 0 || index >= tops.length ? TABLE_TOP : tops[index];
        return MathUtils.lerp(top, TABLE_TOP, flatten());
    }

    /** The facilities' models on their hexes, more and the main one larger for a larger tier; copies merged. */
    private void facilities(Sector shown) {
        Map<String, List<Matrix4>> placements = new LinkedHashMap<>();
        for (Site site : shown.sites()) {
            facility(placements, site, occupied(shown, site.x(), site.y()));
        }
        int vertexCount = 0;
        for (Map.Entry<String, List<Matrix4>> entry : placements.entrySet()) {
            GlbProp prop = assets.prop(entry.getKey());
            if (prop != null) {
                GlbProp batch = prop.batch(entry.getValue());
                vertexCount += batch.vertexCount();
                batches.add(new Batch(batch, null));
            }
        }
        LOGGER.debug("War table: {} facility models, {} batches, {} vertices", placements.values().stream()
              .mapToInt(List::size).sum(), batches.size(), vertexCount);
    }

    /** A facility's main model in the middle of its hex and its outbuildings round it. */
    private void facility(Map<String, List<Matrix4>> placements, Site site, boolean held) {
        Kit kit = WarTableAssets.facility(site.type());
        int tier = site.tier() == null ? 1 : site.tier().ordinal() + 1;
        float r = radius;
        float cx = centreX(site.x());
        float ground = topOf(site.x(), site.y());
        plates.add(new Plate(cx, ground, centreZ(site.x(), site.y()), site.allied() ? ALLIED : HOSTILE));
        // With forces at the front of the plate, the facility steps back and shrinks to make room
        float cz = centreZ(site.x(), site.y()) - (held ? r * HELD_BACK : 0);
        float room = held ? 0.72f : 1;
        float floor = ground + r * PLATE_TALL;
        float turn = HoloTableView.hash(site.x() * 2.1, site.y() * 1.1) * 360;
        GlbProp main = assets.prop(kit.main());
        if (main != null) {
            float footprint = r * FACILITY_SIZE * room * (tier == 3 ? 0.78f : tier == 2 ? 0.7f : 0.62f);
            float scale = Math.min(footprint / Math.max(width(main), 1e-4f), r * 0.5f / height(main));
            placements.computeIfAbsent(kit.main(), ignored -> new ArrayList<>())
                  .add(place(main, cx, floor, cz, turn, scale));
        }
        for (int j = 0; j < (held ? 0 : Math.min(tier, kit.extras().size())); j++) {
            String model = kit.extras().get(j);
            GlbProp prop = assets.prop(model);
            if (prop == null) {
                continue;
            }
            float a = turn + 40 + j * 120;
            float scale = Math.min(r * 0.26f / Math.max(width(prop), 1e-4f), r * 0.3f / height(prop));
            placements.computeIfAbsent(model, ignored -> new ArrayList<>())
                  .add(place(prop, cx + MathUtils.cosDeg(a) * r * 0.5f, floor, cz + MathUtils.sinDeg(a) * r * 0.5f,
                        -a, scale));
        }
    }

    /**
     * The small things on the scouted ground, its trees, bushes and rocks, more the denser its kind of ground; none on
     * water, in cities or round the facilities, fewer by a road and kept off it. Copies merged by model.
     */
    private void scatter(Sector shown) {
        if (ground == null) {
            return;
        }
        Set<Long> sites = new HashSet<>();
        for (Site site : shown.sites()) {
            sites.add(key(site.x(), site.y()));
        }
        Map<String, List<Matrix4>> placements = new LinkedHashMap<>();
        float r = radius;
        for (Hex hex : shown.hexes()) {
            Ground look = WarTableAssets.ground(hex);
            if (!hex.scouted() || look.water() || hex.city() || look.scatter().isEmpty()
                  || sites.contains(key(hex.x(), hex.y()))) {
                continue;
            }
            int count = Math.round(look.density() * 1.4f * (hex.road() ? 0.5f : 1));
            float cx = centreX(hex.x());
            float cz = centreZ(hex.x(), hex.y());
            for (int j = 0; j < count; j++) {
                double seed = hex.x() * 7.31 + hex.y() * 3.17 + j * 1.913;
                float angle = HoloTableView.hash(seed, 0.37) * 360;
                float reach = (float) Math.sqrt(HoloTableView.hash(seed * 1.7, 2.9));
                // Kept off the road, which runs through the middle
                float distance = r * (hex.road() ? 0.45f + 0.3f * reach : 0.75f * reach);
                float x = cx + MathUtils.cosDeg(angle) * distance;
                float z = cz + MathUtils.sinDeg(angle) * distance;
                String model = look.scatter().get(
                      (int) (HoloTableView.hash(seed * 3.1, 5.3) * look.scatter().size()) % look.scatter().size());
                boolean rock = model.contains("/rocks/");
                GlbProp prop = assets.prop(model);
                if (prop == null) {
                    continue;
                }
                float tall = r * (rock ? 0.14f : 0.22f)
                      * (0.75f + 0.5f * HoloTableView.hash(seed * 0.7, 8.1));
                float scale = Math.min(r * SCATTER_SIZE / Math.max(width(prop), 1e-4f), tall / height(prop));
                placements.computeIfAbsent(rock ? model + DETAIL + WarTableAssets.rock(look) : model,
                            ignored -> new ArrayList<>())
                      .add(place(prop, x, ground.height(x, z) - r * 0.01f, z, HoloTableView.hash(seed, 9.9) * 360,
                            scale));
            }
        }
        int instances = 0;
        for (Map.Entry<String, List<Matrix4>> entry : placements.entrySet()) {
            String[] keyed = entry.getKey().split(DETAIL, 2);
            GlbProp prop = assets.prop(keyed[0]);
            if (prop != null) {
                scatter.add(new Batch(prop.batch(entry.getValue()), keyed.length > 1 ? assets.detail(keyed[1]) : null));
                instances += entry.getValue().size();
            }
        }
        LOGGER.debug("War table: {} scattered models in {} batches", instances, scatter.size());
    }

    /** Where {@code prop} stands {@code scale} large on ({@code x}, {@code y}, {@code z}), turned {@code turn}. */
    private static Matrix4 place(GlbProp prop, float x, float y, float z, float turn, float scale) {
        Vector3 min = prop.min();
        Vector3 max = prop.max();
        return new Matrix4().translate(x, y, z).rotate(Vector3.Y, turn).scale(scale, scale, scale)
              .translate(-(min.x + max.x) / 2, -min.y, -(min.z + max.z) / 2);
    }

    private static float width(GlbProp prop) {
        return Math.max(prop.max().x - prop.min().x, prop.max().z - prop.min().z);
    }

    private static float height(GlbProp prop) {
        return Math.max(prop.max().y - prop.min().y, 1e-4f);
    }

    /**
     * Lays the player's forces on the map, each as its force's icon as a standee; one with no icon shows none. Forces
     * sharing a hex fan out round it, spaced so their bases don't touch; on a facility's hex they stand at the front of
     * its plate; too many to fan out stand as one standee on a stack of bases, one for each.
     */
    private void stand(Sector shown) {
        Map<BufferedImage, Standee> made = new IdentityHashMap<>();
        Map<Long, List<Token>> byHex = new LinkedHashMap<>();
        for (Token token : shown.tokens()) {
            if (token.icon() != null) {
                byHex.computeIfAbsent(key(token.x(), token.y()), ignored -> new ArrayList<>()).add(token);
            }
        }
        int stacks = 0;
        for (List<Token> group : byHex.values()) {
            Token first = group.get(0);
            boolean held = garrisoned(shown, first.x(), first.y());
            boolean stacked = group.size() > (held ? MOST_FANNED - 1 : MOST_FANNED);
            int stack = stacked ? stacks++ : -1;
            for (int nth = 0; nth < group.size(); nth++) {
                Token token = group.get(nth);
                Standee standee = made.computeIfAbsent(token.icon(), icon -> standee(icon, true));
                if (standee == null) {
                    continue;
                }
                float[] spot = spot(token, stacked ? 0 : nth, stacked ? 1 : group.size(), held);
                float x = spot[0];
                float z = spot[1];
                float y = held ? topOf(token.x(), token.y()) + radius * PLATE_TALL * 1.25f
                      : ground == null ? topOf(token.x(), token.y()) : ground.height(x, z);
                figures.add(new Figure(standee, token.force(), x, y, z, standeeScale(standee, spot[2]), stack));
            }
        }
        for (Figure figure : figures) {
            if (expected.remove(figure.force())) {
                landing.put(figure.force(), 0f);
            }
        }
    }

    /** Whether a facility stands on the hex at ({@code x}, {@code y}). */
    private static boolean garrisoned(Sector shown, int x, int y) {
        for (Site site : shown.sites()) {
            if (site.x() == x && site.y() == y) {
                return true;
            }
        }
        return false;
    }

    /** Whether any of the player's forces stand on the hex at ({@code x}, {@code y}). */
    private static boolean occupied(Sector shown, int x, int y) {
        for (Token token : shown.tokens()) {
            if (token.icon() != null && token.x() == x && token.y() == y) {
                return true;
            }
        }
        return false;
    }

    /** {@code icon}'s standee, null if it has no shape; kept ones go with the sector's meshes. */
    private @Nullable Standee standee(BufferedImage icon, boolean kept) {
        float depth = STANDEE_DEPTH * Math.max(icon.getWidth(), icon.getHeight()) / STANDEE_SIZE;
        GpuStandee.Geometry geometry = GpuStandee.build(icon, depth);
        if (geometry == null) {
            return null;
        }
        Standee standee = new Standee(standeeMesh(geometry.front()), standeeMesh(geometry.back()),
              standeeMesh(geometry.walls()),
              WarTableAssets.texture(icon), geometry.paint(), geometry.width(), geometry.height(), geometry.depth());
        if (kept) {
            standees.add(standee);
        }
        return standee;
    }

    private static void disposeStandee(Standee standee) {
        standee.face().dispose();
        standee.back().dispose();
        standee.walls().dispose();
        standee.art().dispose();
    }

    private static Mesh standeeMesh(GpuStandee.Part part) {
        Mesh mesh = new Mesh(true, Math.max(1, part.vertices().length / GpuStandee.STRIDE),
              Math.max(1, part.indices().length), new VertexAttributes(VertexAttribute.Position(),
              VertexAttribute.Normal(), VertexAttribute.TexCoords(0)));
        mesh.setVertices(part.vertices());
        mesh.setIndices(part.indices());
        return mesh;
    }

    /**
     * Where the {@code nth} of {@code count} forces on a hex stands and how large, as a share of a lone one: alone, at
     * the middle; two or three, round it, far enough apart that their bases don't touch; on a facility's hex ({@code
     * held}), in a row at the front of its plate, the facility drawn back behind them.
     */
    private float[] spot(Token token, int nth, int count, boolean held) {
        float x;
        float z;
        float share;
        if (held) {
            share = count > 1 ? 0.5f : 0.6f;
            x = count > 1 ? (nth == 0 ? -0.26f : 0.26f) : 0;
            z = HELD_FRONT;
        } else if (count == 1) {
            share = 1;
            x = 0;
            z = 0;
        } else {
            share = 0.62f;
            float angle = count == 2 ? nth * 180 : nth * 120 + 210;
            float offset = count == 2 ? 0.32f : 0.36f;
            x = MathUtils.cosDeg(angle) * offset;
            z = MathUtils.sinDeg(angle) * offset;
        }
        return new float[] { centreX(token.x()) + x * radius, centreZ(token.x(), token.y()) + z * radius, share };
    }

    /**
     * The tracking over the map: the supply networks' ribbons, the scenarios' rings and pillars, the points of
     * interest's diamonds and the cut roads' crosses, each vertex's position, uv, colour and a_kind (see
     * {@link WarTableShaders#HOLO_VERTEX}).
     */
    private FloatArray hologram(Sector shown) {
        FloatArray out = new FloatArray();
        float r = radius;
        for (Link link : shown.supply()) {
            ribbon(out, link, r);
        }
        for (Spot cut : shown.cuts()) {
            flat(out, centreX(cut.x()), topOf(cut.x(), cut.y()) + 0.003f, centreZ(cut.x(), cut.y()), r * 0.55f,
                  HOSTILE, 0.9f, 4, 0);
        }
        for (Beacon beacon : shown.beacons()) {
            if (arrivals.stream().noneMatch(arrival -> indexOfBeacon(List.of(arrival.beacon()), beacon) >= 0)) {
                beacon(out, beacon, 1);
            }
        }
        for (Mark mark : shown.marks()) {
            float y = topOf(mark.x(), mark.y()) + r * 1.1f;
            billboard(out, centreX(mark.x()), y, centreZ(mark.x(), mark.y()), mark.allied() ? ALLIED : POINT,
                  r * 0.32f);
        }
        return out;
    }

    /**
     * A scenario's beacon, its ring counting down its days and a low, dim marker of light over it (a tracking mark,
     * not a searchlight), {@code strength} as bright.
     */
    private void beacon(FloatArray out, Beacon beacon, float strength) {
        if (strength <= 0) {
            return;
        }
        float x = centreX(beacon.x());
        float y = topOf(beacon.x(), beacon.y());
        float z = centreZ(beacon.x(), beacon.y());
        float[] colour = beacon.daysLeft() == 0 ? HOSTILE : BATTLE;
        float left = beacon.daysLeft() < 0 ? 1 : MathUtils.clamp(beacon.daysLeft() / 7f, 0.06f, 1);
        flat(out, x, y + 0.004f, z, radius * 0.95f, colour, (beacon.objective() ? 1.4f : 1) * strength, 1, left);
        quad(out, x, y, z, -1, 0, x, y, z, 1, 0, x, y, z, 1, 1, x, y, z, -1, 1, colour, strength * 0.5f, 2,
              radius * (beacon.objective() ? 1.6f : 1.1f));
    }

    /** A link's ribbon, arched over the hexes it crosses, with dashes flowing from its start to its end. */
    private void ribbon(FloatArray out, Link link, float r) {
        float[] colour = link.player() ? ALLIED : HOSTILE;
        float ax = centreX(link.fromX());
        float az = centreZ(link.fromX(), link.fromY());
        float bx = centreX(link.toX());
        float bz = centreZ(link.toX(), link.toY());
        // Both sides' networks share some links; the enemy's floats a little higher so they don't fight
        float lift = link.player() ? r * 0.28f : r * 0.4f;
        float ay = topOf(link.fromX(), link.fromY()) + lift;
        float by = topOf(link.toX(), link.toY()) + lift;
        float length = (float) Math.hypot(bx - ax, bz - az);
        if (length < 1e-5f) {
            return;
        }
        float px = -(bz - az) / length * r * 0.09f;
        float pz = (bx - ax) / length * r * 0.09f;
        int steps = 6;
        float along = length / (r * 3.2f);
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps;
            float t1 = (i + 1) / (float) steps;
            float x0 = MathUtils.lerp(ax, bx, t0);
            float z0 = MathUtils.lerp(az, bz, t0);
            float y0 = MathUtils.lerp(ay, by, t0) + MathUtils.sin(t0 * MathUtils.PI) * r * 0.25f;
            float x1 = MathUtils.lerp(ax, bx, t1);
            float z1 = MathUtils.lerp(az, bz, t1);
            float y1 = MathUtils.lerp(ay, by, t1) + MathUtils.sin(t1 * MathUtils.PI) * r * 0.25f;
            float speed = link.player() ? 0.7f : 0.45f;
            quad(out, x0 - px, y0, z0 - pz, t0 * along, 0, x1 - px, y1, z1 - pz, t1 * along, 0,
                  x1 + px, y1, z1 + pz, t1 * along, 1, x0 + px, y0, z0 + pz, t0 * along, 1, colour, 1, 0, speed);
        }
    }

    /** A square flat on the map at ({@code x}, {@code y}, {@code z}), {@code half} across each way. */
    private static void flat(FloatArray out, float x, float y, float z, float half, float[] colour, float strength,
          int kind, float parameter) {
        quad(out, x - half, y, z - half, -1, -1, x + half, y, z - half, 1, -1, x + half, y, z + half, 1, 1,
              x - half, y, z + half, -1, 1, colour, strength, kind, parameter);
    }

    private static void billboard(FloatArray out, float x, float y, float z, float[] colour, float size) {
        quad(out, x, y, z, -1, -1, x, y, z, 1, -1, x, y, z, 1, 1, x, y, z, -1, 1, colour, 1, 3, size);
    }

    private static void quad(FloatArray out, float x0, float y0, float z0, float u0, float v0, float x1, float y1,
          float z1, float u1, float v1, float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3,
          float u3, float v3, float[] colour, float strength, int kind, float parameter) {
        float[][] corners = {
              { x0, y0, z0, u0, v0 }, { x1, y1, z1, u1, v1 }, { x2, y2, z2, u2, v2 },
              { x0, y0, z0, u0, v0 }, { x2, y2, z2, u2, v2 }, { x3, y3, z3, u3, v3 } };
        for (float[] c : corners) {
            out.addAll(c[0], c[1], c[2], c[3], c[4], colour[0], colour[1], colour[2], strength, kind, parameter);
        }
    }

    private static Mesh holoMesh(FloatArray vertices) {
        Mesh mesh = new Mesh(true, Math.max(1, vertices.size / HOLO_STRIDE), 0, new VertexAttributes(
              VertexAttribute.Position(), VertexAttribute.TexCoords(0), VertexAttribute.ColorUnpacked(),
              new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_kind")));
        mesh.setVertices(vertices.items, 0, vertices.size);
        return mesh;
    }

    private void disposeMeshes() {
        if (carried != null && !carriedMade) {
            // Carried off the map: its standee goes with the sector's
            drop();
        }
        if (hologram != null) {
            hologram.dispose();
            hologram = null;
        }
        batches.forEach(batch -> batch.mesh().dispose());
        batches.clear();
        plates.clear();
        scatter.forEach(batch -> batch.mesh().dispose());
        scatter.clear();
        groundMeshes.forEach(Mesh::dispose);
        groundMeshes.clear();
        standees.forEach(WarTableView::disposeStandee);
        standees.clear();
        figures.clear();
    }

    @Override
    public void dispose() {
        drop();
        disposeMeshes();
        if (cells != 0) {
            Gdx.gl.glDeleteTexture(cells);
            cells = 0;
        }
        for (Disposable resource : new Disposable[] { terrainShader, fieldShader, overlayShader, holoShader, propShader,
              screen, prism, effects, assets }) {
            if (resource != null) {
                resource.dispose();
            }
        }
    }
}
