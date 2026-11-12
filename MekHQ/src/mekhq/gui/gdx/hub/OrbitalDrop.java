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

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import jakarta.annotation.Nullable;

/**
 * The orbital drop from the star map down onto an operation's war table, and the climb back out. The system's star
 * resolves into a wireframe globe turning its operation's marker round to face the camera; the camera falls onto the
 * marker as the globe opens out flat under it, its meridians and parallels giving way to the war table's hex grid, which
 * locks in from the marker outward; the table then shows from under it. Climbing out runs it all backwards, the grid
 * curling back into the globe and the globe shrinking into its star.
 *
 * <p>The table is built before the drop starts, so it falls without a hitch; should it not be {@link #advance ready},
 * the drop holds just before it shows ({@link #LOCK}). It draws over what's under it: the star map while it starts and
 * ends, the war table as it lands.</p>
 *
 * <p>Its space is the war table's: y up, the marker landing on the middle of the table's top.</p>
 */
final class OrbitalDrop implements Disposable {
    /** The globe's radius; it opens out into a plane its meridians and parallels keep their lengths on. */
    private static final float GLOBE = 1;
    /** When the camera starts its fall, the globe facing it. */
    private static final float FALL = 0.8f;
    /** When the table is needed to go on: the drop holds here until it is. */
    private static final float LOCK = 1.35f;
    /** When the table shows, the drop fading off it. */
    private static final float HANDOFF = 1.8f;
    private static final float END = 2.4f;
    /** How many times as fast the climb out runs as the drop. */
    private static final float CLIMB_SPEED = 1.6f;
    private static final float HIGH = 24;
    private static final float START_HIGH = 30;
    private static final float START_PITCH = 86;
    private static final Vector3 CYAN = new Vector3(0.25f, 0.75f, 1.0f);
    private static final Vector3 GREEN = new Vector3(0.22f, 1.0f, 0.5f);

    private static final String LINE_VERTEX = """
          #version 330 core
          in vec2 a_surface;
          in float a_lift;
          in float a_order;
          in float a_kind;
          uniform mat4 u_projView;
          uniform vec3 u_centre;
          uniform vec3 u_camera;
          // How curved the globe is: one over its radius, 0 opened out flat
          uniform float u_curve;
          uniform float u_flat;
          uniform float u_spin;
          uniform float u_reveal;
          uniform float u_lines;
          uniform float u_marker;
          uniform float u_markerSize;
          uniform vec2 u_shift;
          uniform float u_time;
          uniform vec3 u_colour;
          out vec3 v_colour;
          const vec3 AMBER = vec3(1.0, 0.72, 0.3);

          void main() {
              float pulse = fract(u_time * 0.7);
              float size = a_kind > 0.5 ? u_markerSize : 1.0;
              vec2 s = a_surface * size * (a_kind > 1.5 ? 1.0 + pulse * 2.0 : 1.0) + vec2(u_spin, 0.0);
              float lift = a_lift * size;
              vec3 p;
              vec3 n;
              if (u_curve < 1e-3) {
                  n = vec3(0.0, 1.0, 0.0);
                  p = u_centre + vec3(s.x, lift, s.y);
              } else {
                  float r = 1.0 / u_curve;
                  float lon = s.x * u_curve;
                  float lat = s.y * u_curve;
                  n = vec3(cos(lat) * sin(lon), cos(lat) * cos(lon), sin(lat));
                  p = u_centre + vec3(0.0, -r, 0.0) + n * (r + lift);
              }
              float facing = dot(n, normalize(u_camera - p));
              float near = mix(0.2, 1.0, smoothstep(-0.2, 0.2, facing));
              float shown = clamp((u_reveal - a_order) * 8.0, 0.0, 1.0);
              float edge = mix(1.0, 1.0 - smoothstep(1.1, 2.4, length(a_surface)), u_flat);
              float flicker = 0.88 + 0.12 * sin(u_time * 9.0 + a_order * 60.0);
              if (a_kind < 0.5) {
                  v_colour = u_colour * 0.55 * u_lines * shown * near * edge * flicker;
              } else if (a_kind < 1.5) {
                  v_colour = AMBER * u_marker * near;
              } else {
                  v_colour = AMBER * 0.7 * u_marker * near * (1.0 - pulse);
              }
              gl_Position = u_projView * vec4(p, 1.0);
              gl_Position.xy += u_shift * gl_Position.w;
          }
          """;

