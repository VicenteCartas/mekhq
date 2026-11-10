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

import java.time.LocalDate;
import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import megamek.client.ui.gdx.UiTheme;
import mekhq.gui.model.view.StarMap;

/**
 * Writes the star map's system names beside their stars, outlined against the dark, in stage units so they follow
 * the GUI scale: capitals larger, each at the strength the map fades it to. A name that would overlap one already
 * written gives way and fades out. GPU thread.
 */
final class StarLabels implements Disposable {
    /** The names' sizes: a realm's capital, a region's, and every other. */
    private static final float[] SIZES = { 16, 13.5f, 12 };
    private static final float BORDER = 1.5f;
    private static final float REALM_SIZE = 22;
    private static final float REALM_ALPHA = 0.5f;
    /** How far right of its star, and above, a name sits, in stage units. */
    private static final float OFFSET_X = 7;
    private static final float OFFSET_Y = 4;
    private static final float GAP = 3;
    /** A realm's emblem's size, how strong it shows against the realm's name, and its gap below the name. */
    private static final float EMBLEM_SIZE = 46;
    private static final float EMBLEM_ALPHA = 0.85f;
    private static final float EMBLEM_GAP = 6;
    private static final Color OUTLINE = new Color(0.01f, 0.015f, 0.03f, 0.85f);

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont[] fonts = new BitmapFont[SIZES.length];
    private final BitmapFont[] outlines = new BitmapFont[SIZES.length];
    private final BitmapFont realmFont;
    private final FactionEmblems emblems = new FactionEmblems();
    private int year;
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector2 at = new Vector2();
    private final Color tint = new Color();
    /** The boxes written this frame: x, y, right, top. */
    private final FloatArray placed = new FloatArray();
    /** The realms' names written this frame, which only the most prominent system names may overlap. */
    private final FloatArray realms = new FloatArray();

    StarLabels(UiTheme theme) {
        for (int i = 0; i < SIZES.length; i++) {
            fonts[i] = theme.font("starmap-label-" + i, UiTheme.ROBOTO, SIZES[i]);
            outlines[i] = theme.font("starmap-label-outline-" + i, UiTheme.ROBOTO, SIZES[i], BORDER);
        }
        realmFont = theme.font("starmap-realm", UiTheme.ROBOTO, REALM_SIZE);
    }

    /** Writes {@code view}'s names over the stage {@code stage} draws to. */
    void draw(StarMapView view, Stage stage) {
        placed.clear();
        realms.clear();
        emblems.upload();
        LocalDate date = view.shownDate();
        year = date == null ? 0 : date.getYear();
        batch.setProjectionMatrix(stage.getCamera().combined);
        batch.begin();
        view.realmNames((name, code, rgb, x, y, span, alpha) -> realm(stage, name, code, rgb, x, y, span, alpha));
        view.labels((star, x, y, tier, alpha, wanted) -> write(stage, star, x, y, tier, alpha, wanted));
        batch.end();
    }

    /**
     * Writes a realm's name in wide-tracked capitals centred on (x, y), faint, in its colour; it fades away when it
     * would run far past its territory. The faction's emblem, once read, hangs under the name.
     */
    private void realm(Stage stage, String name, String code, int rgb, float x, float y, float span, float alpha) {
        String text = name.toUpperCase(Locale.ROOT);
        stage.screenToStageCoordinates(at.set(x, y));
        float scale = stage.getWidth() / Math.max(1, Gdx.graphics.getWidth());
        float tracking = REALM_SIZE * 0.35f;
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            layout.setText(realmFont, text, i, i + 1, Color.WHITE, 0, Align.left, false, null);
            width += layout.width + (i + 1 < text.length() ? tracking : 0);
        }
        float room = span * scale * 2.2f;
        float fits = MathUtils.clamp((room - width * 0.6f) / (width * 0.5f), 0, 1);
        float strength = alpha * fits;
        if (strength < 0.01f) {
            return;
        }
        tint.set(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1);
        tint.lerp(Color.WHITE, 0.35f);
        tint.a = REALM_ALPHA * strength;
        realmFont.setColor(tint);
        float left = at.x - width / 2;
        // Below the realm's central system, clear of its capital's name
        float top = at.y - REALM_SIZE * 1.3f;
        if (strength > 0.3f) {
            realms.addAll(left, top - REALM_SIZE, left + width, top);
        }
        for (int i = 0; i < text.length(); i++) {
            layout.setText(realmFont, text, i, i + 1, tint, 0, Align.left, false, null);
            realmFont.draw(batch, layout, left, top);
            left += layout.width + tracking;
        }
        Texture emblem = year == 0 ? null : emblems.emblem(code, year);
        if (emblem != null) {
            float emblemTop = top - REALM_SIZE - EMBLEM_GAP;
            float emblemLeft = at.x - EMBLEM_SIZE / 2;
            if (strength > 0.3f) {
                realms.addAll(emblemLeft, emblemTop - EMBLEM_SIZE, emblemLeft + EMBLEM_SIZE, emblemTop);
            }
            tint.a = REALM_ALPHA * EMBLEM_ALPHA * strength;
            batch.setColor(tint);
            batch.draw(emblem, emblemLeft, emblemTop - EMBLEM_SIZE, EMBLEM_SIZE, EMBLEM_SIZE);
            batch.setColor(Color.WHITE);
        }
    }

    private boolean write(Stage stage, StarMap.Star star, float x, float y, int tier, float alpha, boolean wanted) {
        int size = switch (star.settled() ? star.capital() : 0) {
            case 3 -> 0;
            case 2 -> 1;
            default -> 2;
        };
        BitmapFont font = fonts[size];
        stage.screenToStageCoordinates(at.set(x, y));
        layout.setText(font, star.name());
        float left = at.x + OFFSET_X;
        float top = at.y + OFFSET_Y + layout.height;
        float right = left + layout.width;
        float bottom = top - layout.height;
        boolean free = wanted && clear(placed, left, bottom, right, top) && (tier <= 1 || clear(realms, left, bottom,
              right, top));
        if (free) {
            placed.addAll(left, bottom, right, top);
        }
        if (alpha < 0.01f) {
            return free;
        }
        boolean marked = tier == 0;
        float strength = alpha * alpha * (3 - 2 * alpha);
        BitmapFont outline = outlines[size];
        outline.setColor(OUTLINE.r, OUTLINE.g, OUTLINE.b, OUTLINE.a * strength);
        outline.draw(batch, star.name(), left - BORDER, top);
        int rgb = star.rgb();
        tint.set(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1);
        // Owner colours run dark; names stay readable lifted most of the way to white
        tint.lerp(Color.WHITE, marked ? 0.85f : (size == 0 ? 0.45f : 0.55f));
        tint.a = (marked || size == 0 ? 1 : 0.8f) * strength;
        font.setColor(tint);
        font.draw(batch, star.name(), left, top);
        return free;
    }

    /** Whether the box from (left, bottom) to (right, top) keeps clear of every box in {@code boxes}. */
    private static boolean clear(FloatArray boxes, float left, float bottom, float right, float top) {
        for (int i = 0; i < boxes.size; i += 4) {
            if (left < boxes.get(i + 2) + GAP && right + GAP > boxes.get(i) && bottom < boxes.get(i + 3) + GAP
                  && top + GAP > boxes.get(i + 1)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void dispose() {
        batch.dispose();
        emblems.dispose();
    }
}
