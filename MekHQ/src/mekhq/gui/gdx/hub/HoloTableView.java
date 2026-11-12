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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.FloatArray;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.GUIPreferences;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.Star;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;
import mekhq.gui.gdx.hub.HqStations.Pose;
import mekhq.gui.gdx.hub.HqStations.Station;

/**
 * The command post's holotable in its dark room, the star map projected above it: the table lit by a dim key, two rim
 * lights and the hologram's own light, and the hologram drawn additively over it (a disc of rings, a beam from the
 * emitter, and the systems as glowing points in their owners' colours, tinted towards the projector's cyan). Around it
 * stand the {@link HqStations}: the operations table, the comms console and the status boards.
 *
 * <p>The camera starts at a fixed view; dragging turns it, the wheel moves it in or out, and a click on the map's disc
 * opens the star map ({@link #input}), for which the camera can dive down onto the disc ({@link #render(float, float)}).
 * A click on a station steps the camera over to it, and a click on the operations table there opens the war table; a
 * click on nothing, a right click or {@link #back} steps back to the holotable.</p>
 */
final class HoloTableView extends ApplicationAdapter {
    /** The projector's colour, linear. */
    private static final Vector3 CYAN = new Vector3(0.25f, 0.75f, 1.0f);
    /** The emitter dome glows dimmer than authored, so it doesn't wash out the map's centre seen from above. */
    private static final Map<String, Float> EMITTER_GLOW = Map.of("holotable-emitter", 0.1f);
    /** The room's fog, linear; the clear colour is it as the shaders tone map it. */
    private static final Vector3 FOG = new Vector3(0.0035f, 0.0045f, 0.0065f);
    /** The map's disc: its height over the table's anchor and its radius, in metres. */
    private static final float DISC_LIFT = 0.24f;
    private static final float DISC_RADIUS = 0.6f;
    /** The light years from the focus to the disc's edge. */
    private static final float MAP_RADIUS = 420;
    /** How thick the slab of stars is over the disc, in metres. */
    private static final float SLAB = 0.08f;
    private static final float TABLE_RADIUS = 0.8f;
    static final float FIELD_OF_VIEW = 42;
    /** The hologram's stars are about ten light years across at its scale; diving, they shrink to the map's size. */
    private static final float DIVED_STAR_SCALE = 0.15f;
    /** How far a press may move, in pixels, and still be a click. */
    private static final float CLICK_SLOP = 6;
    /** How long the camera takes to step between stations, in seconds. */
    private static final float MOVE_SECONDS = 0.55f;
    private static final int GL_PROGRAM_POINT_SIZE = 0x8642;

