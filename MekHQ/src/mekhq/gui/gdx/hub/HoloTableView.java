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
import java.util.Map;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.Star;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;

/**
 * The command post's holotable in its dark room, the star map projected above it: the table lit by a dim key, two rim
 * lights and the hologram's own light, and the hologram drawn additively over it (a disc of rings, a beam from the
 * emitter, and the systems as glowing points in their owners' colours, tinted towards the projector's cyan).
 *
 * <p>The camera starts at a fixed view; dragging turns it and the wheel moves it in or out ({@link #input}).</p>
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
    private static final float FIELD_OF_VIEW = 42;
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
    private Texture white;
    private Texture flat;
    private float time;
    private float yaw = 35;
    private float pitch = 24;
    private float distance = 3.3f;
    private final InputProcessor orbit = new InputAdapter() {
        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            yaw -= Gdx.input.getDeltaX() * 0.25f;
            pitch = MathUtils.clamp(pitch + Gdx.input.getDeltaY() * 0.2f, 4, 80);
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            distance = MathUtils.clamp(distance * (1 + amountY * 0.08f), 1.2f, 8);
            return true;
        }
    };

    /** The table from the GLB file {@code glb}, with {@code chart} over it; none when null. */
    HoloTableView(Path glb, @Nullable StarChart chart) {
        this.glb = glb;
        this.chart = chart;
    }

    /** The camera's controls, for the screen showing the view to put behind its own. */
    InputProcessor input() {
        return orbit;
    }

    @Override
    public void create() {
        propShader = compile(HoloShaders.PROP_VERTEX, HoloShaders.PROP_FRAGMENT);
        starShader = compile(HoloShaders.STAR_VERTEX, HoloShaders.STAR_FRAGMENT);
        holoShader = compile(HoloShaders.HOLO_VERTEX, HoloShaders.HOLO_FRAGMENT);
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
        time += Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        Vector3 target = new Vector3(0, anchor.y * 0.9f, 0);
        float cosPitch = MathUtils.cosDeg(pitch);
        camera.position.set(MathUtils.sinDeg(yaw) * cosPitch, MathUtils.sinDeg(pitch), MathUtils.cosDeg(yaw) * cosPitch)
              .scl(distance).add(target);
        camera.up.set(Vector3.Y);
        camera.lookAt(target);
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
        drawHologram(flicker);

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
        propShader.setUniformf("u_fogDensity", 0.3f);
        propShader.setUniformf("u_glowPulse", 1.4f + 0.25f * MathUtils.sin(time * 2.2f));
        propShader.setUniformf("u_tableRadius", TABLE_RADIUS);
        propShader.setUniformf("u_floor", 0f);
        table.draw(propShader, EMITTER_GLOW);

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

    private void drawHologram(float flicker) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        Gdx.gl.glDepthMask(false);

        holoShader.bind();
        holoShader.setUniformMatrix("u_projView", camera.combined);
        holoShader.setUniformf("u_colour", CYAN);
        holoShader.setUniformf("u_time", time);
        holoShader.setUniformf("u_intensity", flicker);
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
            starShader.setUniformMatrix("u_model", hologram());
            starShader.setUniformf("u_camera", camera.position);
            starShader.setUniformf("u_time", time);
            starShader.setUniformf("u_pixels",
                  Gdx.graphics.getBackBufferHeight() / (2 * MathUtils.tanDeg(FIELD_OF_VIEW / 2)) * flicker);
            stars.render(starShader, GL20.GL_POINTS);
            Gdx.gl.glDisable(GL_PROGRAM_POINT_SIZE);
        }
    }

    /** The map's frame: over the anchor, turning slowly. */
    private Matrix4 hologram() {
        return model.setToTranslation(anchor.x, anchor.y + DISC_LIFT, anchor.z).rotate(Vector3.Y, time * 2.5f);
    }

    @Override
    public void dispose() {
        for (var resource : new com.badlogic.gdx.utils.Disposable[] { propShader, starShader, holoShader, table, floor,
              disc, beam, stars, white, flat }) {
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
