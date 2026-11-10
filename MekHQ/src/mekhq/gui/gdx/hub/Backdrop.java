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
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Disposable;

/**
 * The deep sky behind the star map: faint stars scattered on planes far beneath the galactic plane, so they drift
 * slower than the map as it pans, and dim nebulae on a plane deeper still, brightening coreward towards the galaxy's
 * heart. It only sets the scene; nothing in it is a system. GPU thread.
 */
final class Backdrop implements Disposable {
    /** How far beneath the plane each layer of stars lies, in light years, and how many stars it holds. */
    private static final float[] DEPTHS = { 400, 1100, 2600 };
    private static final int[] COUNTS = { 1800, 2600, 3400 };
    /** How far the layers spread either way, in light years. */
    private static final float SPREAD = 7000;
    private static final float NEBULA_DEPTH = 3400;
    private static final float NEBULA_SPREAD = 12000;
    private static final int POINT_FLOATS = 5;
    private static final int GL_PROGRAM_POINT_SIZE = 0x8642;

    private ShaderProgram starShader;
    private ShaderProgram nebulaShader;
    private Mesh stars;
    private Mesh nebula;

    void create() {
        starShader = HoloTableView.compile(StarMapShaders.BACKDROP_STAR_VERTEX, StarMapShaders.BACKDROP_STAR_FRAGMENT);
        nebulaShader = HoloTableView.compile(StarMapShaders.NEBULA_VERTEX, StarMapShaders.NEBULA_FRAGMENT);
        stars = stars();
        nebula = new Mesh(true, 4, 6, VertexAttribute.Position());
        float y = -NEBULA_DEPTH;
        float e = NEBULA_SPREAD;
        nebula.setVertices(new float[] { -e, y, -e, e, y, -e, e, y, e, -e, y, e });
        nebula.setIndices(new short[] { 0, 1, 2, 2, 3, 0 });
    }

    /** Draws the deep sky, additively, at {@code fade} of its brightness. */
    void render(Camera camera, float fade, float time, float pixelScale) {
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        nebulaShader.bind();
        nebulaShader.setUniformMatrix("u_projView", camera.combined);
        nebulaShader.setUniformf("u_fade", fade);
        nebulaShader.setUniformf("u_time", time);
        nebula.render(nebulaShader, GL20.GL_TRIANGLES);
        Gdx.gl.glEnable(GL_PROGRAM_POINT_SIZE);
        starShader.bind();
        starShader.setUniformMatrix("u_projView", camera.combined);
        starShader.setUniformf("u_fade", fade);
        starShader.setUniformf("u_time", time);
        starShader.setUniformf("u_scale", pixelScale);
        stars.render(starShader, GL20.GL_POINTS);
        Gdx.gl.glDisable(GL_PROGRAM_POINT_SIZE);
    }

    private static Mesh stars() {
        RandomXS128 random = new RandomXS128(3025);
        int total = 0;
        for (int count : COUNTS) {
            total += count;
        }
        float[] vertices = new float[total * POINT_FLOATS];
        int at = 0;
        for (int layer = 0; layer < DEPTHS.length; layer++) {
            for (int i = 0; i < COUNTS[layer]; i++) {
                vertices[at++] = MathUtils.lerp(-SPREAD, SPREAD, random.nextFloat());
                vertices[at++] = -DEPTHS[layer] * MathUtils.lerp(0.85f, 1.15f, random.nextFloat());
                vertices[at++] = MathUtils.lerp(-SPREAD, SPREAD, random.nextFloat());
                // Mostly dim, a few bright; a phase for the twinkle, and a tint from blue-white to amber
                float bright = (float) Math.pow(random.nextFloat(), 6) * 0.9f + 0.06f;
                vertices[at++] = bright * (1.2f - layer * 0.2f);
                vertices[at++] = random.nextFloat();
            }
        }
        Mesh mesh = new Mesh(true, total, 0, new VertexAttributes(VertexAttribute.Position(),
              new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")));
        mesh.setVertices(vertices);
        return mesh;
    }

    @Override
    public void dispose() {
        for (Disposable resource : new Disposable[] { starShader, nebulaShader, stars, nebula }) {
            if (resource != null) {
                resource.dispose();
            }
        }
    }
}
