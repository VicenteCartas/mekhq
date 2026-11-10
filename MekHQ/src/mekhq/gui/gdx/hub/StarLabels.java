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

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import megamek.client.ui.gdx.UiTheme;
import mekhq.gui.model.view.StarMap;

/**
 * Writes the star map's system names beside their stars, outlined against the dark, in stage units so they follow
 * the GUI scale; a name that would overlap one already written is left out. GPU thread.
 */
final class StarLabels implements Disposable {
    private static final float SIZE = 12;
    private static final float BORDER = 1.5f;
    /** How far right of its star, and above, a name sits, in stage units. */
    private static final float OFFSET_X = 7;
    private static final float OFFSET_Y = 4;
    private static final float GAP = 3;
    private static final Color OUTLINE = new Color(0.01f, 0.015f, 0.03f, 0.85f);

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font;
    private final BitmapFont outline;
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector2 at = new Vector2();
    private final Color tint = new Color();
    /** The boxes written this frame: x, y, right, top. */
    private final FloatArray placed = new FloatArray();

    StarLabels(UiTheme theme) {
        font = theme.font("starmap-label", UiTheme.ROBOTO, SIZE);
        outline = theme.font("starmap-label-outline", UiTheme.ROBOTO, SIZE, BORDER);
    }

    /** Writes {@code view}'s names over the stage {@code stage} draws to. */
    void draw(StarMapView view, Stage stage) {
        placed.clear();
        batch.setProjectionMatrix(stage.getCamera().combined);
        batch.begin();
        view.labels((star, x, y, marked) -> write(stage, star, x, y, marked));
        batch.end();
    }

    private void write(Stage stage, StarMap.Star star, float x, float y, boolean marked) {
        stage.screenToStageCoordinates(at.set(x, y));
        layout.setText(font, star.name());
        float left = at.x + OFFSET_X;
        float top = at.y + OFFSET_Y + layout.height;
        float right = left + layout.width;
        float bottom = top - layout.height;
        for (int i = 0; i < placed.size; i += 4) {
            if (left < placed.get(i + 2) + GAP && right + GAP > placed.get(i) && bottom < placed.get(i + 3) + GAP
                  && top + GAP > placed.get(i + 1)) {
                return;
            }
        }
        placed.addAll(left, bottom, right, top);
        outline.setColor(OUTLINE);
        outline.draw(batch, star.name(), left - BORDER, top);
        int rgb = star.rgb();
        tint.set(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1);
        // Owner colours run dark; names stay readable lifted most of the way to white
        tint.lerp(Color.WHITE, marked ? 0.85f : 0.55f);
        tint.a = marked ? 1 : 0.8f;
        font.setColor(tint);
        font.draw(batch, star.name(), left, top);
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}
