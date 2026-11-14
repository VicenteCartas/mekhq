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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import jakarta.annotation.Nullable;
import megamek.client.ui.gdx.UiKit;
import megamek.client.ui.gdx.UiTheme;
import megamek.common.Configuration;
import megamek.common.loaders.MekSummary;
import megamek.common.units.UnitRole;
import mekhq.campaign.force.FormationSlot;
import mekhq.campaign.force.FormationTemplate;
import mekhq.gui.gdx.hub.ToeLayout.Block;
import mekhq.gui.gdx.hub.ToeLayout.Slot;
import mekhq.gui.gdx.hub.ToeLayout.Vacancy;
import mekhq.gui.gdx.hub.ToeSnapshot.Card;
import mekhq.gui.gdx.hub.ToeSnapshot.Condition;
import mekhq.gui.gdx.hub.ToeSnapshot.Crew;
import mekhq.gui.gdx.hub.ToeSnapshot.Group;
import mekhq.gui.gdx.hub.ToeSnapshot.Kind;
import mekhq.gui.gdx.hub.ToeSnapshot.Work;

/**
 * A developer's look at the TO&amp;E without a campaign: a mercenary regiment of three BattleMek battalions with
 * armour, aerospace, infantry and DropShips, spread over its main force and two bases, some of it deployed, some on the
 * move, damaged, under repair, mothballed or uncrewed, opened on the TO&amp;E table in a window. Run through the
 * toeDemo task; {@code -Pcapture} instead writes a few shots to build/toe and exits; {@code small} shows a lone
 * mercenary company instead.
 */
public final class ToeDemo {
    private static final long SEED = 3025_06_01L;
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    private static final String UNITS = "data/images/units/";
    private static final String[] MEKS = { "Atlas_7A", "marauder_3b", "Warhammer_10CT", "Catapult_A1-Col",
          "Jenner_A", "Griffin_1A", "Wolverine_10D", "Centurion_AH", "Hunchback_4H", "Locust_1E", "Commando_1A",
          "Archer_1A", "BattleMaster_10S", "Awesome_10KM", "Thunderbolt_10M", "Crusader_3L", "Valkyrie_QD1",
          "Dervish_1S", "Trebuchet_3C", "Zeus_10WB", "Victor_10L", "Orion_1C", "Javelin_10A", "Spider_10K",
          "Panther_12A", "Firestarter_81X", "Ostscout_10CS", "Quickdraw_4Gh", "Grasshopper_5Hh", "Cicada_3C",
          "Vindicator_1AA" };
    private static final double[] MEK_TONS = { 100, 75, 70, 65, 35, 55, 55, 50, 50, 20, 25, 70, 85, 80, 65, 65, 30,
                                               55, 50, 80, 80, 75, 30, 30, 35, 35, 35, 60, 70, 40, 45 };
    private static final String[] VEHICLES = { "Bulldog", "Condor", "Demolisher", "Ferret", "Galleon_104" };
    private static final double[] VEHICLE_TONS = { 60, 50, 80, 5, 30 };
    private static final String[] PILOTS = { "Capt. Mara Voss", "Lt. Dalen Okafor", "Sgt. Ines Kade",
          "MekWarrior Tobin Hale", "MekWarrior Petra Lindqvist", "Lt. Sora Marchetti", "Sgt. Yusuf Brandt",
          "MekWarrior Aiko Renn", "MekWarrior Caleb Dorne", "Sgt. Neve Castellan", "Lt. Oren Valcourt",
          "MekWarrior Lio Strand" };
    private static final String[] COMPANIES = { "Alpha Company", "Bravo Company", "Charlie Company" };
    private static final String[] LANCES = { "Command Lance", "Battle Lance", "Striker Lance", "Fire Lance",
          "Recon Lance" };
    /** Each battalion's camouflage, multiplied over the art's greys. */
    private static final int[][] CAMO = { { 120, 140, 160 }, { 150, 118, 92 }, { 104, 132, 104 },
                                          { 132, 132, 140 } };
    private static final Map<String, BufferedImage> ICONS = new HashMap<>();

    private ToeDemo() {
    }

