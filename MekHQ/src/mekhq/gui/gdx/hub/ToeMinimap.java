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

import java.util.function.BiConsumer;
import java.util.function.Predicate;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import jakarta.annotation.Nullable;
import mekhq.gui.gdx.hub.ToeLayout.Block;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;

/**
 * The whole TO&amp;E in a corner, small, with the part of it on screen outlined: for a force too big to see all at
 * once, where the player is in it. A press or a drag on it takes the camera there.
 */
final class ToeMinimap extends Actor {
    /** The most of the interface's pixels it takes across and down. */
    private static final float MOST_WIDE = 220;
    private static final float MOST_DEEP = 130;
    private static final float INSET = 6;
    private static final Color BACK = new Color(0.035f, 0.047f, 0.06f, 0.92f);
    private static final Color EDGE = new Color(0.45f, 0.72f, 0.95f, 0.6f);
    private static final Color NODE = new Color(0.45f, 0.72f, 0.95f, 0.38f);
    private static final Color CARD = new Color(0.45f, 0.72f, 0.95f, 0.8f);
    private static final Color SEEN = new Color(0.93f, 0.95f, 0.97f, 0.95f);
    private static final Color SEEN_FILL = new Color(0.93f, 0.95f, 0.97f, 0.12f);
    /** How much of itself a formation with none of the lit units shows. */
    private static final float DIMMED = 0.25f;

    private final TextureRegion white;
    private final BiConsumer<Float, Float> jump;
    private final Color tint = new Color();
    private @Nullable ToeLayout layout;
    private float[] bounds = new float[4];
    private @Nullable float[] seen;
    private @Nullable Predicate<Card> lit;
    /** Interface pixels to a cell of the table. */
    private float shrink = 1;

    /** Drawn with {@code white}; {@code jump} is handed the point on the table a press or a drag is over. */
    ToeMinimap(TextureRegion white, BiConsumer<Float, Float> jump) {
        this.white = white;
        this.jump = jump;
        setTouchable(Touchable.enabled);
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                aim(x, y);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                aim(x, y);
            }
        });
    }

    /** Draws {@code shown}, sized to fit the corner, as wide or deep as it is. */
    void show(@Nullable ToeLayout shown) {
        layout = shown;
        if (shown == null) {
            return;
        }
        bounds = shown.bounds();
        float wide = Math.max(bounds[2] - bounds[0], 1e-3f);
        float deep = Math.max(bounds[3] - bounds[1], 1e-3f);
        shrink = Math.min((MOST_WIDE - INSET * 2) / wide, (MOST_DEEP - INSET * 2) / deep);
        setSize(wide * shrink + INSET * 2, deep * shrink + INSET * 2);
    }

    /** Dims the formations with none of the units {@code filter} picks; null lights them all. */
    void filter(@Nullable Predicate<Card> filter) {
        lit = filter;
    }

    private boolean lit(Block block) {
        Predicate<Card> filter = lit;
        return filter == null || block.units().stream().anyMatch(filter);
    }

    /**
     * Outlines {@code on}, the part of the table on screen, as {@link ToeLayout#bounds}; whether that's short of all of
     * it, so the map has something to show.
     */
    boolean seen(@Nullable float[] on) {
        seen = on;
        return layout != null && on != null
              && (on[0] > bounds[0] + 1e-3f || on[1] > bounds[1] + 1e-3f || on[2] < bounds[2] - 1e-3f
                    || on[3] < bounds[3] - 1e-3f);
    }

    private void aim(float x, float y) {
        if (layout == null) {
            return;
        }
        // The table's z runs down the screen, the stage's y up it
        float tableX = bounds[0] + (x - INSET) / shrink;
        float tableZ = bounds[1] + (getHeight() - INSET - y) / shrink;
        jump.accept(Math.max(bounds[0], Math.min(tableX, bounds[2])), Math.max(bounds[1], Math.min(tableZ,
              bounds[3])));
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        ToeLayout shown = layout;
        if (shown == null) {
            return;
        }
        float alpha = getColor().a * parentAlpha;
        fill(batch, BACK, alpha, getX(), getY(), getWidth(), getHeight());
        outline(batch, EDGE, alpha, getX(), getY(), getWidth(), getHeight());
        for (Block block : shown.blocks()) {
            float[] rect = rect(block.x(), block.z(), block.x() + block.width(), block.z() + block.depth());
            float shade = lit(block) ? alpha : alpha * DIMMED;
            if (block.card()) {
                fill(batch, CARD, shade, rect[0], rect[1], rect[2], rect[3]);
            } else {
                fill(batch, NODE, shade, rect[0], rect[1], rect[2], rect[3]);
                outline(batch, EDGE, shade, rect[0], rect[1], rect[2], rect[3]);
            }
        }
        float[] on = seen;
        if (on != null) {
            float x0 = Math.max(on[0], bounds[0]);
            float z0 = Math.max(on[1], bounds[1]);
            float x1 = Math.min(on[2], bounds[2]);
            float z1 = Math.min(on[3], bounds[3]);
            if (x1 > x0 && z1 > z0) {
                float[] rect = rect(x0, z0, x1, z1);
                fill(batch, SEEN_FILL, alpha, rect[0], rect[1], rect[2], rect[3]);
                outline(batch, SEEN, alpha, rect[0], rect[1], rect[2], rect[3]);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** The table's {@code x0, z0} to {@code x1, z1} on the map: its bottom left on the stage, then its size. */
    private float[] rect(float x0, float z0, float x1, float z1) {
        return new float[] { getX() + INSET + (x0 - bounds[0]) * shrink,
                             getY() + getHeight() - INSET - (z1 - bounds[1]) * shrink,
                             Math.max(1, (x1 - x0) * shrink), Math.max(1, (z1 - z0) * shrink) };
    }

    private void fill(Batch batch, Color color, float alpha, float x, float y, float width, float height) {
        batch.setColor(tint.set(color).mul(1, 1, 1, alpha));
        batch.draw(white, x, y, width, height);
    }

    private void outline(Batch batch, Color color, float alpha, float x, float y, float width, float height) {
        fill(batch, color, alpha, x, y, width, 1);
        fill(batch, color, alpha, x, y + height - 1, width, 1);
        fill(batch, color, alpha, x, y, 1, height);
        fill(batch, color, alpha, x + width - 1, y, 1, height);
    }
}
