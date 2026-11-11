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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import megamek.client.ui.gdx.UiKit;
import megamek.client.ui.gdx.UiTheme;
import megamek.common.Configuration;
import megamek.common.MMRandom;
import megamek.common.compute.Compute;
import megamek.common.loaders.MapSettings;
import mekhq.campaign.campaignOptions.CampaignOptions;
import mekhq.campaign.digitalGM.stratCon.StratConContractInitializer;
import mekhq.campaign.digitalGM.stratCon.StratConCoords;
import mekhq.campaign.digitalGM.stratCon.StratConTrackState;
import mekhq.campaign.digitalGM.stratCon.biome.StratConBiomeManifest;
import mekhq.campaign.digitalGM.stratCon.biome.StratConTerrainCategory;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacility;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacility.FacilityIntel;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacility.FacilityTier;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacilityFactory;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacilitySupply;
import mekhq.campaign.digitalGM.stratCon.sectorGeneration.LatitudeBand;
import mekhq.campaign.digitalGM.stratCon.sectorGeneration.PlanetProfile;
import mekhq.campaign.digitalGM.stratCon.sectorGeneration.SectorSpec;
import mekhq.gui.gdx.hub.WarTableSnapshot.Beacon;
import mekhq.gui.gdx.hub.WarTableSnapshot.Hex;
import mekhq.gui.gdx.hub.WarTableSnapshot.Kind;
import mekhq.gui.gdx.hub.WarTableSnapshot.Mark;
import mekhq.gui.gdx.hub.WarTableSnapshot.Reserve;
import mekhq.gui.gdx.hub.WarTableSnapshot.Sector;
import mekhq.gui.gdx.hub.WarTableSnapshot.Site;
import mekhq.gui.gdx.hub.WarTableSnapshot.Spot;
import mekhq.gui.gdx.hub.WarTableSnapshot.Token;
import mekhq.utilities.ScenarioUtils;

/**
 * A developer's look at the war table without a campaign: generates an operation's sectors with StratCon's own
 * initializer from a fixed seed, sets facilities, supply and scouted ground on them, adds scenarios and forces, and
 * opens the table on them in a window. Forces dragged on and off the table are deployed and recalled on the demo's own
 * snapshot, and N passes a day on the sector shown: ground is scouted, scenarios count down and turn up, and a facility
 * changes hands. Run through the warTableDemo task; {@code -Pcapture} instead writes a few shots to build/war-table, a
 * deployment and a day passing among them, and exits.
 */
public final class WarTableDemo {
    private static final long SEED = 3025_06_01L;
    private static final LocalDate TODAY = LocalDate.of(3025, 6, 1);
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    private static final String[] SECTORS = { "Sector Kestrel", "Sector Anvil", "Sector Hollow" };
    private static final String[] FORCES = { "Alpha Lance", "Bravo Lance", "Recon Star", "Fire Lance" };
    /**
     * The forces held back off the table, each sector's own, ids from {@link #RESERVE_IDS} up: lances of a few
     * companies, flights of the air wing and some foot, enough to fill the drawer.
     */
    private static final String[] LANCES = { "Cobra Lance", "Hammer Lance", "Striker Lance", "Ghost Lance",
          "Anvil Lance", "Spear Lance", "Talon Lance", "Raven Lance", "Wolf Lance", "Iron Lance", "Bastion Lance",
          "Ember Lance" };
    private static final String[] COMPANIES = { "Able Company", "Baker Company", "Charlie Company" };
    private static final String[] FLIGHTS = { "Kestrel Flight", "Harrier Flight", "Valkyrie Flight",
          "Osprey Flight", "Condor Flight" };
    private static final String[] FOOT = { "Jump Platoon", "Rifle Platoon", "Elemental Point" };
    private static final int RESERVE_IDS = 10;
    /** The forces' icons' unit types, layered as the TO&E's icon editor would: four lances, a flight, a platoon. */
    private static final String[] FORCE_TYPES = { "BattleMek (Line)", "BattleMek (Center)", "BattleMek (Recon)",
          "BattleMek (Fire Support)", "Aerospace", "Battle Armor" };
    private static final String[] FORCE_BACKGROUNDS = { "Inner Sphere/Federated Suns.png", "Mercenary.png",
          "Inner Sphere/Federated Suns.png", "Mercenary.png", "Mercenary.png", "Inner Sphere/Federated Suns.png" };
    private static final int AIR_ICON = 4;
    private static final int FOOT_ICON = 5;
    private static final BufferedImage[] ICONS = new BufferedImage[FORCE_TYPES.length];
    private static final String[][] SCENARIOS = {
          { "Hold the Ridge", "Enemy armour is probing the ridge line. Hold until relieved; falling back gives them "
                + "the heights over the supply road." },
          { "Breakthrough", "A gap has opened in the enemy's screen. Punch through and reach the far edge before "
                + "their reserves close it." },
          { "Base Attack", "The depot feeds every enemy lance in the sector. Destroy it, and their line starves." },
          { "Recon in Force", "Find out what's moving behind the hills. Bring back sensor data; don't get pinned." },
          { "Convoy Escort", "Supply trucks run the valley road at dawn. See them through." } };