    private final Path glb;
    private final @Nullable StarChart chart;
    private final Matrix4 model = new Matrix4();
    private final Vector3 anchor = new Vector3();
    private PerspectiveCamera camera;
    private ShaderProgram propShader;
    private ShaderProgram starShader;
    private ShaderProgram holoShader;
    private GlbProp table;
    private Mesh floor;
    private Mesh disc;
    private Mesh beam;
    private Mesh stars;
    private Mesh veil;
    private ShaderProgram veilShader;
    private Texture white;
    private Texture flat;
    private float time;
    /** How far the map's disc has turned, in degrees; it stops while the camera dives. */
    private float spin;
    /** The disc's turn this frame, and how far the camera has dived. */
    private float angle;
    private float dive;
    private final HqStations stations;
    /** The camera's pose at the holotable, kept while it is at a station. */
    private final Pose overview = new Pose();
    /** The camera steps {@code travel} (0 to 1) of the way from one pose to the other, and is at the third now. */
    private final Pose from = new Pose();
    private final Pose to = new Pose();
    private final Pose live = new Pose();
    private float travel = 1;
    /** The station the camera is at, or steps to. */
    private Station focus = Station.TABLE;
    /** The station under the cursor, nearer than the map's disc; null for none. */
    private @Nullable Station overStation;
    /** Whether the cursor is over the map's disc, and how much it glows for that, 0 to 1. */
    private boolean overMap;
    private float hoverGlow;
    private int pressX;
    private int pressY;
    private boolean dragged;
    private final Runnable mapClicked;
    private final Runnable operationsClicked;
    private final InputProcessor orbit = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            pressX = screenX;
            pressY = screenY;
            dragged = false;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (Math.abs(screenX - pressX) + Math.abs(screenY - pressY) > CLICK_SLOP) {
                dragged = true;
            }
            float turn = -Gdx.input.getDeltaX() * 0.25f;
            float tilt = Gdx.input.getDeltaY() * 0.2f;
            for (Pose pose : new Pose[] { from, to }) {
                pose.yaw += turn;
                pose.pitch = MathUtils.clamp(pose.pitch + tilt, 4, 80);
            }
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (!dragged && button == Input.Buttons.LEFT) {
                click(screenX, screenY);
            } else if (!dragged && button == Input.Buttons.RIGHT) {
                back();
            }
            return true;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            hover(screenX, screenY);
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            for (Pose pose : new Pose[] { from, to }) {
                pose.distance = MathUtils.clamp(pose.distance * (1 + amountY * 0.08f), 1.2f, 8);
            }
            return true;
        }
    };

    /**
     * The table from the GLB file {@code glb}, with {@code chart} over it; none when null, and the stations' props
     * beside it. A click on the map's disc runs {@code mapClicked}, and one on the operations table, with the camera at
     * it, {@code operationsClicked}.
     */
    HoloTableView(Path glb, @Nullable StarChart chart, Runnable mapClicked, Runnable operationsClicked) {
        this.glb = glb;
        this.chart = chart;
        this.mapClicked = mapClicked;
        this.operationsClicked = operationsClicked;
        stations = new HqStations(glb);
    }

    /** The camera's controls, for the screen showing the view to put behind its own. */
    InputProcessor input() {
        return orbit;
    }

    /** Whether the cursor is over something a click acts on: the map's disc, or a station. */
    boolean hovering() {
        return overMap || overStation != null && (overStation != focus || overStation == Station.OPERATIONS);
    }

    /** The station the camera is at, or steps to. */
    Station focus() {
        return focus;
    }

    /** Steps the camera back to the holotable from a station; false if it is there already. */
    boolean back() {
        if (focus == Station.TABLE) {
            return false;
        }
        focus(Station.TABLE);
        return true;
    }

    /** The fonts the status boards write in, once the hub's theme has them. */
    void fonts(BitmapFont label, BitmapFont headline) {
        stations.fonts(label, headline);
    }

    /** What the status boards show; see {@link HqStations#boards}. */
    void boards(String[] captions, String date, String where, String funds, String campaign, String alertsTitle,
          List<String> alerts, boolean scenarioDue) {
        stations.boards(captions, date, where, funds, campaign, alertsTitle, alerts, scenarioDue);
    }

    /** Whether a transmission is coming in, which the comms console blinks for. */
    void incoming(boolean waiting) {
        stations.incoming(waiting);
    }

    /**
     * How high over the disc the camera ends its dive for the disc to show the map as the star map's camera does from
     * {@code mapDistance} light years: the same field of view, at the hologram's scale.
     */
    static float diveHeight(float mapDistance) {
        return mapDistance * DISC_RADIUS / MAP_RADIUS;
    }

    @Override
    public void create() {
        propShader = compile(HoloShaders.PROP_VERTEX, HoloShaders.PROP_FRAGMENT);
        starShader = compile(HoloShaders.STAR_VERTEX, HoloShaders.STAR_FRAGMENT);
        holoShader = compile(HoloShaders.HOLO_VERTEX, HoloShaders.HOLO_FRAGMENT);
        veilShader = compile(HoloShaders.VEIL_VERTEX, HoloShaders.VEIL_FRAGMENT);
        veil = new Mesh(true, 3, 0, new VertexAttribute(VertexAttributes.Usage.Position, 2,
              ShaderProgram.POSITION_ATTRIBUTE));
        veil.setVertices(new float[] { -1, -1, 3, -1, -1, 3 });
        table = GlbProp.load(glb);
        anchor.set(table.anchor());
        if (anchor.isZero()) {
            anchor.set(0, 1.17f, 0);
        }
        white = solid(0xFFFFFFFF);
        flat = solid(0x8080FFFF);
        floor = floor();
        disc = disc();
        beam = beam();
        stars = stars();
        stations.create();
        overview.set(35, 17, 5.2f, new Vector3(0, anchor.y * 1.2f, 0));
        from.set(overview);
        to.set(overview);
        live.set(overview);
        camera = new PerspectiveCamera(FIELD_OF_VIEW, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.05f;
        camera.far = 60;
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null && width > 0 && height > 0) {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
        }
    }

    @Override
    public void render() {
        render(0, 0);
    }

    /**
     * Draws the room with the camera {@code dive} of the way (0 to 1, eased) from its orbit to straight over the disc,
     * the disc turned to face coreward away and its slab of stars flattened, and the hologram faded {@code fade} of
     * the way out as the room fades into the star map's dark.
     */
    void render(float dive, float fade) {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        time += delta;
        if (dive == 0) {
            spin = (spin + delta * 2.5f) % 360;
        }
        float facing = spin > 180 ? 360 : 0;
        angle = MathUtils.lerp(spin, facing, dive);
        hoverGlow = MathUtils.clamp(hoverGlow + (overMap && dive == 0 ? delta : -delta) * 5, 0, 1);
        this.dive = dive;
        stations.hover(dive == 0 ? overStation : null);
        stations.prepare(delta);
        travel = Math.min(1, travel + delta / MOVE_SECONDS);
        live.lerp(from, to, Interpolation.smooth.apply(travel));

        Vector3 target = new Vector3(live.target).lerp(new Vector3(anchor.x, anchor.y + DISC_LIFT, anchor.z), dive);
        float turned = MathUtils.lerp(live.yaw, Math.round(live.yaw / 360) * 360f, dive);
        float tilted = MathUtils.lerp(live.pitch, 90, dive);
        float far = live.distance * (float) Math.pow(diveHeight(StarMapView.START_DISTANCE) / live.distance, dive);
        float cosPitch = MathUtils.cosDeg(tilted);
        camera.position.set(MathUtils.sinDeg(turned) * cosPitch, MathUtils.sinDeg(tilted),
              MathUtils.cosDeg(turned) * cosPitch).scl(far).add(target);
        // Up is the way the camera faces over the floor, which stays defined looking straight down
        camera.up.set(-MathUtils.sinDeg(turned), 0, -MathUtils.cosDeg(turned));
        camera.lookAt(target);
        camera.near = Math.min(0.05f, far * 0.2f);
        camera.update();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glClearColor(display(FOG.x), display(FOG.y), display(FOG.z), 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // A projector's light is never quite steady
        float flicker = 1 + 0.04f * MathUtils.sin(time * 31) + 0.03f * MathUtils.sin(time * 7.3f)
              - (((time * 0.13f) % 1) < 0.012f ? 0.35f : 0);
        drawRoom(flicker);
        if (fade > 0) {
            drawVeil(fade);
        }
        drawHologram(flicker * (1 + 0.6f * hoverGlow), 1 - fade);

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    private void drawRoom(float flicker) {
        propShader.bind();
        propShader.setUniformMatrix("u_projView", camera.combined);
        propShader.setUniformf("u_camera", camera.position);
        propShader.setUniform3fv("u_lightDir", new float[] {
              -0.3f, -1f, -0.45f,
              0.75f, -0.35f, 0.6f,
              -0.8f, -0.25f, 0.55f }, 0, 9);
        propShader.setUniform3fv("u_lightColor", new float[] {
              0.55f, 0.62f, 0.75f,
              0.9f, 0.42f, 0.18f,
              0.3f, 0.55f, 1.3f }, 0, 9);
        propShader.setUniformf("u_holoLight", anchor.x, anchor.y + DISC_LIFT, anchor.z);
        propShader.setUniformf("u_holoColor", new Vector3(CYAN).scl(0.9f * flicker));
        propShader.setUniformf("u_fog", FOG);
        propShader.setUniformf("u_fogDensity", 0.2f);
        propShader.setUniformf("u_glowPulse", 1.4f + 0.25f * MathUtils.sin(time * 2.2f));
        propShader.setUniformf("u_tableRadius", TABLE_RADIUS);
        propShader.setUniformf("u_floor", 0f);
        stations.light(propShader);
        table.draw(propShader, EMITTER_GLOW);
        stations.drawSolids(propShader, white, flat);

        propShader.setUniformf("u_floor", 1f);
        flat.bind(1);
        white.bind(2);
        white.bind(3);
        white.bind(0);
        propShader.setUniformf("u_baseFactor", 0.045f, 0.05f, 0.055f, 1);
        propShader.setUniformf("u_emissive", 0f, 0f, 0f);
        propShader.setUniformf("u_metallic", 0.85f);
        propShader.setUniformf("u_roughness", 0.6f);
        floor.render(propShader, GL20.GL_TRIANGLES);
    }

    /** Fills the screen with the star map's dark, {@code alpha} of the way over the room. */
    private void drawVeil(float alpha) {
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Vector3 dark = StarMapView.BACKGROUND;
        veilShader.bind();
        veilShader.setUniformf("u_colour", display(dark.x), display(dark.y), display(dark.z), alpha);
        veil.render(veilShader, GL20.GL_TRIANGLES);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
    }

    private void drawHologram(float flicker, float glow) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        Gdx.gl.glDepthMask(false);

        holoShader.bind();
        holoShader.setUniformMatrix("u_projView", camera.combined);
        holoShader.setUniformf("u_colour", CYAN);
        holoShader.setUniformf("u_time", time);
        // The beam and the disc's rings give way to the map's own light as the camera nears
        holoShader.setUniformf("u_intensity", flicker * glow * (1 - dive * 0.6f));
        holoShader.setUniformf("u_camera", camera.position);
        holoShader.setUniformMatrix("u_model", model.setToTranslation(anchor));
        holoShader.setUniformi("u_mode", 1);
        beam.render(holoShader, GL20.GL_TRIANGLES);
        holoShader.setUniformMatrix("u_model", hologram().scl(DISC_RADIUS, 1, DISC_RADIUS));
        holoShader.setUniformi("u_mode", 0);
        disc.render(holoShader, GL20.GL_TRIANGLES);

        if (stars != null) {
            Gdx.gl.glEnable(GL_PROGRAM_POINT_SIZE);
            starShader.bind();
            starShader.setUniformMatrix("u_projView", camera.combined);
            // Diving, the slab flattens onto the disc as the map lies on its plane
            starShader.setUniformMatrix("u_model", hologram().scl(1, 1 - dive, 1));
            starShader.setUniformf("u_camera", camera.position);
            starShader.setUniformf("u_time", time);
            starShader.setUniformf("u_intensity", glow);
            starShader.setUniformf("u_pixels", Gdx.graphics.getBackBufferHeight()
                  / (2 * MathUtils.tanDeg(FIELD_OF_VIEW / 2)) * flicker * MathUtils.lerp(1, DIVED_STAR_SCALE, dive));
            stars.render(starShader, GL20.GL_POINTS);
            Gdx.gl.glDisable(GL_PROGRAM_POINT_SIZE);
        }

        holoShader.bind();
        stations.drawHolograms(holoShader, glow);
    }

    /** Steps the camera over to {@code station}, or back to its pose at the holotable. */
    void focus(Station station) {
        if (focus == Station.TABLE) {
            overview.set(to);
        }
        focus = station;
        from.set(live);
        if (station == Station.TABLE) {
            to.set(overview);
        } else {
            stations.pose(station, to);
        }
        // Turn the short way round
        to.yaw = from.yaw + ((to.yaw - from.yaw) % 360 + 540) % 360 - 180;
        travel = GUIPreferences.getInstance().getReduceMotion() ? 1 : 0;
    }

    /** A click: on the map's disc opens it; on a station steps to it, or acts on it there; on nothing steps back. */
    private void click(int screenX, int screenY) {
        if (camera == null) {
            return;
        }
        Ray ray = camera.getPickRay(screenX, screenY);
        float[] along = new float[1];
        Station station = stations.pick(ray, along);
        if (mapDistance(ray) < along[0]) {
            mapClicked.run();
        } else if (station == null) {
            back();
        } else if (station != focus) {
            focus(station);
        } else if (station == Station.OPERATIONS) {
            operationsClicked.run();
        }
    }

    private void hover(int screenX, int screenY) {
        if (camera == null) {
            return;
        }
        Ray ray = camera.getPickRay(screenX, screenY);
        float[] along = new float[1];
        Station station = stations.pick(ray, along);
        float map = mapDistance(ray);
        overMap = map < along[0];
        overStation = overMap ? null : station;
    }

    /** The map's frame: over the anchor, turned as far as the disc has. */
    private Matrix4 hologram() {
        return model.setToTranslation(anchor.x, anchor.y + DISC_LIFT, anchor.z).rotate(Vector3.Y, angle);
    }

    /** How far along {@code ray} it meets the map's disc, or the largest float if it misses. */
    private float mapDistance(Ray ray) {
        Vector3 hit = new Vector3();
        if (!Intersector.intersectRayPlane(ray, new Plane(Vector3.Y, -(anchor.y + DISC_LIFT)), hit)
              || Vector2.dst(hit.x, hit.z, anchor.x, anchor.z) > DISC_RADIUS) {
            return Float.MAX_VALUE;
        }
        return ray.origin.dst(hit);
    }

    @Override
    public void dispose() {
        for (var resource : new com.badlogic.gdx.utils.Disposable[] { propShader, starShader, holoShader, veilShader,
              stations, table, floor, disc, beam, stars, veil, white, flat }) {
            if (resource != null) {
                resource.dispose();
            }
        }
    }

    private Mesh floor() {
        float size = 40;
        Mesh mesh = new Mesh(true, 4, 6, new VertexAttributes(VertexAttribute.Position(), VertexAttribute.Normal(),
              VertexAttribute.TexCoords(0)));
        mesh.setVertices(new float[] {
              -size, 0, -size, 0, 1, 0, 0, 0,
              size, 0, -size, 0, 1, 0, 1, 0,
              size, 0, size, 0, 1, 0, 1, 1,
              -size, 0, size, 0, 1, 0, 0, 1 });
        mesh.setIndices(new short[] { 0, 2, 1, 0, 3, 2 });
        return mesh;
    }

    /** A unit square in the XZ plane, its uv the plane's own -1..1. */
    private Mesh disc() {
        Mesh mesh = new Mesh(true, 4, 6, new VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)));
        mesh.setVertices(new float[] {
              -1.05f, 0, -1.05f, -1.05f, -1.05f,
              1.05f, 0, -1.05f, 1.05f, -1.05f,
              1.05f, 0, 1.05f, 1.05f, 1.05f,
              -1.05f, 0, 1.05f, -1.05f, 1.05f });
        mesh.setIndices(new short[] { 0, 2, 1, 0, 3, 2 });
        return mesh;
    }

    /** An open cone from the emitter's lens up to the disc, uv.y its height. */
    private Mesh beam() {
        int segments = 64;
        float bottom = 0.06f;
        FloatArray vertices = new FloatArray();
        short[] indices = new short[segments * 6];
        for (int i = 0; i <= segments; i++) {
            float angle = MathUtils.PI2 * i / segments;
            float cos = MathUtils.cos(angle);
            float sin = MathUtils.sin(angle);
            vertices.addAll(cos * bottom, 0.005f, sin * bottom, (float) i / segments, 0);
            vertices.addAll(cos * DISC_RADIUS * 0.98f, DISC_LIFT, sin * DISC_RADIUS * 0.98f, (float) i / segments, 1);
        }
        for (int i = 0; i < segments; i++) {
            int base = i * 2;
            int at = i * 6;
            indices[at] = (short) base;
            indices[at + 1] = (short) (base + 1);
            indices[at + 2] = (short) (base + 3);
            indices[at + 3] = (short) base;
            indices[at + 4] = (short) (base + 3);
            indices[at + 5] = (short) (base + 2);
        }
        Mesh mesh = new Mesh(true, vertices.size / 5, indices.length,
              new VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)));
        mesh.setVertices(vertices.toArray());
        mesh.setIndices(indices);
        return mesh;
    }

    /**
     * The chart's systems within {@link #MAP_RADIUS} of its focus, laid on the disc (coreward away from the default
     * view), each a little above it so the map has depth; the focus larger and nearly white.
     */
    private @Nullable Mesh stars() {
        if (chart == null) {
            return null;
        }
        FloatArray vertices = new FloatArray();
        int count = 0;
        for (Star star : chart.stars()) {
            float x = (float) (star.x() - chart.focusX()) / MAP_RADIUS;
            float y = (float) (star.y() - chart.focusY()) / MAP_RADIUS;
            float r = (float) Math.sqrt(x * x + y * y);
            if (r > 0.97f) {
                continue;
            }
            boolean focus = x == 0 && y == 0;
            float hash = hash(star.x(), star.y());
            float[] rgb = star.color().getRGBColorComponents(null);
            float tint = focus ? 0.2f : 0.45f;
            float edge = 1 - MathUtils.clamp((r - 0.82f) / 0.15f, 0, 1);
            float bright = (focus ? 3.5f : 1.1f) * edge;
            vertices.addAll(x * DISC_RADIUS, (hash - 0.5f) * SLAB, -y * DISC_RADIUS);
            for (int c = 0; c < 3; c++) {
                float linear = (float) Math.pow(rgb[c], 2.2);
                float target = c == 0 ? CYAN.x : c == 1 ? CYAN.y : CYAN.z;
                vertices.add((linear + (target - linear) * tint) * bright);
            }
            vertices.add(1);
            vertices.addAll(focus ? 0.05f : 0.014f + hash * 0.006f, hash);
            count++;
        }
        if (count == 0) {
            return null;
        }
        Mesh mesh = new Mesh(true, count, 0, new VertexAttributes(VertexAttribute.Position(),
              VertexAttribute.ColorUnpacked(), VertexAttribute.TexCoords(0)));
        mesh.setVertices(vertices.toArray());
        return mesh;
    }

    static float hash(double x, double y) {
        long bits = Double.doubleToLongBits(x * 12.9898 + y * 78.233);
        bits ^= bits >>> 33;
        bits *= 0xff51afd7ed558ccdL;
        bits ^= bits >>> 33;
        return (bits & 0xFFFFFF) / (float) 0x1000000;
    }

    /** A linear value as the shaders write it: ACES filmic, then sRGB gamma. */
    static float display(float linear) {
        float mapped = linear * (2.51f * linear + 0.03f) / (linear * (2.43f * linear + 0.59f) + 0.14f);
        return (float) Math.pow(MathUtils.clamp(mapped, 0, 1), 1 / 2.2);
    }

    private static Texture solid(int rgba) {
        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.drawPixel(0, 0, rgba);
        Texture texture = new Texture(pixel);
        pixel.dispose();
        return texture;
    }

    /** Compiles self-contained GLSL: the window's version prefix (which declares fragColor) is set aside meanwhile. */
    static ShaderProgram compile(String vertex, String fragment) {
        String vertexPrefix = ShaderProgram.prependVertexCode;
        String fragmentPrefix = ShaderProgram.prependFragmentCode;
        ShaderProgram program;
        try {
            ShaderProgram.prependVertexCode = "";
            ShaderProgram.prependFragmentCode = "";
            program = new ShaderProgram(vertex, fragment);
        } finally {
            ShaderProgram.prependVertexCode = vertexPrefix;
            ShaderProgram.prependFragmentCode = fragmentPrefix;
        }
        if (!program.isCompiled()) {
            String log = program.getLog();
            program.dispose();
            throw new IllegalStateException("Holotable shader failed: " + log);
        }
        return program;
    }
}