    private static final String LINE_FRAGMENT = """
          #version 330 core
          in vec3 v_colour;
          out vec4 fragColor;
          void main() {
              fragColor = vec4(v_colour, 1.0);
          }
          """;

    private static final String GRID_VERTEX = """
          #version 330 core
          in vec3 a_position;
          uniform mat4 u_projView;
          out vec3 v_world;
          void main() {
              v_world = a_position;
              gl_Position = u_projView * vec4(a_position, 1.0);
          }
          """;

    /** The war table's hex grid, locking in from the marker outward behind a bright front. */
    private static final String GRID_FRAGMENT = """
          #version 330 core
          in vec3 v_world;
          // The grid's origin and a hex's radius
          uniform vec3 u_grid;
          uniform vec3 u_colour;
          uniform float u_alpha;
          uniform float u_front;
          out vec4 fragColor;

          vec2 hexAt(vec2 p) {
              p = (p - u_grid.xy) / u_grid.z;
              vec3 cube = vec3(2.0 / 3.0 * p.x, -1.0 / 3.0 * p.x + 0.57735027 * p.y, 0.0);
              cube.z = -cube.x - cube.y;
              vec3 rounded = floor(cube + 0.5);
              vec3 off = abs(rounded - cube);
              if (off.x > off.y && off.x > off.z) {
                  rounded.x = -rounded.y - rounded.z;
              } else if (off.y > off.z) {
                  rounded.y = -rounded.x - rounded.z;
              }
              return vec2(rounded.x, rounded.y + (rounded.x - mod(rounded.x, 2.0)) / 2.0);
          }

          vec2 centreOf(vec2 at) {
              return u_grid.xy + u_grid.z * vec2(1.5 * at.x, 1.7320508 * (at.y + mod(at.x, 2.0) * 0.5));
          }

          void main() {
              vec2 p = v_world.xz;
              vec2 local = (p - centreOf(hexAt(p))) / u_grid.z;
              float d = max(abs(local.y), max(abs(dot(local, vec2(0.8660254, 0.5))),
                    abs(dot(local, vec2(0.8660254, -0.5)))));
              float w = max(fwidth(d), 1e-5);
              float line = 1.0 - smoothstep(0.4 * w, 1.4 * w, 0.8660254 - d);
              float away = length(p);
              float locked = 1.0 - smoothstep(u_front - 0.06, u_front, away);
              float front = exp(-pow((away - u_front) / 0.05, 2.0));
              float fade = 1.0 - smoothstep(0.9, 1.9, away);
              float glow = line * (0.32 * locked + 0.9 * front) + front * 0.025;
              fragColor = vec4(u_colour * glow * fade * u_alpha, 1.0);
          }
          """;

    private static final String VEIL_VERTEX = """
          #version 330 core
          in vec3 a_position;
          void main() {
              gl_Position = vec4(a_position.xy, 0.0, 1.0);
          }
          """;

    private static final String VEIL_FRAGMENT = """
          #version 330 core
          uniform vec4 u_veil;
          out vec4 fragColor;
          void main() {
              fragColor = u_veil;
          }
          """;

    private PerspectiveCamera camera;
    private final Vector2 star = new Vector2();
    private final float[] grid = { 0, 0, 0.06f };
    private ShaderProgram lineShader;
    private ShaderProgram gridShader;
    private ShaderProgram veilShader;
    private Mesh globe;
    private Mesh plane;
    private Mesh screen;
    /** Seconds into the drop; the climb out runs it back down to 0. */
    private float clock;
    /** 1 dropping, -1 climbing out, 0 neither. */
    private int heading;
    private boolean starSeen;
    /** Whether the drop was skipped: it ends as soon as the table's ready. */
    private boolean hurried;
    private float time;