    public static void main(String[] args) {
        boolean capture = List.of(args).contains("capture");
        boolean small = List.of(args).contains("small");
        ToeSnapshot snapshot = small ? company(new Random(SEED)) : regiment(new Random(SEED));

        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("TO&E demo");
        configuration.setWindowedMode(WIDTH, HEIGHT);
        configuration.setInitialVisible(!capture);
        configuration.useVsync(true);
        configuration.setForegroundFPS(capture ? 60 : 0);
        configuration.setBackBufferConfig(8, 8, 8, 8, 24, 8, 4);
        configuration.disableAudio(true);
        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 4, 3);
        new Lwjgl3Application(new ApplicationAdapter() {
            private ToeTable table;
            private int frames;
            private @Nullable Block pointed;
            /** The force as the table has it, moved about by its drags. */
            private ToeSnapshot current = snapshot;
            /** The scripted drag: the unit carried and where it goes, and where it's refused. */
            private @Nullable Slot carried;
            private @Nullable Block goal;
            private @Nullable Block refused;
            /** Where the scripted look at a formation's slots points. */
            private @Nullable Vector2 slotted;
            private @Nullable Block filling;

            @Override
            public void create() {
                UiTheme theme = new UiTheme(Configuration.fontsDir());
                UiKit ui = new UiKit(theme.skin);
                table = new ToeTable(() -> Gdx.app.exit(), moves -> {
                    current = ToeMoves.applied(current, moves);
                    table.refresh(current);
                }, shaping -> {
                    List<FormationTemplate> kept = new ArrayList<>(current.templates());
                    if (shaping.keep()) {
                        kept.removeIf(template -> template.name().equalsIgnoreCase(shaping.template()));
                        kept.add(new FormationTemplate(shaping.template(), shaping.slots(), shaping.rule()));
                    }
                    current = new ToeSnapshot(current.force(), authorized(current.root(), shaping), current.unassigned(),
                          current.bases(), kept, current.summaries());
                    table.refresh(current);
                });
                table.create(ui, 1);
                table.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                table.open(snapshot);
                Gdx.input.setInputProcessor(table.input());
            }

            @Override
            public void resize(int width, int height) {
                table.resize(width, height);
            }

            @Override
            public void render() {
                table.render(Math.min(Gdx.graphics.getDeltaTime(), 0.1f));
                frames++;
                if (!capture) {
                    return;
                }
                String prefix = small ? "small-" : "";
                switch (frames) {
                    case 20 -> shot(prefix + "rising");
                    case 150 -> shot(prefix + "overview");
                    // Steps in on the middle twice, from where it opened
                    case 160 -> zoom(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, -1);
                    case 260 -> {
                        Slot slot = slotNear();
                        if (slot != null) {
                            Vector2 at = table.onScreen(slot);
                            table.input().mouseMoved(Math.round(at.x), Math.round(at.y));
                        }
                    }
                    case 270 -> shot(prefix + "close");
                    case 280 -> zoom(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, -1);
                    case 380 -> shot(prefix + "closer");
                    case 390 -> table.input().keyDown(Input.Keys.HOME);
                    case 400 -> table.narrow(2);
                    case 500 -> shot(prefix + "base");
                    case 510 -> table.narrow(table.view() == null ? 0 : lastPlace());
                    case 610 -> shot(prefix + "transit");
                    case 620 -> table.narrow(0);
                    case 625 -> table.input().keyDown(Input.Keys.L);
                    case 720 -> shot(prefix + "formationLabels");
                    case 725 -> table.input().keyDown(Input.Keys.L);
                    case 735 -> shot(prefix + "noLabels");
                    // Steps all the way out, then in a step at a time, each opening the next tier of formations
                    case 740 -> table.input().keyDown(Input.Keys.L);
                    case 745 -> zoom(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, 10);
                    case 860 -> shot(prefix + "fold0");
                    case 865 -> zoom(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, -1);
                    case 960 -> shot(prefix + "fold1");
                    case 965 -> zoom(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, -1);
                    case 1060 -> shot(prefix + "fold2");
                    case 1065 -> zoom(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, -1);
                    case 1160 -> shot(prefix + "fold3");
                    // Back home, the force on its top formations' cards, and opens one
                    case 1165 -> table.input().keyDown(Input.Keys.HOME);
                    case 1260 -> shot(prefix + "home");
                    case 1265 -> {
                        pointed = cardNear();
                        if (pointed != null) {
                            Vector2 at = table.onScreen(pointed, 0.5f, 0.85f);
                            table.input().mouseMoved(Math.round(at.x), Math.round(at.y));
                        }
                    }
                    case 1275 -> shot(prefix + "cardHover");
                    // On a unit's run of the card's readiness strip, that unit's own card shows
                    case 1276 -> {
                        if (pointed != null) {
                            Vector2 at = table.onScreen(pointed, 0.5f,
                                  (ToeLayout.CARD_STRIP_FROM + ToeLayout.CARD_STRIP_TO) / 2);
                            table.input().mouseMoved(Math.round(at.x), Math.round(at.y));
                        }
                    }
                    case 1279 -> shot(prefix + "stripHover");
                    case 1280 -> {
                        if (pointed != null) {
                            Vector2 at = table.onScreen(pointed);
                            table.input().touchDown(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.LEFT);
                            table.input().touchUp(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.LEFT);
                        }
                    }
                    case 1380 -> shot(prefix + "cardOpened");
                    // Opens a second card, the one nearest the middle now, where it stands too
                    case 1385 -> {
                        Block next = cardNear();
                        if (next != null) {
                            Vector2 at = table.onScreen(next, 0.5f, 0.25f);
                            table.input().mouseMoved(Math.round(at.x), Math.round(at.y));
                            table.input().touchDown(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.LEFT);
                            table.input().touchUp(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.LEFT);
                        }
                    }
                    case 1480 -> shot(prefix + "cardOpened2");
                    // Steps in until the units show, carries one over the formations it may go to and one it may
                    // not, then drops it
                    case 1485 -> zoom(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2, -10);
                    case 1580 -> {
                        carried = slotNear();
                        if (carried != null) {
                            goal = blockNear(carried, false);
                            refused = blockNear(carried, true);
                            Vector2 at = table.onScreen(carried);
                            table.input().touchDown(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.LEFT);
                        }
                    }
                    case 1581, 1583, 1585, 1587, 1589 -> {
                        if (carried != null && goal != null) {
                            dragToward(carried, goal, (frames - 1579) / 10f);
                        }
                    }
                    case 1595 -> shot(prefix + "dragTarget");
                    case 1600 -> {
                        if (refused != null) {
                            Vector2 at = table.onScreen(refused);
                            table.input().touchDragged(Math.round(at.x), Math.round(at.y), 0);
                        }
                    }
                    case 1610 -> {
                        if (refused != null) {
                            shot(prefix + "dragRefused");
                        }
                    }
                    case 1615 -> {
                        if (goal != null) {
                            Vector2 at = table.onScreen(goal);
                            table.input().touchDragged(Math.round(at.x), Math.round(at.y), 0);
                            table.input().touchUp(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.LEFT);
                        }
                    }
                    case 1715 -> shot(prefix + "dropped");
                    // Steps back, then in on a formation short of what it's authorized: its vacant slots, one
                    // pointed at, a unit carried toward one it would fill, and the formation's slot editor
                    case 1720 -> table.input().keyDown(Input.Keys.HOME);
                    case 1820 -> {
                        Block lacking = shortNear();
                        if (lacking != null) {
                            slotted = table.onScreen(lacking);
                            zoom(Math.round(slotted.x), Math.round(slotted.y), -10);
                        }
                    }
                    // Its head pointed at: how it stands against its rule, and what it falls short of
                    case 1924 -> {
                        Block lacking = shortNear();
                        if (lacking != null) {
                            Vector2 at = table.onScreen(lacking, 0.3f,
                                  lacking.card() ? 0.5f : lacking.head() / 2 / lacking.depth());
                            table.input().mouseMoved(Math.round(at.x), Math.round(at.y));
                        }
                    }
                    case 1928 -> shot(prefix + "ruleHover");
                    case 1930 -> {
                        Vacancy vacancy = slotted == null ? null : vacancyNear(slotted);
                        if (vacancy != null) {
                            slotted = table.onScreen(vacancy);
                            table.input().mouseMoved(Math.round(slotted.x), Math.round(slotted.y));
                        }
                    }
                    case 1940 -> shot(prefix + "vacancy");
                    case 1945 -> {
                        Vacancy vacancy = slotted == null ? null : vacancyNear(slotted);
                        carried = vacancy == null ? null : filler(vacancy);
                        filling = vacancy == null ? null : vacancy.block();
                        if (carried != null) {
                            Vector2 at = table.onScreen(carried);
                            table.input().touchDown(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.LEFT);
                        }
                    }
                    case 1946, 1948, 1950, 1952, 1954 -> {
                        if (carried != null && filling != null) {
                            dragToward(carried, filling, (frames - 1944) / 10f);
                        }
                    }
                    case 1970 -> shot(prefix + "dragFits");
                    case 1975 -> {
                        table.view().cancelDrag();
                        if (filling != null) {
                            Vector2 at = table.onScreen(filling);
                            table.input().touchDown(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.RIGHT);
                            table.input().touchUp(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.RIGHT);
                        }
                    }
                    case 1995 -> shot(prefix + "slotEditor");
                    case 1996 -> table.choose(table.choice("Level II"));
                    case 1999 -> shot(prefix + "slotTemplate");
                    // Then the Recon rule: what it asks for under its name, the slots kept as they were
                    case 2000 -> table.choose(table.choice("Recon"));
                    case 2003 -> shot(prefix + "slotRule");
                    // Closes the editor and clicks the vacant slot instead: the picker, then the first unit it offers
                    case 2004 -> table.input().keyDown(Input.Keys.ESCAPE);
                    case 2005 -> {
                        Vacancy vacancy = slotted == null ? null : vacancyNear(slotted);
                        if (vacancy != null) {
                            Vector2 at = table.onScreen(vacancy);
                            table.input().touchDown(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.LEFT);
                            table.input().touchUp(Math.round(at.x), Math.round(at.y), 0, Input.Buttons.LEFT);
                        }
                    }
                    case 2030 -> shot(prefix + "fillPicker");
                    case 2035 -> {
                        ToeTable.Filling picker = table.filling();
                        if (picker != null && !picker.candidates().isEmpty()) {
                            table.fillWith(picker.candidates().get(0));
                        }
                    }
                    case 2090 -> shot(prefix + "filled");
                    // Back home, lighting only the damaged from the header's count of them
                    case 2095 -> table.input().keyDown(Input.Keys.HOME);
                    case 2190 -> table.showOnly(ToeTable.Status.DAMAGED);
                    case 2290 -> {
                        shot(prefix + "statusFilter");
                        Gdx.app.exit();
                    }
                    default -> {
                    }
                }
            }

            private void zoom(int x, int y, int steps) {
                table.input().mouseMoved(x, y);
                for (int i = 0; i < Math.abs(steps); i++) {
                    table.input().scrolled(0, Math.signum(steps));
                }
            }

            /** The unit nearest the middle of the screen, to point at. */
            private @Nullable Slot slotNear() {
                Slot best = null;
                float bestDistance = Float.MAX_VALUE;
                for (Card card : snapshot.allUnits()) {
                    Slot slot = table.slotOf(card);
                    if (slot == null) {
                        continue;
                    }
                    Vector2 at = table.onScreen(slot);
                    float distance = at.dst(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = slot;
                    }
                }
                return best;
            }

            /** Drags the press under way {@code share} of the way from {@code from} to {@code to}. */
            private void dragToward(Slot from, Block to, float share) {
                Vector2 start = table.onScreen(from);
                Vector2 end = table.onScreen(to);
                Vector2 at = start.lerp(end, share);
                table.input().touchDragged(Math.round(at.x), Math.round(at.y), 0);
            }

            /**
             * The formation on screen nearest {@code slot}'s that it could be dropped into, or, {@code locked}, one
             * it couldn't because it's deployed; well in from the screen's edges, so the drag doesn't scroll.
             */
            private @Nullable Block blockNear(Slot slot, boolean locked) {
                Block best = null;
                float bestDistance = Float.MAX_VALUE;
                Vector2 from = table.onScreen(slot);
                int width = Gdx.graphics.getWidth();
                int height = Gdx.graphics.getHeight();
                for (Block block : table.blocks()) {
                    if (block.group() == null || block.key() == slot.block().key() || block.group().deployed() != locked
                          || !block.group().children().isEmpty()) {
                        continue;
                    }
                    Vector2 at = table.onScreen(block);
                    if (at.x < width * 0.12f || at.x > width * 0.88f || at.y < height * 0.2f
                          || at.y > height * 0.85f) {
                        continue;
                    }
                    float distance = at.dst(from);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = block;
                    }
                }
                return best;
            }

            /** The folded formation nearest the middle of the screen, to point at. */
            private @Nullable Block cardNear() {
                Block best = null;
                float bestDistance = Float.MAX_VALUE;
                for (Block card : table.cards()) {
                    float distance = table.onScreen(card).dst(Gdx.graphics.getWidth() / 2f,
                          Gdx.graphics.getHeight() / 2f);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = card;
                    }
                }
                return best;
            }

