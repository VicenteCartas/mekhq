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

import java.nio.ShortBuffer;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import jakarta.annotation.Nullable;
import mekhq.campaign.universe.territory.InfluenceMap;
import mekhq.gui.model.view.Borders;

/**
 * The realms' territory on the star map, as {@link Borders#land()} works it out for each date. Showing another date
 * turns the old territory into the new one realm by realm, so borders sweep across space as history moves. GPU thread.
 */
final class TerritoryField implements Disposable {
    /** Seconds one date's territory takes to turn into the next. */
    private static final float MORPH = 0.6f;

    private ShaderProgram shader;
    private @Nullable Texture palette;
    private int paletteRealms;
    /** Where a land is staged on its way to the GPU, kept for the next. */
    private @Nullable ShortBuffer staging;
    /** The date shown, or turned into; null until one is. */
    private @Nullable Layer to;
    /** The date turning into {@link #to}. */
    private @Nullable Layer from;
    /** How far {@link #from} has turned into {@link #to}, 0 to 1. */
    private float progress = 1;
    /** The realm in the spotlight, from 1, or 0 for none. */
    private int spot;
    private float spotIn;

    void create() {
        shader = HoloTableView.compile(StarMapShaders.TERRITORY_VERTEX, StarMapShaders.TERRITORY_FRAGMENT);
    }

    /** The borders shown, or being turned into; null until some are. */
    @Nullable Borders shown() {
        Layer showing = to;
        return showing == null ? null : showing.borders;
    }

    /**
     * Turns the territory into {@code borders}'. Turning already, it carries on from whichever date it is nearer, so
     * scrubbing quickly never jumps far.
     */
    void show(Borders borders) {
        palette(borders.realms());
        Layer next = new Layer(borders, stage(borders.land()));
        if (to == null) {
            to = next;
            progress = 1;
            return;
        }
        if (from != null && progress < 0.5f) {
            to.dispose();
        } else {
            if (from != null) {
                from.dispose();
            }
            from = to;
            progress = 0;
        }
        to = next;
    }

    /** Lights the realm {@code realm}, from 1 (0 for none), at {@code strength}, 0 to 1. */
    void spotlight(int realm, float strength) {
        spot = realm;
        spotIn = strength;
    }

    /** Moves the turning {@code delta} seconds on. */
    void update(float delta) {
        if (from == null) {
            return;
        }
        progress = Math.min(1, progress + delta / MORPH);
        if (progress >= 1) {
            from.dispose();
            from = null;
        }
    }

    /** Draws the territory under {@code projView}, at {@code alpha}, {@code time} seconds into the map's life. */
    void render(Matrix4 projView, float alpha, float time) {
        Layer target = to;
        if (target == null || palette == null || alpha <= 0.001f) {
            return;
        }
        Layer source = from == null ? target : from;
        float eased = progress * progress * (3 - 2 * progress);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shader.bind();
        shader.setUniformMatrix("u_projView", projView);
        palette.bind(2);
        source.bind(0);
        target.bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        shader.setUniformi("u_landA", 0);
        shader.setUniformi("u_landB", 1);
        shader.setUniformi("u_palette", 2);
        source.uniforms(shader, "A");
        target.uniforms(shader, "B");
        shader.setUniformf("u_blend", from == null ? 1 : eased);
        shader.setUniformf("u_alpha", alpha);
        shader.setUniformf("u_time", time);
        shader.setUniformi("u_spot", spot);
        shader.setUniformf("u_spotIn", spotIn);
        // Both dates cover the same systems, so either bounds the territory
        target.plane.render(shader, GL20.GL_TRIANGLES);
    }

    private ShortBuffer stage(InfluenceMap land) {
        short[] samples = land.samples();
        if (staging == null || staging.capacity() < samples.length) {
            staging = BufferUtils.newShortBuffer(samples.length);
        }
        staging.clear();
        staging.put(samples);
        staging.flip();
        return staging;
    }

    /** Keeps the realms' colours, by number, in the palette, adding those numbered since. */
    private void palette(List<Borders.Realm> realms) {
        if (palette != null && realms.size() <= paletteRealms) {
            return;
        }
        int count = Math.max(1, realms.size());
        Pixmap colours = new Pixmap(Math.min(count, 256), (count + 255) / 256, Pixmap.Format.RGBA8888);
        colours.setBlending(Pixmap.Blending.None);
        for (int i = 0; i < realms.size(); i++) {
            Borders.Realm realm = realms.get(i);
            colours.drawPixel(i % 256, i / 256, (realm.rgb() << 8) | ("IND".equals(realm.shortName()) ? 0x48 : 0xFF));
        }
        if (palette != null) {
            palette.dispose();
        }
        palette = new Texture(colours);
        palette.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        colours.dispose();
        paletteRealms = realms.size();
    }

    @Override
    public void dispose() {
        for (Disposable resource : new Disposable[] { shader, palette, to, from }) {
            if (resource != null) {
                resource.dispose();
            }
        }
        to = null;
        from = null;
    }

    /** One date's land on the GPU, and the plane it is drawn on. */
    private static final class Layer implements Disposable {
        private final Borders borders;
        private final InfluenceMap land;
        private final int texture;
        private final Mesh plane;

        Layer(Borders borders, ShortBuffer samples) {
            this.borders = borders;
            land = borders.land();
            texture = Gdx.gl.glGenTexture();
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
            Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
            Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
            Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
            Gdx.gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
            Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 2);
            Gdx.gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL30.GL_RGBA16I, land.width(), land.height(), 0,
                  GL30.GL_RGBA_INTEGER, GL20.GL_SHORT, samples);
            Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 4);
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
            float west = land.west();
            float south = land.south();
            float east = west + land.texel() * land.width();
            float north = south + land.texel() * land.height();
            plane = new Mesh(true, 6, 0, VertexAttribute.Position());
            plane.setVertices(new float[] { west, 0, -south, east, 0, -south, east, 0, -north, west, 0, -south,
                                            east, 0, -north, west, 0, -north });
        }

        void bind(int unit) {
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
        }

        void uniforms(ShaderProgram program, String which) {
            program.setUniformf("u_origin" + which, land.west(), land.south());
            program.setUniformf("u_texel" + which, land.texel());
            program.setUniformi("u_size" + which, land.width(), land.height());
        }

        @Override
        public void dispose() {
            Gdx.gl.glDeleteTexture(texture);
            plane.dispose();
        }
    }
}