    void create() {
        camera = new PerspectiveCamera(WarTableView.FIELD_OF_VIEW, 1, 1);
        lineShader = HoloTableView.compile(LINE_VERTEX, LINE_FRAGMENT);
        gridShader = HoloTableView.compile(GRID_VERTEX, GRID_FRAGMENT);
        veilShader = HoloTableView.compile(VEIL_VERTEX, VEIL_FRAGMENT);
        globe = globe();
        float top = WarTableView.TABLE_TOP;
        float reach = 2.6f;
        plane = new Mesh(true, 4, 6, VertexAttribute.Position());
        plane.setVertices(new float[] { -reach, top, -reach, reach, top, -reach, reach, top, reach, -reach, top,
                                        reach });
        plane.setIndices(new short[] { 0, 1, 2, 2, 3, 0 });
        screen = new Mesh(true, 4, 6, VertexAttribute.Position());
        screen.setVertices(new float[] { -1, -1, 0, 1, -1, 0, 1, 1, 0, -1, 1, 0 });
        screen.setIndices(new short[] { 0, 1, 2, 2, 3, 0 });
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    void resize(int width, int height) {
        if (camera != null && width > 0 && height > 0) {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
        }
    }

    /** Starts the drop from the star map's chosen system down onto its operation. */
    void drop() {
        clock = 0;
        heading = 1;
        starSeen = false;
        hurried = false;
    }

    /** Starts the climb from the war table back out to the star map. */
    void climb() {
        clock = Math.min(clock > 0 ? clock : HANDOFF, HANDOFF);
        heading = -1;
    }

    /** Ends the drop at once, the table showing; or the climb, the map showing. */
    void skip() {
        if (heading > 0) {
            clock = Math.max(clock, LOCK);
            hurried = true;
        } else {
            clock = 0;
            heading = 0;
        }
    }

    boolean active() {
        return heading != 0;
    }

    /** Whether the war table shows: before and after the drop, and as it lands. */
    boolean tableShows() {
        return heading == 0 || heading > 0 && clock >= HANDOFF;
    }

    /** How bright the star map under the drop shows, 0 to 1. */
    float mapLight() {
        if (heading == 0) {
            return 1;
        }
        return (1 - 0.5f * smooth(clock / FALL)) * (1 - veil());
    }

    /** Lands the drop on the war table's sector's hex grid: its origin's x and z and a hex's radius. */
    void grid(float[] sector) {
        System.arraycopy(sector, 0, grid, 0, 3);
    }

    /** Puts the globe over the star it rises from, in normalised device coordinates; null when it's off screen. */
    void star(@Nullable Vector2 device) {
        starSeen = device != null;
        if (device != null) {
            star.set(device);
        }
    }

    /**
     * Moves the drop {@code delta} seconds on; dropping, it waits at {@link #LOCK} until the table it lands on is
     * {@code ready}.
     */
    void advance(float delta, boolean ready) {
        if (heading == 0) {
            return;
        }
        time += delta;
        if (heading > 0) {
            clock += delta;
            if (!ready) {
                clock = Math.min(clock, LOCK);
            } else if (hurried) {
                clock = Math.max(clock, END - 0.25f);
                hurried = false;
            }
            if (clock >= END) {
                clock = 0;
                heading = 0;
            }
        } else {
            clock -= delta * CLIMB_SPEED;
            if (clock <= 0) {
                clock = 0;
                heading = 0;
            }
        }
    }

    /** Draws the drop over what's already drawn. */
    void render() {
        if (heading == 0) {
            return;
        }
        aim();
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        // What's under keeps its alpha: the screen stays opaque for captures read back from it
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ZERO, GL20.GL_ONE);
        float veil = veil();
        if (veil > 0) {
            veilShader.bind();
            veilShader.setUniformf("u_veil", 0.014f, 0.018f, 0.024f, veil);
            screen.render(veilShader, GL20.GL_TRIANGLES);
        }
        Gdx.gl.glBlendFuncSeparate(GL20.GL_ONE, GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_ONE);
        Vector3 colour = new Vector3(CYAN).lerp(GREEN, smooth((clock - 0.9f) / 0.6f));
        float landed = clock < HANDOFF ? 1 : 1 - smooth((clock - HANDOFF) / 0.4f);
        float flat = smooth((clock - 1) / 0.55f);
        float lines = 1 - smooth((clock - 1.45f) / 0.35f);
        float marker = smooth((clock - 0.15f) / 0.3f) * (clock < HANDOFF ? 1 : 1 - smooth((clock - HANDOFF) / 0.4f));
        if (lines > 0 || marker > 0) {
            float shift = starSeen ? 1 - smooth((clock - 0.6f) / 0.5f) : 0;
            lineShader.bind();
            lineShader.setUniformMatrix("u_projView", camera.combined);
            set(lineShader, "u_centre", 0, WarTableView.TABLE_TOP, 0);
            set(lineShader, "u_camera", camera.position.x, camera.position.y, camera.position.z);
            set(lineShader, "u_curve", (1 - flat) / GLOBE);
            set(lineShader, "u_flat", flat);
            set(lineShader, "u_spin", -1.3f * GLOBE * (1 - Interpolation.pow2Out.apply(Math.min(1, clock / FALL))));
            set(lineShader, "u_reveal", smooth(clock / 0.6f) * 1.15f);
            set(lineShader, "u_lines", lines);
            set(lineShader, "u_marker", marker);
            set(lineShader, "u_markerSize", Math.max(1, camera.position.dst(0, WarTableView.TABLE_TOP, 0) / 5));
            set(lineShader, "u_shift", star.x * shift, star.y * shift);
            set(lineShader, "u_time", time);
            set(lineShader, "u_colour", colour.x, colour.y, colour.z);
            globe.render(lineShader, GL20.GL_LINES);
        }
        float hexes = smooth((clock - 1.3f) / 0.3f) * landed;
        if (hexes > 0) {
            gridShader.bind();
            gridShader.setUniformMatrix("u_projView", camera.combined);
            set(gridShader, "u_grid", grid[0], grid[1], grid[2]);
            set(gridShader, "u_colour", GREEN.x, GREEN.y, GREEN.z);
            set(gridShader, "u_alpha", hexes);
            set(gridShader, "u_front", (clock - 1.3f) * 4);
            plane.render(gridShader, GL20.GL_TRIANGLES);
        }
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glDepthMask(true);
    }

