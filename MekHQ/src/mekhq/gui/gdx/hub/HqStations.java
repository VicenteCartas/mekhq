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
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import jakarta.annotation.Nullable;

/**
 * The command post's stations around its holotable: the operations table, whose plate projects a green hologram of a
 * sector's hexes and relief; the comms console, whose screens trace the traffic and blink amber while a transmission
 * waits; and the status boards on the wall behind, which show the date, the funds and what needs attention.
 */
final class HqStations implements Disposable {
    /** A place in the room the camera can step over to; {@link #TABLE} is the holotable, the room's overview. */
    enum Station { TABLE, OPERATIONS, COMMS, STATUS }

    /** A camera's pose about a point: its bearing and elevation in degrees, its distance in metres, and the point. */
    static final class Pose {
        float yaw;
        float pitch;
        float distance;
        final Vector3 target = new Vector3();

        Pose set(float yaw, float pitch, float distance, Vector3 target) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
            this.target.set(target);
            return this;
        }

        Pose set(Pose other) {
            return set(other.yaw, other.pitch, other.distance, other.target);
        }

        /** This pose {@code t} of the way from {@code from} to {@code to}. */
        Pose lerp(Pose from, Pose to, float t) {
            yaw = MathUtils.lerp(from.yaw, to.yaw, t);
            pitch = MathUtils.lerp(from.pitch, to.pitch, t);
            distance = MathUtils.lerp(from.distance, to.distance, t);
            target.set(from.target).lerp(to.target, t);
            return this;
        }
    }

    /** The ground operations' colour, and the colour of what needs attention; linear. */
    static final Vector3 GREEN = new Vector3(0.3f, 1.0f, 0.5f);
    static final Vector3 AMBER = new Vector3(1.0f, 0.55f, 0.12f);
    private static final Vector3 CYAN = new Vector3(0.25f, 0.75f, 1.0f);
    /** Where the stations stand: bearings around the holotable in degrees, as the camera's yaw, and ranges in metres. */
    private static final float OPERATIONS_BEARING = 275;
    private static final float OPERATIONS_RANGE = 2.75f;
    private static final float COMMS_BEARING = 158;
    private static final float COMMS_RANGE = 2.6f;
    private static final float WALL_BEARING = 215;
    private static final float WALL_RADIUS = 4.6f;
    /** How far the wall reaches either side of its bearing, in degrees, and how tall it is. */
    private static final float WALL_SPAN = 58;
    private static final float WALL_HEIGHT = 3.4f;
    /** The status boards: the degrees between them, their size, the height of their centres, and their bezels. */
    private static final float BOARD_SPREAD = 22;
    private static final float BOARD_WIDTH = 1.5f;
    private static final float BOARD_HEIGHT = 0.84f;
    private static final float BOARD_LIFT = 1.8f;
    private static final float BEZEL = 0.05f;
    private static final float BEZEL_DEPTH = 0.08f;
    /** Half the operations table's projection plate, across and deep, in metres. */
    private static final float PLATE_X = 0.9f;
    private static final float PLATE_Z = 0.54f;
    /** The hologram's relief: its stacked contour layers and the height between them, in metres. */
    private static final int LAYERS = 6;
    private static final float LAYER_STEP = 0.022f;
    private static final int TEXT_WIDTH = 1024;
    private static final int TEXT_HEIGHT = 576;
    /** The theme's fonts raster at four pixels a unit. */
    private static final float TEXT_UNITS = 4;
    /** The console's screens show only what the holographic pass draws on them, not their authored glow. */
    private static final Map<String, Float> DARK_SCREENS = Map.of("comms-console-screen", 0f, "comms-console-alert",
          0f);

    private final Path sandFile;
    private final Path consoleFile;
    private final Matrix4 sandPlace = new Matrix4();
    private final Matrix4 consolePlace = new Matrix4();
    private final Matrix4 model = new Matrix4();
    private final Vector3 plate = new Vector3();
    private final Vector3 desk = new Vector3();
    private final Vector3[] boardCentres = new Vector3[3];
    private final Vector3[] boardRights = new Vector3[3];
    private final Vector3[] boardNormals = new Vector3[3];
    private final FrameBuffer[] boardText = new FrameBuffer[3];
    private final Vector3[] boardColours = { CYAN, CYAN, GREEN };
    private final String[][] boardLines = { { "", "", "" }, { "", "", "" }, { "", "", "" } };
    private final float[] hover = new float[Station.values().length];
    private final GlyphLayout layout = new GlyphLayout();
    private GlbProp sandSource;
    private GlbProp consoleSource;
    private GlbProp sand;
    private GlbProp console;
    private List<Mesh> screens = List.of();
    private Mesh plateQuad;
    private Mesh wall;
    private Mesh bezels;
    private Mesh boards;
    private SpriteBatch batch;
    private @Nullable BitmapFont small;
    private @Nullable BitmapFont big;
    private @Nullable Station hovered;
    private boolean boardsChanged = true;
    private boolean scenarioDue;
    private boolean incoming;
    private float time;

    /** The stations, their props beside the holotable's file {@code table}. */
    HqStations(Path table) {
        sandFile = table.resolveSibling("sandtable.glb");
        consoleFile = table.resolveSibling("comms-console.glb");
    }

    /** GPU thread: loads the props and builds the room's wall and boards. */
    void create() {
        sandSource = GlbProp.load(sandFile);
        consoleSource = GlbProp.load(consoleFile);
        place(sandPlace, OPERATIONS_BEARING, OPERATIONS_RANGE);
        place(consolePlace, COMMS_BEARING, COMMS_RANGE);
        sand = sandSource.batch(List.of(sandPlace));
        console = consoleSource.batch(List.of(consolePlace));
        screens = console.meshes("comms-console-alert");
        plate.set(sandSource.anchor()).mul(sandPlace);
        desk.set(0, 1.2f, 0).mul(consolePlace);
        plateQuad = plateQuad();
        wall = wall();
        bezels = boardsAndBezels(true);
        boards = boardsAndBezels(false);
        batch = new SpriteBatch();
        batch.getProjectionMatrix().setToOrtho2D(0, 0, TEXT_WIDTH / TEXT_UNITS, TEXT_HEIGHT / TEXT_UNITS);
        for (int i = 0; i < boardText.length; i++) {
            boardText[i] = new FrameBuffer(Pixmap.Format.RGBA8888, TEXT_WIDTH, TEXT_HEIGHT, false);
            boardText[i].getColorBufferTexture().setFilter(Texture.TextureFilter.Linear,
                  Texture.TextureFilter.Linear);
        }
    }

    private static void place(Matrix4 placement, float bearing, float range) {
        placement.setToTranslation(MathUtils.sinDeg(bearing) * range, 0, MathUtils.cosDeg(bearing) * range)
              .rotate(Vector3.Y, bearing + 180);
    }

    /** The fonts the boards write in: {@code label} for their captions and details, {@code headline} for figures. */
    void fonts(BitmapFont label, BitmapFont headline) {
        small = label;
        big = headline;
        boardsChanged = true;
    }

    /**
     * What the boards show: the date {@code date} and where the force is, {@code where}; the funds and the campaign's
     * name; and its {@code alerts}, titled {@code alertsTitle}, or {@code clear} when there are none.
     */
    void boards(String[] captions, String date, String where, String funds, String campaign, String alertsTitle,
          List<String> alerts, boolean scenario) {
        boardLines[0] = new String[] { captions[0], date, where };
        boardLines[1] = new String[] { captions[1], funds, campaign };
        boardLines[2] = new String[] { captions[2], alertsTitle, String.join("  \u00b7  ", alerts) };
        boardColours[2] = alerts.isEmpty() ? GREEN : AMBER;
        scenarioDue = scenario;
        boardsChanged = true;
    }

    /** Whether a transmission is coming in, which the comms console's screens blink for. */
    void incoming(boolean waiting) {
        incoming = waiting;
    }

    /** The station under the cursor, which glows a little brighter; null for none. */
    void hover(@Nullable Station station) {
        hovered = station;
    }

    /** The pose the camera takes at {@code station}, set into {@code pose}; the holotable's is left as it is. */
    Pose pose(Station station, Pose pose) {
        return switch (station) {
            case TABLE -> pose;
            case OPERATIONS -> pose.set(OPERATIONS_BEARING + 180, 52, 2.7f, plate);
            case COMMS -> pose.set(COMMS_BEARING + 180, 14, 2.2f, desk);
            case STATUS -> pose.set(WALL_BEARING + 180, 4, 4.4f, boardCentres[1]);
        };
    }

    /** The nearest station {@code ray} meets, or null; its distance along the ray goes into {@code distance}[0]. */
    @Nullable Station pick(Ray ray, float[] distance) {
        Station nearest = null;
        float best = Float.MAX_VALUE;
        Vector3 hit = new Vector3();
        float sandAt = boxHit(ray, sandPlace, sandSource, hit);
        if (sandAt < best) {
            best = sandAt;
            nearest = Station.OPERATIONS;
        }
        float consoleAt = boxHit(ray, consolePlace, consoleSource, hit);
        if (consoleAt < best) {
            best = consoleAt;
            nearest = Station.COMMS;
        }
        for (int i = 0; i < boardCentres.length; i++) {
            Vector3 normal = boardNormals[i];
            Plane plane = new Plane(normal, boardCentres[i]);
            if (Intersector.intersectRayPlane(ray, plane, hit)) {
                Vector3 offset = new Vector3(hit).sub(boardCentres[i]);
                float across = Math.abs(offset.dot(boardRights[i]));
                float up = Math.abs(offset.y);
                float along = ray.origin.dst(hit);
                if (across <= BOARD_WIDTH / 2 + BEZEL && up <= BOARD_HEIGHT / 2 + BEZEL && along < best) {
                    best = along;
                    nearest = Station.STATUS;
                }
            }
        }
        distance[0] = best;
        return nearest;
    }

    /** How far along {@code ray} it meets {@code prop}'s bounds placed by {@code placement}, or the largest float. */
    private static float boxHit(Ray ray, Matrix4 placement, GlbProp prop, Vector3 hit) {
        Ray local = ray.cpy().mul(new Matrix4(placement).inv());
        if (!Intersector.intersectRayBounds(local, new BoundingBox(prop.min(), prop.max()), hit)) {
            return Float.MAX_VALUE;
        }
        return ray.origin.dst(hit.mul(placement));
    }

    /** GPU thread, outside any frame buffer: writes the boards again if what they show changed. */
    void prepare(float delta) {
        time += delta;
        for (Station station : Station.values()) {
            int i = station.ordinal();
            hover[i] = MathUtils.clamp(hover[i] + (station == hovered ? delta : -delta) * 5, 0, 1);
        }
        if (!boardsChanged || small == null || big == null) {
            return;
        }
        boardsChanged = false;
        float width = TEXT_WIDTH / TEXT_UNITS;
        float height = TEXT_HEIGHT / TEXT_UNITS;
        float margin = 16;
        for (int i = 0; i < boardText.length; i++) {
            boardText[i].begin();
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();
            String[] lines = boardLines[i];
            write(small, lines[0].toUpperCase(Locale.ROOT), margin, height - margin, width - margin * 2,
                  Align.left);
            write(big, lines[1], margin, height * 0.64f, width - margin * 2, Align.left);
            write(small, lines[2], margin, height * 0.27f, width - margin * 2, Align.left);
            batch.end();
            boardText[i].end();
            Texture texture = boardText[i].getColorBufferTexture();
            texture.bind();
            Gdx.gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
            texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }
    }

    /** Writes {@code text} with its top at {@code top}, shrunk to fit {@code width} if it's wider. */
    private void write(BitmapFont font, String text, float x, float top, float width, int align) {
        if (text.isEmpty()) {
            return;
        }
        float scaleX = font.getData().scaleX;
        float scaleY = font.getData().scaleY;
        layout.setText(font, text);
        if (layout.width > width) {
            font.getData().setScale(scaleX * width / layout.width, scaleY * width / layout.width);
        }
        font.setColor(Color.WHITE);
        font.draw(batch, text, x, top, width, align, false);
        font.getData().setScale(scaleX, scaleY);
    }

    /**
     * The stations' lights and shadows on the room's prop shader: the operations hologram's green glow over its table,
     * the console's screens, and the two props' contact shadows on the deck.
     */
    void light(ShaderProgram shader) {
        float blink = incoming ? 0.5f + 0.5f * MathUtils.sin(time * 6) : 0;
        float sandGlow = 0.12f * (1 + 0.6f * hover[Station.OPERATIONS.ordinal()]);
        Vector3 screen = new Vector3(desk).add(0, 0.15f, 0).lerp(new Vector3(0, desk.y, 0), 0.12f);
        Vector3 screenLight = new Vector3(GREEN).scl(0.05f).lerp(new Vector3(AMBER).scl(0.16f), blink);
        shader.setUniform3fv("u_pointLight", new float[] {
              plate.x, plate.y + 0.3f, plate.z,
              screen.x, screen.y, screen.z }, 0, 6);
        shader.setUniform3fv("u_pointColor", new float[] {
              GREEN.x * sandGlow, GREEN.y * sandGlow, GREEN.z * sandGlow,
              screenLight.x, screenLight.y, screenLight.z }, 0, 6);
        Vector3 sandAt = new Vector3().mul(sandPlace);
        Vector3 consoleAt = new Vector3().mul(consolePlace);
        shader.setUniform4fv("u_blockers", new float[] {
              sandAt.x, sandAt.z, 1.25f, 0.55f,
              consoleAt.x, consoleAt.z, 0.85f, 0.5f }, 0, 8);
    }

    /** Draws the stations' props, the wall and the boards' bezels with the room's prop shader. */
    void drawSolids(ShaderProgram shader, Texture white, Texture flat) {
        shader.setUniformf("u_floor", 0f);
        sand.draw(shader, Map.of());
        console.draw(shader, DARK_SCREENS);
        flat.bind(1);
        white.bind(2);
        white.bind(3);
        white.bind(0);
        shader.setUniformf("u_emissive", 0f, 0f, 0f);
        shader.setUniformf("u_baseFactor", 0.05f, 0.055f, 0.06f, 1);
        shader.setUniformf("u_metallic", 0.8f);
        shader.setUniformf("u_roughness", 0.45f);
        bezels.render(shader, GL20.GL_TRIANGLES);
        shader.setUniformf("u_floor", 2f);
        shader.setUniformf("u_baseFactor", 0.045f, 0.05f, 0.055f, 1);
        shader.setUniformf("u_metallic", 0.7f);
        shader.setUniformf("u_roughness", 0.6f);
        wall.render(shader, GL20.GL_TRIANGLES);
    }

    /**
     * Draws the stations' light additively with the hologram shader: the operations table's hologram, the console's
     * screens and the boards, {@code intensity} as bright as they are when the room is in full view.
     */
    void drawHolograms(ShaderProgram shader, float intensity) {
        shader.setUniformf("u_haze", 0.07f);
        shader.setUniformf("u_time", time);
        shader.setUniformf("u_accent", AMBER);

        shader.setUniformi("u_mode", 2);
        shader.setUniformf("u_colour", GREEN);
        shader.setUniformf("u_half", PLATE_X, PLATE_Z);
        shader.setUniformf("u_layers", LAYERS);
        shader.setUniformf("u_alert", scenarioDue ? 1 : 0);
        shader.setUniformf("u_intensity", intensity * (1 + 0.6f * hover[Station.OPERATIONS.ordinal()]));
        for (int layer = 0; layer < LAYERS; layer++) {
            model.set(sandPlace).translate(sandSource.anchor()).translate(0, 0.003f + layer * LAYER_STEP, 0);
            shader.setUniformMatrix("u_model", model);
            shader.setUniformf("u_layer", layer);
            plateQuad.render(shader, GL20.GL_TRIANGLES);
        }

        shader.setUniformi("u_mode", 3);
        shader.setUniformMatrix("u_model", model.idt());
        shader.setUniformf("u_alert", incoming ? 1 : 0);
        shader.setUniformf("u_intensity", intensity * (1 + 0.5f * hover[Station.COMMS.ordinal()]));
        for (int i = 0; i < screens.size(); i++) {
            shader.setUniformf("u_layer", i);
            screens.get(i).render(shader, GL20.GL_TRIANGLES);
        }

        shader.setUniformi("u_mode", 4);
        shader.setUniformi("u_text", 4);
        shader.setUniformf("u_half", BOARD_WIDTH / 2, BOARD_HEIGHT / 2);
        shader.setUniformf("u_intensity", intensity * (1 + 0.4f * hover[Station.STATUS.ordinal()]));
        for (int i = 0; i < boardText.length; i++) {
            boardText[i].getColorBufferTexture().bind(4);
            shader.setUniformf("u_colour", boardColours[i]);
            shader.setUniformf("u_layer", i);
            boards.render(shader, GL20.GL_TRIANGLES, i * 6, 6);
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    /** The operations table's plate as a quad over its anchor, its uv the plate's own metres. */
    private static Mesh plateQuad() {
        Mesh mesh = new Mesh(true, 4, 6, new VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0)));
        mesh.setVertices(new float[] {
              -PLATE_X, 0, -PLATE_Z, -PLATE_X, -PLATE_Z,
              PLATE_X, 0, -PLATE_Z, PLATE_X, -PLATE_Z,
              PLATE_X, 0, PLATE_Z, PLATE_X, PLATE_Z,
              -PLATE_X, 0, PLATE_Z, -PLATE_X, PLATE_Z });
        mesh.setIndices(new short[] { 0, 2, 1, 0, 3, 2 });
        return mesh;
    }

    /** The curved wall behind the boards, facing the holotable, its uv its metres along and up. */
    private static Mesh wall() {
        int segments = 36;
        FloatArray vertices = new FloatArray();
        ShortArray indices = new ShortArray();
        for (int i = 0; i <= segments; i++) {
            float bearing = WALL_BEARING - WALL_SPAN + 2 * WALL_SPAN * i / segments;
            float sin = MathUtils.sinDeg(bearing);
            float cos = MathUtils.cosDeg(bearing);
            float along = MathUtils.degreesToRadians * bearing * WALL_RADIUS;
            vertices.addAll(sin * WALL_RADIUS, 0, cos * WALL_RADIUS, -sin, 0, -cos, along, 0);
            vertices.addAll(sin * WALL_RADIUS, WALL_HEIGHT, cos * WALL_RADIUS, -sin, 0, -cos, along, WALL_HEIGHT);
            if (i > 0) {
                short base = (short) (i * 2 - 2);
                indices.addAll(base, (short) (base + 1), (short) (base + 3), base, (short) (base + 3),
                      (short) (base + 2));
            }
        }
        return mesh(vertices, indices);
    }

    /**
     * The boards' bezels, boxes standing out of the wall, or with {@code bezels} false the boards' faces just in front
     * of them, six indices each, their uv 0 to 1 across and up. Notes each board's centre, right and normal.
     */
    private Mesh boardsAndBezels(boolean bezels) {
        FloatArray vertices = new FloatArray();
        ShortArray indices = new ShortArray();
        Vector3 up = new Vector3(0, 1, 0);
        for (int i = 0; i < 3; i++) {
            float bearing = WALL_BEARING + (1 - i) * BOARD_SPREAD;
            Vector3 outward = new Vector3(MathUtils.sinDeg(bearing), 0, MathUtils.cosDeg(bearing));
            Vector3 right = new Vector3(-outward.z, 0, outward.x);
            float face = WALL_RADIUS - BEZEL_DEPTH;
            Vector3 centre = new Vector3(outward).scl(face - 0.003f).add(0, BOARD_LIFT, 0);
            boardCentres[i] = centre;
            boardRights[i] = right;
            boardNormals[i] = new Vector3(outward).scl(-1);
            if (bezels) {
                Vector3 middle = new Vector3(outward).scl(face + BEZEL_DEPTH / 2).add(0, BOARD_LIFT, 0);
                box(vertices, indices, middle, new Vector3(right).scl(BOARD_WIDTH / 2 + BEZEL),
                      new Vector3(up).scl(BOARD_HEIGHT / 2 + BEZEL), new Vector3(outward).scl(BEZEL_DEPTH / 2));
            } else {
                Vector3 halfRight = new Vector3(right).scl(BOARD_WIDTH / 2);
                Vector3 halfUp = new Vector3(up).scl(BOARD_HEIGHT / 2);
                short base = (short) (vertices.size / 8);
                corner(vertices, centre, halfRight, halfUp, -1, -1, boardNormals[i], 0, 0);
                corner(vertices, centre, halfRight, halfUp, 1, -1, boardNormals[i], 1, 0);
                corner(vertices, centre, halfRight, halfUp, 1, 1, boardNormals[i], 1, 1);
                corner(vertices, centre, halfRight, halfUp, -1, 1, boardNormals[i], 0, 1);
                indices.addAll(base, (short) (base + 1), (short) (base + 2), base, (short) (base + 2),
                      (short) (base + 3));
            }
        }
        return mesh(vertices, indices);
    }

    private static void corner(FloatArray vertices, Vector3 centre, Vector3 right, Vector3 up, float x, float y,
          Vector3 normal, float u, float v) {
        vertices.addAll(centre.x + right.x * x + up.x * y, centre.y + right.y * x + up.y * y,
              centre.z + right.z * x + up.z * y, normal.x, normal.y, normal.z, u, v);
    }

    /** Adds a box about {@code centre} with the half axes {@code a}, {@code b} and {@code c}, flat shaded. */
    private static void box(FloatArray vertices, ShortArray indices, Vector3 centre, Vector3 a, Vector3 b, Vector3 c) {
        Vector3[] axes = { a, b, c };
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                Vector3 normal = new Vector3(axes[axis]).nor().scl(sign);
                Vector3 faceCentre = new Vector3(centre).mulAdd(axes[axis], sign);
                Vector3 u = axes[(axis + 1) % 3];
                Vector3 w = axes[(axis + 2) % 3];
                short base = (short) (vertices.size / 8);
                corner(vertices, faceCentre, u, w, -1, -1, normal, 0, 0);
                corner(vertices, faceCentre, u, w, 1, -1, normal, 0, 0);
                corner(vertices, faceCentre, u, w, 1, 1, normal, 0, 0);
                corner(vertices, faceCentre, u, w, -1, 1, normal, 0, 0);
                indices.addAll(base, (short) (base + 1), (short) (base + 2), base, (short) (base + 2),
                      (short) (base + 3));
            }
        }
    }

    private static Mesh mesh(FloatArray vertices, ShortArray indices) {
        Mesh mesh = new Mesh(true, vertices.size / 8, indices.size, new VertexAttributes(VertexAttribute.Position(),
              VertexAttribute.Normal(), VertexAttribute.TexCoords(0)));
        mesh.setVertices(vertices.toArray());
        mesh.setIndices(indices.toArray());
        return mesh;
    }

    @Override
    public void dispose() {
        for (Disposable resource : new Disposable[] { sand, console, sandSource, consoleSource, plateQuad, wall, bezels,
              boards, batch }) {
            if (resource != null) {
                resource.dispose();
            }
        }
        for (FrameBuffer buffer : boardText) {
            if (buffer != null) {
                buffer.dispose();
            }
        }
    }
}
