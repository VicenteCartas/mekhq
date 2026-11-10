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
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;

/**
 * Glowing lines a fixed number of pixels wide however near or far, which core profile GL lines can't be: each segment
 * is a ribbon turned to face the screen. A segment may carry light pulses flowing along it, from its first end to its
 * second, or be dashed. Drawn additively. Build them with {@link #add}, then {@link #upload}; GPU thread.
 */
final class Strokes implements Disposable {
    /** Floats per vertex: this end, the other end, colour, and side, width, along and flow. */
    private static final int FLOATS = 14;
    private static final int GROWTH = 6 * 64;

    private final FloatArray vertices = new FloatArray();
    private ShaderProgram shader;
    private Mesh mesh;
    private int count;

    void create() {
        shader = HoloTableView.compile(StarMapShaders.STROKE_VERTEX, StarMapShaders.STROKE_FRAGMENT);
        mesh = mesh(GROWTH);
    }

    /** Forgets every segment added. */
    void clear() {
        vertices.clear();
    }

    /** Whether no segment has been added since the last {@link #clear}. */
    boolean isEmpty() {
        return vertices.size == 0;
    }

    /**
     * Adds a segment from (x1, y1, z1) to (x2, y2, z2) in the map's world, {@code width} pixels wide in colour (r, g,
     * b) at {@code alpha}; {@code along} light years along its line at its start. A positive {@code flow} sends a
     * pulse along it every {@code flow} light years; a negative one dashes it every {@code -flow}.
     */
    void add(float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float alpha,
          float width, float along, float flow) {
        add(x1, y1, z1, x2, y2, z2, r, g, b, alpha, alpha, width, along, flow);
    }

    /** Adds a segment as the other {@code add} does, its strength fading from {@code alpha} to {@code alphaEnd}. */
    void add(float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float alpha,
          float alphaEnd, float width, float along, float flow) {
        float length = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
        float end = along + length;
        // The far end sees the segment the other way round, so its sides are the near end's negated
        corner(x1, y1, z1, x2, y2, z2, r, g, b, alpha, -1, width, along, flow);
        corner(x1, y1, z1, x2, y2, z2, r, g, b, alpha, 1, width, along, flow);
        corner(x2, y2, z2, x1, y1, z1, r, g, b, alphaEnd, 1, width, end, flow);
        corner(x2, y2, z2, x1, y1, z1, r, g, b, alphaEnd, 1, width, end, flow);
        corner(x1, y1, z1, x2, y2, z2, r, g, b, alpha, 1, width, along, flow);
        corner(x2, y2, z2, x1, y1, z1, r, g, b, alphaEnd, -1, width, end, flow);
    }

    private void corner(float x, float y, float z, float ox, float oy, float oz, float r, float g, float b,
          float alpha, float side, float width, float along, float flow) {
        vertices.addAll(x, y, z, ox, oy, oz, r, g, b, alpha, side, width, along, flow);
    }

    /** Sends what was added since the last {@link #clear} to the GPU. */
    void upload() {
        count = vertices.size / FLOATS;
        if (count == 0) {
            return;
        }
        if (count > mesh.getMaxVertices()) {
            mesh.dispose();
            mesh = mesh(Math.max(count, mesh.getMaxVertices() * 2));
        }
        mesh.setVertices(vertices.items, 0, count * FLOATS);
    }

    /**
     * Draws what was last uploaded at {@code fade} of its brightness, {@code time} seconds into the pulses, its
     * heights over the plane times {@code lift}.
     */
    void render(Camera camera, float fade, float time, float lift) {
        render(camera, fade, time, lift, Float.MAX_VALUE);
    }

    /**
     * Draws as {@link #render(Camera, float, float, float)} does, only as far along the strokes as {@code reveal}
     * light years, a bright front where they end.
     */
    void render(Camera camera, float fade, float time, float lift, float reveal) {
        if (count == 0 || fade <= 0) {
            return;
        }
        Gdx.gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        shader.bind();
        shader.setUniformMatrix("u_projView", camera.combined);
        shader.setUniformf("u_viewport", Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        shader.setUniformf("u_scale", Gdx.graphics.getBackBufferHeight() / (float) Math.max(1,
              Gdx.graphics.getHeight()));
        shader.setUniformf("u_fade", fade);
        shader.setUniformf("u_time", time);
        shader.setUniformf("u_lift", lift);
        shader.setUniformf("u_reveal", reveal);
        mesh.render(shader, GL20.GL_TRIANGLES, 0, count);
    }

    private static Mesh mesh(int size) {
        return new Mesh(false, size, 0, new VertexAttributes(VertexAttribute.Position(), VertexAttribute.Normal(),
              VertexAttribute.ColorUnpacked(),
              new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 4, "a_texCoord0", 0)));
    }

    @Override
    public void dispose() {
        if (shader != null) {
            shader.dispose();
        }
        if (mesh != null) {
            mesh.dispose();
        }
    }
}