    @Override
    public void dispose() {
        for (Disposable disposable : new Disposable[] { lineShader, gridShader, veilShader, globe, plane, screen }) {
            if (disposable != null) {
                disposable.dispose();
            }
        }
    }

    /** How dark the drop has made the screen, the map going out under it and the table coming in. */
    private float veil() {
        return clock < HANDOFF ? smooth((clock - 0.55f) / 0.6f) : 1 - smooth((clock - HANDOFF) / (END - HANDOFF));
    }

    /**
     * Puts the camera where the drop has it: high over the globe looking almost straight down, then falling onto the
     * marker to end where the war table's camera starts.
     */
    private void aim() {
        float high;
        float pitch;
        if (clock < FALL) {
            high = MathUtils.lerp(START_HIGH, HIGH, clock / FALL);
            pitch = START_PITCH;
        } else {
            float fall = Math.min(1, (clock - FALL) / (HANDOFF - FALL));
            high = (float) Math.exp(MathUtils.lerp((float) Math.log(HIGH),
                  (float) Math.log(WarTableView.DEFAULT_DISTANCE), Interpolation.pow3.apply(fall)));
            pitch = MathUtils.lerp(START_PITCH, WarTableView.DEFAULT_PITCH, Interpolation.smooth.apply(fall));
        }
        Vector3 centre = new Vector3(0, WarTableView.TABLE_TOP, 0);
        camera.position.set(0, MathUtils.sinDeg(pitch), MathUtils.cosDeg(pitch)).scl(high).add(centre);
        camera.up.set(0, 1, 0);
        camera.lookAt(centre);
        camera.near = high * 0.02f;
        camera.far = high * 4 + 10;
        camera.update();
    }