            /** The formation on the table with a vacant slot nearest the middle of the screen. */
            private @Nullable Block shortNear() {
                Block best = null;
                float bestDistance = Float.MAX_VALUE;
                for (Block block : table.blocks()) {
                    if (block.group() == null || ToeSlots.tally(block.group())[0] == 0
                          || !block.card() && ToeSlots.of(block.group()).vacant().isEmpty()) {
                        continue;
                    }
                    float distance = table.onScreen(block).dst(Gdx.graphics.getWidth() / 2f,
                          Gdx.graphics.getHeight() / 2f);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = block;
                    }
                }
                return best;
            }

            private @Nullable Vacancy vacancyNear(Vector2 point) {
                Vacancy best = null;
                float bestDistance = Float.MAX_VALUE;
                for (Vacancy vacancy : table.vacancies()) {
                    float distance = table.onScreen(vacancy).dst(point);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = vacancy;
                    }
                }
                return best;
            }

            /** The unit on screen from another formation nearest {@code vacancy} that would fill it. */
            private @Nullable Slot filler(Vacancy vacancy) {
                Slot best = null;
                float bestDistance = Float.MAX_VALUE;
                Vector2 to = table.onScreen(vacancy);
                for (Card card : current.allUnits()) {
                    Slot slot = table.slotOf(card);
                    if (slot == null || slot.block().key() == vacancy.block().key()
                          || !vacancy.slot().admits(card.fit()) || card.deployed()) {
                        continue;
                    }
                    float distance = table.onScreen(slot).dst(to);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = slot;
                    }
                }
                return best;
            }

            private int lastPlace() {
                return ToeTable.places(snapshot).size() - 1;
            }
        }, configuration);
    }

    private static void shot(String name) {
        File out = new File("build/toe");
        out.mkdirs();
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
        // Translucent layers like the editor's scrim lower the framebuffer's alpha, which the window ignores
        ByteBuffer pixels = pixmap.getPixels();
        for (int alpha = 3; alpha < pixels.limit(); alpha += 4) {
            pixels.put(alpha, (byte) 0xFF);
        }
        PixmapIO.writePNG(Gdx.files.absolute(new File(out, name + ".png").getAbsolutePath()), pixmap, -1, true);
        pixmap.dispose();
    }

    /** A regiment of three BattleMek battalions and its support, at its main force and two bases. */
    static ToeSnapshot regiment(Random random) {
        Ids ids = new Ids();
        Set<UUID> unknown = new HashSet<>();
        Map<UUID, UnitRole> roles = new HashMap<>();
        List<Group> arms = new ArrayList<>();
        String[] battalions = { "1st Battalion", "2nd Battalion", "3rd Battalion" };
        for (int b = 0; b < battalions.length; b++) {
            int place = b == 1 ? 1 : 0;
            List<Group> companies = new ArrayList<>();
            for (int c = 0; c < COMPANIES.length; c++) {
                List<Group> lances = new ArrayList<>();
                for (int l = 0; l < 3; l++) {
                    boolean deployed = b == 0 && c == 1 && l == 1;
                    boolean transit = b == 2 && c == 2;
                    int at = transit ? 2 : place;
                    List<Card> units = meks(random, 4, b, at, deployed, transit);
                    List<FormationSlot> slots = b == 0 && c == 0 && l == 0
                          ? matched(units, new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.MEDIUM))
                          : b == 0 && c == 0 && l == 2 ? misplaced(units, 1) : matched(units);
                    lances.add(new Group(ids.next(), LANCES[l], icon("BattleMek (Line)", "(04) Lance"), List.of(),
                          units, deployed, slots, -1, null, b == 0 && c == 0 && l == 0 ? "Command" : null));
                }
                companies.add(new Group(ids.next(), COMPANIES[c], icon("BattleMek (Center)", "(05) Company"),
                      lances, List.of(), false, List.of()));
            }
            arms.add(new Group(ids.next(), battalions[b], icon("BattleMek (Center)", "(06) Battalion"), companies,
                  meks(random, 4, b, place, false, false), false, List.of()));
        }
        // The first armour lance's designs MegaMek doesn't know; the last is one Scout or Striker short of its rule
        List<Group> armour = new ArrayList<>();
        for (int l = 0; l < 3; l++) {
            List<Card> units = vehicles(random, 4, 3, 0);
            if (l == 0) {
                units.forEach(card -> unknown.add(card.id()));
            } else if (l == 2) {
                shortOfScouts(units, roles);
            }
            armour.add(new Group(ids.next(), "Armor Lance " + (l + 1), icon("Assault", "(04) Lance"), List.of(),
                  units, false, open(FormationSlot.Kind.VEHICLE, 4), -1, null,
                  l == 0 ? "Fire" : l == 2 ? "Security" : null));
        }
        arms.add(new Group(ids.next(), "Armored Company", icon("Assault", "(05) Company"), armour, List.of(), false, List.of()));
        List<Group> wing = new ArrayList<>();
        for (int f = 0; f < 3; f++) {
            wing.add(new Group(ids.next(), "Flight " + (f + 1), icon("Aerospace", "(03) Team"), List.of(),
                  defaults(random, "asf", Kind.AEROSPACE, 50, 2, f == 2 ? 2 : 0), false, List.of(), 0));
        }
        arms.add(new Group(ids.next(), "Aerospace Wing", icon("Aerospace", "(04) Lance"), wing, List.of(), false, List.of()));
        List<Group> foot = List.of(
              new Group(ids.next(), "Jump Platoon", icon("Battle Armor", "(04) Lance"), List.of(),
                    defaults(random, "default_infantry", Kind.INFANTRY, 3, 2, 0), false, List.of()),
              new Group(ids.next(), "Elemental Point", icon("Battle Armor", "(03) Team"), List.of(),
                    defaults(random, "default_ba", Kind.BATTLE_ARMOR, 5, 2, 0), false, List.of(), 0));
        arms.add(new Group(ids.next(), "Infantry Detachment", icon("Battle Armor", "(05) Company"), foot, List.of(),
              false, List.of()));
        arms.add(new Group(ids.next(), "Transport Squadron", icon("DropShip", "(04) Lance"), List.of(),
              defaults(random, "dropship_spheroid", Kind.LARGE_CRAFT, 3500, 2, 0), false, List.of()));
        arms.add(new Group(ids.next(), "Replacement Lance", icon("BattleMek (Line)", "(04) Lance"), List.of(),
              List.of(), false, open(FormationSlot.Kind.MEK, 4)));
        Group root = new Group(ids.next(), "Ashfall Lancers", icon("Headquarters", "(07) Regiment"), arms,
              meks(random, 4, 3, 0, false, false), false, List.of());
        List<Card> unassigned = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int kind = random.nextInt(MEKS.length);
            unassigned.add(card(MEKS[kind], "meks/" + MEKS[kind], Kind.MEK, MEK_TONS[kind], 3, "",
                  Condition.SOUND, i < 2 ? Work.MOTHBALLING : Work.MOTHBALLED, Crew.NONE, false, false, i < 4 ? 0 : 1));
        }
        unassigned.add(card(MEKS[3], "meks/" + MEKS[3], Kind.MEK, MEK_TONS[3], 3, "", Condition.SALVAGE, Work.NONE,
              Crew.NONE, false, false, 0));
        unassigned.add(card(VEHICLES[2], "vehicles/" + VEHICLES[2], Kind.VEHICLE, VEHICLE_TONS[2], 3, "",
              Condition.INOPERABLE, Work.REPAIR, Crew.NONE, false, false, 0));
        return new ToeSnapshot("Ashfall Lancers", root, unassigned, List.of("Kestrel Station", "Corvin Yards"),
              KEPT, designs(root, unassigned, unknown, roles));
    }

    /** The templates the demo's campaigns keep: a player's own mixed lance, and a recon lance meant to meet its rule. */
    private static final List<FormationTemplate> KEPT = List.of(
          new FormationTemplate("Ball of Death", List.of(new FormationSlot(FormationSlot.Kind.MEK,
                FormationSlot.Weight.ASSAULT), new FormationSlot(FormationSlot.Kind.VEHICLE, FormationSlot.Weight.ANY),
                new FormationSlot(FormationSlot.Kind.AEROSPACE, FormationSlot.Weight.ANY)), null),
          new FormationTemplate("Scout Lance", List.of(new FormationSlot(FormationSlot.Kind.MEK,
                FormationSlot.Weight.LIGHT), new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.LIGHT),
                new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.MEDIUM),
                new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.MEDIUM)), "Recon"));

    /**
     * A lone mercenary company: three lances, a lance short of pilots, and a pair of spare Meks; its command lance a
     * Scout or Striker short of its rule, its last lance meant to meet the Command rule.
     */
    static ToeSnapshot company(Random random) {
        Ids ids = new Ids();
        Map<UUID, UnitRole> roles = new HashMap<>();
        List<Group> lances = new ArrayList<>();
        for (int l = 0; l < 3; l++) {
            List<Card> units = meks(random, 4, 0, 0, false, false);
            List<FormationSlot> slots = l == 0
                  ? matched(units, new FormationSlot(FormationSlot.Kind.MEK, FormationSlot.Weight.LIGHT))
                  : l == 1 ? misplaced(units, 0) : List.of();
            if (l == 0) {
                shortOfScouts(units, roles);
            }
            lances.add(new Group(ids.next(), LANCES[l], icon("BattleMek (Line)", "(04) Lance"), List.of(), units,
                  false, slots, -1, null, l == 0 ? "Security" : l == 2 ? "Command" : null));
        }
        Group root = new Group(ids.next(), "Voss's Raiders", icon("BattleMek (Center)", "(05) Company"), lances,
              List.of(), false, List.of());
        List<Card> unassigned = List.of(
              card(MEKS[9], "meks/" + MEKS[9], Kind.MEK, MEK_TONS[9], 0, "", Condition.HEAVY, Work.REPAIR, Crew.NONE,
                    false, false, 0),
              card(MEKS[16], "meks/" + MEKS[16], Kind.MEK, MEK_TONS[16], 0, "", Condition.SOUND, Work.MOTHBALLED,
                    Crew.NONE, false, false, 0));
        return new ToeSnapshot("Voss's Raiders", root, unassigned, List.of(), KEPT,
              designs(root, unassigned, Set.of(), roles));
    }

    /** The roles a unit of each weight class mostly has, the lightest first. */
    private static final UnitRole[][] ROLES = {
          { UnitRole.SCOUT, UnitRole.STRIKER },
          { UnitRole.SKIRMISHER, UnitRole.STRIKER, UnitRole.BRAWLER, UnitRole.SNIPER },
          { UnitRole.BRAWLER, UnitRole.SNIPER, UnitRole.MISSILE_BOAT, UnitRole.SKIRMISHER },
          { UnitRole.JUGGERNAUT, UnitRole.SNIPER, UnitRole.MISSILE_BOAT, UnitRole.BRAWLER } };

    /**
     * What MegaMek knows of the force's designs: every Mek's and vehicle's but {@code unknown}'s, of the role in
     * {@code roles}, or else one its weight suits, and as fast as its weight allows.
     */
    private static Map<UUID, MekSummary> designs(Group root, List<Card> unassigned, Set<UUID> unknown,
          Map<UUID, UnitRole> roles) {
        List<Card> all = new ArrayList<>(root.allUnits());
        all.addAll(unassigned);
        Map<UUID, MekSummary> designs = new HashMap<>();
        int index = 0;
        for (Card card : all) {
            if (unknown.contains(card.id()) || card.kind() != Kind.MEK && card.kind() != Kind.VEHICLE) {
                continue;
            }
            UnitRole[] suited = ROLES[0];
            int walk = 6;
            switch (card.fit().weight()) {
                case ULTRA_LIGHT, LIGHT -> {
                }
                case MEDIUM -> {
                    suited = ROLES[1];
                    walk = 5;
                }
                case HEAVY -> {
                    suited = ROLES[2];
                    walk = 4;
                }
                default -> {
                    suited = ROLES[3];
                    walk = 3;
                }
            }
            MekSummary design = ToeRules.standIn(card);
            design.setUnitRole(roles.getOrDefault(card.id(), suited[index++ % suited.length]));
            design.setWalkMp(walk);
            designs.put(card.id(), design);
        }
        return designs;
    }

    /** Gives {@code units} roles that leave them one Scout or Striker short of the Security rule. */
    private static void shortOfScouts(List<Card> units, Map<UUID, UnitRole> roles) {
        UnitRole[] fire = { UnitRole.SNIPER, UnitRole.MISSILE_BOAT, UnitRole.BRAWLER, UnitRole.JUGGERNAUT };
        for (int i = 0; i < units.size(); i++) {
            roles.put(units.get(i).id(), fire[i % fire.length]);
        }
    }

    /** The slots {@code units} fill exactly, then {@code more}, left vacant. */
    private static List<FormationSlot> matched(List<Card> units, FormationSlot... more) {
        List<FormationSlot> slots = new ArrayList<>(units.stream().map(Card::fit).toList());
        slots.addAll(List.of(more));
        return slots;
    }

    /** The slots {@code units} fill, but the {@code index}th calling for another weight, so that unit fits none. */
    private static List<FormationSlot> misplaced(List<Card> units, int index) {
        List<FormationSlot> slots = matched(units);
        FormationSlot.Weight weight = slots.get(index).weight() == FormationSlot.Weight.ASSAULT
              ? FormationSlot.Weight.LIGHT : FormationSlot.Weight.ASSAULT;
        slots.set(index, new FormationSlot(slots.get(index).kind(), weight));
        return slots;
    }

    /** {@code count} slots for any {@code kind} of unit. */
    private static List<FormationSlot> open(FormationSlot.Kind kind, int count) {
        return Collections.nCopies(count, new FormationSlot(kind, FormationSlot.Weight.ANY));
    }

    /** {@code group} and everything under it, the formation {@code shaping} is for shaped by it. */
    private static Group authorized(Group group, ToeTable.Shaping shaping) {
        List<Group> children = group.children().stream().map(child -> authorized(child, shaping)).toList();
        Group under = group.with(children, group.units());
        return group.id() == shaping.formation() ? under.shaped(shaping.slots(), shaping.template(), shaping.rule())
              : under;
    }

    private static List<Card> meks(Random random, int count, int camo, int place, boolean deployed,
          boolean transit) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int kind = random.nextInt(MEKS.length);
            cards.add(unit(random, MEKS[kind], "meks/" + MEKS[kind], Kind.MEK, MEK_TONS[kind], camo, place,
                  deployed, transit));
        }
        return cards;
    }

    private static List<Card> vehicles(Random random, int count, int camo, int place) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int kind = random.nextInt(VEHICLES.length);
            cards.add(unit(random, VEHICLES[kind], "vehicles/" + VEHICLES[kind], Kind.VEHICLE, VEHICLE_TONS[kind],
                  camo, place, false, false));
        }
        return cards;
    }

    private static List<Card> defaults(Random random, String art, Kind kind, double tons, int count, int place) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            cards.add(unit(random, art.replace('_', ' '), "defaults/" + art, kind, tons, 3, place, false, place != 0));
        }
        return cards;
    }

    /** A unit in the field: mostly sound and crewed, now and then damaged, under the wrench or short of crew. */
    private static Card unit(Random random, String name, String art, Kind kind, double tons, int camo, int place,
          boolean deployed, boolean transit) {
        double roll = random.nextDouble();
        Condition condition = roll < 0.7 ? Condition.SOUND : roll < 0.82 ? Condition.LIGHT
              : roll < 0.88 ? Condition.MODERATE : roll < 0.92 ? Condition.HEAVY : roll < 0.95 ? Condition.CRIPPLED
              : roll < 0.98 ? Condition.INOPERABLE : Condition.SALVAGE;
        Work work = Work.NONE;
        if (!deployed && condition != Condition.SOUND && random.nextDouble() < 0.6) {
            work = Work.REPAIR;
        } else if (!deployed && random.nextDouble() < 0.03) {
            work = Work.REFIT;
        }
        double crewRoll = random.nextDouble();
        Crew crew = deployed ? Crew.FULL : crewRoll < 0.05 ? Crew.NONE : crewRoll < 0.1 ? Crew.PARTIAL : Crew.FULL;
        String pilot = PILOTS[random.nextInt(PILOTS.length)];
        boolean fatigued = crew != Crew.NONE && random.nextDouble() < 0.1;
        return card(name.replace('_', ' '), art, kind, tons, camo, pilot, deployed ? Condition.SOUND : condition, work,
              crew, fatigued, deployed, transit, place);
    }

    private static Card card(String name, String art, Kind kind, double tons, int camo, String pilot,
          Condition condition, Work work, Crew crew, boolean deployed, boolean transit, int place) {
        return card(name, art, kind, tons, camo, pilot, condition, work, crew, false, deployed, transit, place);
    }

    private static Card card(String name, String art, Kind kind, double tons, int camo, String pilot,
          Condition condition, Work work, Crew crew, boolean fatigued, boolean deployed, boolean transit, int place) {
        String key = art + '|' + camo;
        return new Card(UUID.randomUUID(), name.replace('_', ' '), crew == Crew.NONE ? "" : pilot, kind, tons, key,
              art(art, camo), condition, work, crew, fatigued, true, deployed, transit, place, fit(kind, tons));
    }

    /** The slot a unit of {@code kind} weighing {@code tons} fills exactly, by the ground units' weight classes. */
    private static FormationSlot fit(Kind kind, double tons) {
        FormationSlot.Kind slotKind = kind == Kind.OTHER ? FormationSlot.Kind.ANY : FormationSlot.Kind.valueOf(kind.name());
        if (kind != Kind.MEK && kind != Kind.VEHICLE) {
            return new FormationSlot(slotKind, FormationSlot.Weight.ANY);
        }
        FormationSlot.Weight weight = tons < 20 ? FormationSlot.Weight.ULTRA_LIGHT : tons < 40 ? FormationSlot.Weight.LIGHT
              : tons < 60 ? FormationSlot.Weight.MEDIUM : tons < 80 ? FormationSlot.Weight.HEAVY
              : tons <= 100 ? FormationSlot.Weight.ASSAULT : FormationSlot.Weight.SUPER_HEAVY;
        return new FormationSlot(slotKind, weight);
    }

    private static final Map<String, BufferedImage> ART = new HashMap<>();

    /** The unit art at {@code path}, its greys painted in camouflage {@code camo}. */
    private static @Nullable BufferedImage art(String path, int camo) {
        return ART.computeIfAbsent(path + '|' + camo, key -> {
            try {
                BufferedImage source = ImageIO.read(new File(UNITS + path + ".png"));
                if (source == null) {
                    return null;
                }
                BufferedImage painted = ToeSnapshot.copy(source);
                int[] tint = CAMO[camo % CAMO.length];
                for (int y = 0; y < painted.getHeight(); y++) {
                    for (int x = 0; x < painted.getWidth(); x++) {
                        int argb = painted.getRGB(x, y);
                        int r = ((argb >> 16) & 0xFF) * tint[0] / 135;
                        int g = ((argb >> 8) & 0xFF) * tint[1] / 135;
                        int b = (argb & 0xFF) * tint[2] / 135;
                        painted.setRGB(x, y, (argb & 0xFF000000) | (Math.min(r, 255) << 16) | (Math.min(g, 255) << 8)
                              | Math.min(b, 255));
                    }
                }
                return painted;
            } catch (IOException e) {
                return null;
            }
        });
    }

    /** A formation's icon layered as the TO&amp;E's icon editor would: its type over a background, in a frame. */
    private static @Nullable BufferedImage icon(String type, String size) {
        return ICONS.computeIfAbsent(type + '|' + size, key -> {
            String pieces = "data/images/force/Pieces/";
            BufferedImage layered = new BufferedImage(289, 151, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = layered.createGraphics();
            try {
                for (String layer : new String[] { "Backgrounds/Mercenary.png", "Types/StratOps/" + type + ".png",
                                                   "Formations/Inner Sphere/" + size + ".png", "Frames/Frame.png" }) {
                    BufferedImage piece = ImageIO.read(new File(pieces + layer));
                    if (piece != null) {
                        graphics.drawImage(piece, 0, 0, layered.getWidth(), layered.getHeight(), null);
                    }
                }
            } catch (IOException e) {
                return null;
            } finally {
                graphics.dispose();
            }
            return WarTableSnapshot.icon(layered);
        });
    }

    private static final class Ids {
        private int next = 1;

        int next() {
            return next++;
        }
    }
}