    private WarTableDemo() {
    }

    public static void main(String[] args) {
        boolean capture = List.of(args).contains("capture");
        Random random = new Random(SEED);
        Compute.setRNG(new MMRandom() {
            @Override
            public int randomInt(int maxValue) {
                return random.nextInt(maxValue);
            }

            @Override
            public float randomFloat() {
                return random.nextFloat();
            }
        });
        WarTableSnapshot operation = operation(random);
        DemoOrders orders = new DemoOrders(operation);

        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("War table demo");
        configuration.setWindowedMode(WIDTH, HEIGHT);
        configuration.setInitialVisible(!capture);
        configuration.useVsync(true);
        configuration.setForegroundFPS(capture ? 60 : 0);
        configuration.setBackBufferConfig(8, 8, 8, 8, 24, 8, 4);
        configuration.disableAudio(true);
        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 4, 3);
        new Lwjgl3Application(new ApplicationAdapter() {
            private WarTable table;
            private int frames;

            @Override
            public void create() {
                UiTheme theme = new UiTheme(Configuration.fontsDir());
                UiKit ui = new UiKit(theme.skin);
                table = new WarTable(() -> Gdx.app.exit(), orders);
                orders.table = table;
                table.create(theme, ui, 1);
                table.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                table.open(List.of(operation));
                Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
                    @Override
                    public boolean keyDown(int keycode) {
                        if (keycode != Input.Keys.N) {
                            return false;
                        }
                        orders.nextDay(table.sectorShown());
                        return true;
                    }
                }, table.input()));
            }

            @Override
            public void resize(int width, int height) {
                table.resize(width, height);
            }

            @Override
            public void render() {
                table.render(Math.min(Gdx.graphics.getDeltaTime(), 0.1f));
                // The script counts only frames with the sector's ground drawn, however long the board takes to load
                if (table.groundReady()) {
                    frames++;
                } else {
                    return;
                }
                if (capture) {
                    switch (frames) {
                        case 30 -> shot("rising");
                        case 240 -> shot("table");
                        case 260 -> click(0.5f, 0.55f);
                        case 300 -> shot("picked");
                        case 320 -> table.showSector(1);
                        case 520 -> shot("anvil");
                        case 530 -> table.inspect(operation.sectors().get(1).beacons().get(0));
                        case 560 -> shot("diving");
                        case 760 -> shot("board");
                        case 780 -> {
                            table.input().keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
                        }
                        case 830 -> shot("risen");
                        // Opens the reserve's drawer and drags its first force onto the middle of the table
                        case 832 -> table.openDrawer(true);
                        case 860 -> shot("drawer");
                        case 870 -> {
                            List<Reserve> held = table.reserveShown();
                            sent = held.isEmpty() ? -1 : held.get(0).force();
                            table.input().touchDown(tileX(), tileY(), 0, 0);
                        }
                        case 880 -> table.input().touchDragged(tileX() + 120, tileY(), 0);
                        case 890 -> table.input().touchDragged(Gdx.graphics.getWidth() / 2,
                              Gdx.graphics.getHeight() / 2, 0);
                        case 905 -> shot("carried");
                        case 910 -> table.input().touchUp(Gdx.graphics.getWidth() / 2,
                              Gdx.graphics.getHeight() / 2, 0, 0);
                        case 930 -> shot("asking");
                        case 935 -> table.input().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                        case 945 -> shot("landing");
                        case 990 -> shot("landed");
                        // Carries it back off the table onto the drawer to recall it
                        case 1010 -> {
                            Vector2 standee = table.standeeOnScreen(sent);
                            Vector2 at = standee == null ? new Vector2(780, 447) : standee;
                            table.input().touchDown(Math.round(at.x), Math.round(at.y), 0, 0);
                        }
                        case 1020 -> table.input().touchDragged(600, 500, 0);
                        case 1030 -> table.input().touchDragged(170, 500, 0);
                        case 1040 -> shot("recalling");
                        case 1045 -> table.input().touchUp(170, 500, 0, 0);
                        case 1060 -> shot("recallAsk");
                        case 1065 -> table.input().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                        case 1085 -> shot("lifting");
                        case 1150 -> shot("recalled");
                        // A day passes: fog burns off round the forces and a new scenario strikes
                        case 1160 -> orders.nextDay(table.sectorShown());
                        case 1180 -> shot("revealing");
                        case 1206 -> shot("striking");
                        case 1230 -> shot("struck");
                        case 1320 -> shot("dayPassed");
                        // Plots the map: flat, in a printed map's colours
                        case 1330 -> table.input().keyDown(com.badlogic.gdx.Input.Keys.P);
                        case 1390 -> shot("plotted");
                        // Zooms in close, to see the printed map's symbols and then the ground's detail
                        case 1395 -> {
                            table.input().mouseMoved(Gdx.graphics.getWidth() / 2, Gdx.graphics.getHeight() / 2);
                            table.input().scrolled(0, -6);
                        }
                        case 1460 -> shot("plotClose");
                        case 1465 -> table.input().keyDown(com.badlogic.gdx.Input.Keys.P);
                        case 1540 -> shot("close");
                        // Previews the fog of war, then lifts it again; then shows every label
                        case 1545 -> table.input().keyDown(com.badlogic.gdx.Input.Keys.F);
                        case 1640 -> shot("fogged");
                        case 1645 -> table.input().keyDown(com.badlogic.gdx.Input.Keys.F);
                        case 1700 -> shot("unfogging");
                        case 1800 -> {
                            table.input().keyDown(com.badlogic.gdx.Input.Keys.L);
                            table.input().keyDown(com.badlogic.gdx.Input.Keys.L);
                        }
                        case 1810 -> shot("allLabels");
                        case 1815 -> table.input().keyDown(com.badlogic.gdx.Input.Keys.L);
                        case 1825 -> {
                            shot("keyLabels");
                            Gdx.app.exit();
                        }
                        default -> {
                        }
                    }
                }
            }

            /** The force dragged onto the table, to carry back off it. */
            private int sent = -1;

            private int tileX() {
                return Math.round(firstTile().x);
            }

            private int tileY() {
                return Math.round(firstTile().y);
            }

            /** Where the open drawer's first force's tile is, on screen. */
            private Vector2 firstTile() {
                List<Reserve> held = table.reserveShown();
                Vector2 at = held.isEmpty() ? null : table.tileOnScreen(held.get(0).force());
                return at == null ? new Vector2(150, 300) : at;
            }

            private void click(float x, float y) {
                int px = Math.round(Gdx.graphics.getWidth() * x);
                int py = Math.round(Gdx.graphics.getHeight() * y);
                table.input().mouseMoved(px, py);
                table.input().touchDown(px, py, 0, 0);
                table.input().touchUp(px, py, 0, 0);
            }

            @Override
            public void dispose() {
                table.dispose();
            }
        }, configuration);
    }

    private static void shot(String name) {
        File out = new File("build/war-table");
        out.mkdirs();
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
        PixmapIO.writePNG(Gdx.files.absolute(new File(out, name + ".png").getAbsolutePath()), pixmap, -1, true);
        pixmap.dispose();
    }

    /** The operation: three sectors of different sizes and climates. */
    static WarTableSnapshot operation(Random random) {
        List<Sector> sectors = new ArrayList<>();
        int[] teams = { 4, 6, 3 };
        LatitudeBand[] bands = { LatitudeBand.NORTH_TEMPERATE, LatitudeBand.EQUATORIAL, LatitudeBand.NORTH_POLAR };
        int[] temperatures = { 38, 31, 32 };
        for (int i = 0; i < SECTORS.length; i++) {
            StratConTrackState track = StratConContractInitializer.initializeTrackState(
                  new SectorSpec(teams[i], bands[i]), PlanetProfile.neutral(temperatures[i]), new CampaignOptions(),
                  true, 0, 0);
            track.setDisplayableName(SECTORS[i]);
            sectors.add(sector(track, random, i));
        }
        return new WarTableSnapshot(UUID.nameUUIDFromBytes("Hammerfall".getBytes(StandardCharsets.UTF_8)),
              "Operation Hammerfall", sectors, 7, 12);
    }

    private static Sector sector(StratConTrackState track, Random random, int index) {
        StratConBiomeManifest biomes = StratConBiomeManifest.getInstance();
        List<StratConCoords> land = new ArrayList<>();
        for (int x = 0; x < track.getWidth(); x++) {
            for (int y = 0; y < track.getHeight(); y++) {
                StratConCoords coords = new StratConCoords(x, y);
                String terrain = track.getTerrainTile(coords);
                if (!terrain.isBlank() && !track.isOffTrack(coords)
                      && biomes.getTerrainCategory(terrain) != StratConTerrainCategory.OCEAN) {
                    land.add(coords);
                }
            }
        }
        land.sort((a, b) -> a.getX() != b.getX() ? Integer.compare(a.getX(), b.getX())
              : Integer.compare(a.getY(), b.getY()));
        if (land.isEmpty()) {
            return WarTableSnapshot.of(track, TODAY, id -> FORCES[id % FORCES.length], WarTableDemo::icon);
        }
        // The player holds the west, the enemy the east: facilities, scouting and forces follow that front
        List<StratConCoords> west = land.subList(0, Math.max(1, land.size() / 3));
        List<StratConCoords> east = land.subList(land.size() * 2 / 3, land.size());
        Set<StratConCoords> taken = new HashSet<>();
        StratConCoords garrison = null;
        for (int i = 0; i < 2; i++) {
            StratConCoords at = pick(west, random, taken);
            garrison = garrison == null ? at : garrison;
            place(track, StratConFacilityFactory.getRandomAlliedFacility(), at, false, random);
        }
        for (int i = 0; i < 3; i++) {
            place(track, StratConFacilityFactory.getRandomHostileFacility(), pick(east, random, taken), i == 0,
                  random);
        }
        StratConFacilitySupply.rebuildNetworks(track);

        Set<StratConCoords> revealed = new HashSet<>();
        int scoutedTo = land.get(Math.min(land.size() - 1, land.size() * 3 / 5)).getX();
        for (StratConCoords coords : land) {
            if (coords.getX() <= scoutedTo || random.nextFloat() < 0.15f) {
                revealed.add(coords);
            }
        }
        for (StratConCoords coords : track.getFacilities().keySet()) {
            revealed.add(coords);
        }
        track.setRevealedCoords(revealed);

        Map<StratConCoords, Set<Integer>> forces = new HashMap<>();
        int forceCount = 2 + index % 2;
        for (int i = 0; i < forceCount; i++) {
            StratConCoords at = pick(land.subList(0, Math.max(1, land.size() / 2)), random, taken);
            forces.computeIfAbsent(at, coords -> new HashSet<>()).add(i);
        }
        // A crowded hex, its forces stacked, and a force holding one of the player's facilities
        StratConCoords crowded = pick(land.subList(0, Math.max(1, land.size() / 2)), random, taken);
        for (int i = 0; i < 4; i++) {
            forces.computeIfAbsent(crowded, coords -> new HashSet<>()).add(forceCount + i);
        }
        forces.computeIfAbsent(garrison, coords -> new HashSet<>()).add(forceCount + 4);
        track.setAssignedCoordForces(forces);

        Sector sector = WarTableSnapshot.of(track, TODAY, id -> FORCES[id % FORCES.length],
              id -> icon(id % FORCES.length));
        List<Beacon> beacons = new ArrayList<>(sector.beacons());
        int scenarios = 2 + (index == 1 ? 1 : 0);
        List<StratConCoords> middle = land.subList(land.size() / 4, Math.max(land.size() / 4 + 1,
              land.size() * 4 / 5));
        for (int i = 0; i < scenarios; i++) {
            StratConCoords at = pick(middle, random, taken);
            String[] scenario = SCENARIOS[(index * 2 + i) % SCENARIOS.length];
            String terrain = track.getTerrainTile(at);
            beacons.add(new Beacon(at.getX(), at.getY(), scenario[0], i == 0 && index == 0 ? 0 : 1 + random.nextInt(6),
                  i == 0, scenario[1], board(biomes, terrain, random)));
        }
        List<Spot> cuts = new ArrayList<>(sector.cuts());
        List<Mark> marks = new ArrayList<>(sector.marks());
        for (Hex hex : sector.hexes()) {
            if (hex.road() && cuts.isEmpty() && hex.x() > scoutedTo / 2) {
                cuts.add(new Spot(hex.x(), hex.y()));
            }
        }
        StratConCoords cache = pick(land, random, taken);
        marks.add(new Mark(cache.getX(), cache.getY(), "Downed DropShip",
              "A Leopard came down hard here last winter. Its cargo bay may still hold parts.", false));
        List<Token> tokens = new ArrayList<>();
        for (Token token : sector.tokens()) {
            // The first force is fighting, to show one that can't leave
            tokens.add(token.force() != 0 ? token : new Token(token.force(), token.x(), token.y(), token.name(),
                  token.icon(), "Engaged in a scenario: it can't be recalled until the fighting's over."));
        }
        // A scenario's hex is shut to new forces, as it would be while the scenario's in the open
        List<Hex> hexes = new ArrayList<>();
        for (Hex hex : sector.hexes()) {
            boolean beacon = beacons.stream().anyMatch(b -> b.x() == hex.x() && b.y() == hex.y());
            hexes.add(beacon ? withOpen(hex, false) : hex);
        }
        return new Sector(sector.name(), sector.width(), sector.height(), sector.temperature(), hexes,
              sector.sites(), beacons, tokens, sector.supply(), cuts, marks, reserve(index));
    }

    /** Sector {@code index}'s reserve: more forces the further in, as a campaign's would pile up. */
    private static List<Reserve> reserve(int index) {
        List<Reserve> reserve = new ArrayList<>();
        int force = RESERVE_IDS + index * 100;
        for (int i = 0; i < 6 + 3 * index; i++) {
            reserve.add(new Reserve(force++, LANCES[i % LANCES.length], icon(i % FORCES.length), Kind.GROUND,
                  COMPANIES[i / 4 % COMPANIES.length]));
        }
        for (int i = 0; i < 1 + 2 * index; i++) {
            reserve.add(new Reserve(force++, FLIGHTS[i % FLIGHTS.length], icon(AIR_ICON), Kind.AEROSPACE,
                  "Air Wing"));
        }
        for (int i = 0; i < 1 + index; i++) {
            reserve.add(new Reserve(force++, FOOT[i % FOOT.length], icon(FOOT_ICON), Kind.INFANTRY,
                  i == 2 ? "" : "Infantry Detachment"));
        }
        return reserve;
    }

    private static Hex withOpen(Hex hex, boolean open) {
        return new Hex(hex.x(), hex.y(), hex.terrain(), hex.relief(), hex.image(), hex.scouted(), hex.road(),
              hex.city(), open);
    }

    private static Sector with(Sector sector, List<Hex> hexes, List<Token> tokens, List<Reserve> reserve) {
        return new Sector(sector.name(), sector.width(), sector.height(), sector.temperature(), hexes, sector.sites(),
              sector.beacons(), tokens, sector.supply(), sector.cuts(), sector.marks(), reserve);
    }

    /** The table's orders, carried out on the demo's operation a frame later, as the campaign would. */
    private static final class DemoOrders implements WarTable.Orders {
        private WarTableSnapshot operation;
        private WarTable table;
        private int days;
        /** The reserve's forces sent onto the table, to go back to the reserve as they were if recalled. */
        private final Map<Integer, Reserve> sent = new HashMap<>();

        DemoOrders(WarTableSnapshot operation) {
            this.operation = operation;
        }

        @Override
        public void deploy(UUID contract, int sector, int x, int y, int force) {
            Sector at = operation.sectors().get(sector);
            Reserve reserve = at.reserve().stream().filter(r -> r.force() == force).findFirst().orElse(null);
            if (reserve == null) {
                return;
            }
            List<Token> tokens = new ArrayList<>(at.tokens());
            tokens.add(new Token(force, x, y, reserve.name(), reserve.icon(), ""));
            List<Reserve> left = new ArrayList<>(at.reserve());
            left.remove(reserve);
            sent.put(force, reserve);
            update(sector, with(at, opened(at.hexes(), x, y, false), tokens, left), operation.supportPoints());
        }

        @Override
        public void recall(UUID contract, int sector, int force) {
            Sector at = operation.sectors().get(sector);
            Token token = at.tokens().stream().filter(t -> t.force() == force).findFirst().orElse(null);
            if (token == null) {
                return;
            }
            List<Token> tokens = new ArrayList<>(at.tokens());
            tokens.remove(token);
            List<Reserve> reserve = new ArrayList<>(at.reserve());
            reserve.add(sent.getOrDefault(force, new Reserve(force, token.name(), token.icon())));
            boolean held = tokens.stream().anyMatch(t -> t.x() == token.x() && t.y() == token.y());
            List<Hex> hexes = held ? at.hexes() : opened(at.hexes(), token.x(), token.y(), true);
            update(sector, with(at, hexes, tokens, reserve),
                  operation.supportPoints() - WarTableSnapshot.RECALL_COST);
        }

        /**
         * A day passes on sector {@code index}: the ground nearest the forces is scouted, the scenarios count down, those
         * due fought out and gone, a new one turns up and a facility changes hands.
         */
        void nextDay(int index) {
            days++;
            Random random = new Random(SEED + days);
            Sector at = operation.sectors().get(index);
            List<Hex> unseen = new ArrayList<>(at.hexes().stream().filter(hex -> !hex.scouted()).toList());
            unseen.sort(Comparator.comparingDouble(hex -> at.tokens().stream()
                  .mapToDouble(token -> Math.hypot(token.x() - hex.x(), token.y() - hex.y())).min().orElse(hex.x())));
            Set<Hex> scouting = new HashSet<>(unseen.subList(0, Math.min(7, unseen.size())));
            List<Beacon> beacons = new ArrayList<>();
            for (Beacon beacon : at.beacons()) {
                if (beacon.daysLeft() != 0) {
                    beacons.add(beacon.daysLeft() < 0 ? beacon : new Beacon(beacon.x(), beacon.y(), beacon.name(),
                          beacon.daysLeft() - 1, beacon.objective(), beacon.briefing(), beacon.map()));
                }
            }
            List<Hex> hexes = new ArrayList<>();
            for (Hex hex : at.hexes()) {
                hexes.add(!scouting.contains(hex) ? hex : new Hex(hex.x(), hex.y(), hex.terrain(), hex.relief(),
                      hex.image(), true, hex.road(), hex.city(), hex.open()));
            }
            List<Hex> free = hexes.stream().filter(hex -> hex.scouted() && hex.open()
                  && !WarTableAssets.water(hex)
                  && beacons.stream().noneMatch(b -> b.x() == hex.x() && b.y() == hex.y())
                  && at.sites().stream().noneMatch(s -> s.x() == hex.x() && s.y() == hex.y())).toList();
            if (!free.isEmpty()) {
                Hex spot = free.get(random.nextInt(free.size()));
                String[] scenario = SCENARIOS[days % SCENARIOS.length];
                beacons.add(new Beacon(spot.x(), spot.y(), scenario[0], 2 + random.nextInt(4), false, scenario[1],
                      at.beacons().isEmpty() ? null : at.beacons().get(0).map()));
                hexes = opened(hexes, spot.x(), spot.y(), false);
            }
            List<Site> sites = new ArrayList<>(at.sites());
            if (!sites.isEmpty()) {
                int flip = days % sites.size();
                Site site = sites.get(flip);
                sites.set(flip, new Site(site.x(), site.y(), site.name(), site.type(), site.tier(), !site.allied(),
                      site.objective(), site.cutOff(), site.detail()));
            }
            update(index, new Sector(at.name(), at.width(), at.height(), at.temperature(), hexes, sites, beacons,
                  at.tokens(), at.supply(), at.cuts(), at.marks(), at.reserve()), operation.supportPoints());
        }

        private static List<Hex> opened(List<Hex> hexes, int x, int y, boolean open) {
            List<Hex> changed = new ArrayList<>();
            for (Hex hex : hexes) {
                changed.add(hex.x() == x && hex.y() == y ? withOpen(hex, open) : hex);
            }
            return changed;
        }

        private void update(int index, Sector sector, int supportPoints) {
            List<Sector> sectors = new ArrayList<>(operation.sectors());
            sectors.set(index, sector);
            operation = new WarTableSnapshot(operation.contract(), operation.operation(), sectors,
                  operation.victoryPoints(), supportPoints);
            WarTableSnapshot shown = operation;
            Gdx.app.postRunnable(() -> table.refresh(List.of(shown)));
        }
    }

    private static void place(StratConTrackState track, StratConFacility facility, StratConCoords at,
          boolean objective, Random random) {
        if (facility == null) {
            return;
        }
        facility.setTier(FacilityTier.values()[random.nextInt(FacilityTier.values().length)]);
        facility.setVisible(true);
        facility.setIntel(objective ? FacilityIntel.DETAILED : FacilityIntel.SCOUTED);
        facility.setStrategicObjective(objective);
        track.addFacility(at, facility);
    }

    /**
     * Force {@code id}'s icon, layered from the TO&E icon's pieces (background, unit type, formation, frame) as a
     * campaign's {@code LayeredFormationIcon} would; null if the pieces aren't there.
     */
    private static BufferedImage icon(int index) {
        if (ICONS[index] == null) {
            String pieces = "data/images/force/Pieces/";
            BufferedImage layered = new BufferedImage(289, 151, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = layered.createGraphics();
            try {
                for (String layer : new String[] { "Backgrounds/" + FORCE_BACKGROUNDS[index],
                      "Types/StratOps/" + FORCE_TYPES[index] + ".png", "Formations/Inner Sphere/(04) Lance.png",
                      "Frames/Frame.png" }) {
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
            ICONS[index] = WarTableSnapshot.icon(layered);
        }
        return ICONS[index];
    }

    private static StratConCoords pick(List<StratConCoords> from, Random random, Set<StratConCoords> taken) {
        for (int tries = 0; tries < 40; tries++) {
            StratConCoords coords = from.get(random.nextInt(from.size()));
            if (taken.add(coords)) {
                return coords;
            }
        }
        return from.get(random.nextInt(from.size()));
    }

    /** A board for a scenario on {@code terrain}, from the pool StratCon would draw it from. */
    private static MapSettings board(StratConBiomeManifest biomes, String terrain, Random random) {
        StratConBiomeManifest.MapTypeList pool = biomes.getMapTypesForTerrain(terrain);
        String map = pool == null || pool.mapTypes.isEmpty() ? "Plains"
              : pool.mapTypes.get(random.nextInt(pool.mapTypes.size()));
        return ScenarioUtils.getMapSettings(32, 34, map, false, false, false);
    }
}