    /**
     * The globe's lines: its meridians and parallels every 15 degrees, each vertex where it lies on the globe as
     * lengths along it from the marker, revealed from the marker outward; then the marker, a ring with ticks round it
     * and a beam up from it, and a ring pulsing out from it.
     */
    private static Mesh globe() {
        FloatArray lines = new FloatArray();
        float step = MathUtils.PI / 12;
        for (int m = 0; m < 24; m++) {
            float lon = -MathUtils.PI + m * step;
            for (int j = 0; j < 36; j++) {
                float lat = -MathUtils.HALF_PI + j * MathUtils.PI / 36;
                surface(lines, lon, lat);
                surface(lines, lon, lat + MathUtils.PI / 36);
            }
        }
        for (int p = -5; p <= 5; p++) {
            float lat = p * step;
            for (int j = 0; j < 72; j++) {
                float lon = -MathUtils.PI + j * MathUtils.PI2 / 72;
                surface(lines, lon, lat);
                surface(lines, lon + MathUtils.PI2 / 72, lat);
            }
        }
        float ring = 0.08f;
        for (int kind = 1; kind <= 2; kind++) {
            for (int j = 0; j < 40; j++) {
                float a = j * MathUtils.PI2 / 40;
                float b = (j + 1) * MathUtils.PI2 / 40;
                vertex(lines, MathUtils.cos(a) * ring, MathUtils.sin(a) * ring, 0, 0, kind);
                vertex(lines, MathUtils.cos(b) * ring, MathUtils.sin(b) * ring, 0, 0, kind);
            }
        }
        for (int k = 0; k < 4; k++) {
            float a = k * MathUtils.HALF_PI;
            vertex(lines, MathUtils.cos(a) * ring * 1.4f, MathUtils.sin(a) * ring * 1.4f, 0, 0, 1);
            vertex(lines, MathUtils.cos(a) * ring * 2.1f, MathUtils.sin(a) * ring * 2.1f, 0, 0, 1);
        }
        vertex(lines, 0, 0, 0, 0, 1);
        vertex(lines, 0, 0, 0.45f, 0, 1);
        Mesh mesh = new Mesh(true, lines.size / 5, 0, new VertexAttributes(
              new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_surface"),
              new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_lift"),
              new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_order"),
              new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_kind")));
        mesh.setVertices(lines.toArray());
        return mesh;
    }

    /** A vertex of the globe's lines at {@code lon}, {@code lat} radians from the marker. */
    private static void surface(FloatArray into, float lon, float lat) {
        float away = (float) Math.acos(MathUtils.clamp(MathUtils.cos(lat) * MathUtils.cos(lon), -1, 1));
        vertex(into, lon * GLOBE, lat * GLOBE, 0, away / MathUtils.PI, 0);
    }

    private static void vertex(FloatArray into, float a, float b, float lift, float order, float kind) {
        into.add(a);
        into.add(b);
        into.add(lift);
        into.add(order);
        into.add(kind);
    }

    private static float smooth(float x) {
        return Interpolation.smooth.apply(MathUtils.clamp(x, 0, 1));
    }

    /** Sets {@code shader}'s uniform {@code name} if it reads it: its compiler drops those it doesn't. */
    private static void set(ShaderProgram shader, String name, float... values) {
        if (!shader.hasUniform(name)) {
            return;
        }
        switch (values.length) {
            case 1 -> shader.setUniformf(name, values[0]);
            case 2 -> shader.setUniformf(name, values[0], values[1]);
            default -> shader.setUniformf(name, values[0], values[1], values[2]);
        }
    }
}
