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

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.GLTexture;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import jakarta.annotation.Nullable;
import megamek.logging.MMLogger;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacility.FacilityType;
import mekhq.gui.gdx.hub.WarTableSnapshot.Hex;
import mekhq.gui.gdx.hub.WarTableSnapshot.Relief;

/**
 * What the war table's map is drawn with, from the board's own art: each hex's ground (the surface its top is painted
 * with and the small things scattered over it), the buildings and props the facilities are modelled with, and the
 * forces' icons as textures. The mapping is static; the art loads on the GPU thread the first time it's asked for and
 * stays until the table is disposed.
 */
final class WarTableAssets implements Disposable {
    private static final MMLogger LOGGER = MMLogger.create(WarTableAssets.class);
    private static final Path MODELS = Path.of("data", "models");

    /** A facility's model and the props set round it, the more of them the larger its tier. */
    record Kit(String main, List<String> extras) {
    }

    private static final String FRONTIER = "board/scenery/frontier/";
    private static final String MILITARY = "board/scenery/military/";
    private static final String COLONY = "board/scenery/colony/";
    private static final String AUTHORED = "buildings/authored/";
    private static final String TANKS = "buildings/fuel_tanks/";

    /**
     * How a hex's ground shows: the surface its top is painted with (under {@link #SURFACES}), whether it's open
     * water, how thickly the small things that grow or lie on it are scattered and which, and its plain colour on the
     * plotted map ({@code PLOT_*}).
     */
    record Ground(String surface, boolean water, float density, List<String> scatter, int plot) {
    }

    /** The plotted map's kinds of ground, each its own flat colour (see WarTableShaders). */
    static final int PLOT_OPEN = 0;
    static final int PLOT_WOODS = 1;
    static final int PLOT_HILLS = 2;
    static final int PLOT_PEAKS = 3;
    static final int PLOT_ARID = 4;
    static final int PLOT_SNOW = 5;
    static final int PLOT_BUILT = 6;
    static final int PLOT_WATER = 7;
    static final int PLOT_BARREN = 8;

    private static final String SURFACES = "board/textures/terrain/";
    /** How large every surface's layer is, in texels; those drawn larger or smaller are resized. */
    private static final int SURFACE_SIZE = 512;

    private static final String V = "board/vegetation/";
    private static final String R = "board/rocks/";
    private static final String S = "board/scatter/";
    private static final List<String> TEMPERATE = glb(V + "foliage-temperate", V + "foliage-temperate-2",
          V + "foliage-temperate-3", S + "bush-0", S + "bush-1", V + "pine");
    private static final List<String> WOODS = glb(V + "pine", V + "pine-broad", V + "pine-layered", V + "tree",
          V + "tree-broad", V + "birch", V + "foliage-temperate");
    private static final List<String> SNOW_WOODS = glb(V + "pine-tall-snow", V + "tree-forked-snow",
          V + "tree-dead-snow", V + "foliage-snow");
    private static final List<String> JUNGLE = glb(V + "palm", V + "palm-arching", V + "palm-bent",
          V + "palm-compact", V + "foliage-jungle", V + "foliage-jungle-2", V + "foliage-jungle-3");
    private static final List<String> WETLAND = glb(V + "willow", V + "willow-arching", V + "foliage-wetland",
          V + "foliage-wetland-2", V + "foliage-wetland-3", V + "tree-dead");
    private static final List<String> SAVANNAH = glb(V + "orchard-spreading", V + "foliage-desert",
          V + "foliage-desert-2", S + "bush-2");
    private static final List<String> STEPPE = glb(V + "foliage-highland", V + "foliage-highland-2",
          V + "foliage-barren", S + "bush-3");
    private static final List<String> HIGHLAND = glb(V + "foliage-highland", V + "foliage-highland-3",
          R + "boulder-1", R + "boulder-4", V + "pine-slender", V + "pine");
    private static final List<String> PEAKS = glb(R + "outcrop-0", R + "outcrop-2", R + "outcrop-5", R + "block-1",
          R + "block-3", V + "pine-slender");
    private static final List<String> SNOW_PEAKS = glb(R + "outcrop-1", R + "outcrop-3", R + "block-2",
          V + "pine-tall-snow");
    private static final List<String> DRY_PEAKS = glb(R + "outcrop-4", R + "outcrop-6", R + "block-5",
          V + "cactus-old");
    private static final List<String> WET_PEAKS = glb(R + "outcrop-7", R + "block-6", V + "palm-compact",
          V + "foliage-jungle-2");
    private static final List<String> DESERT = glb(V + "cactus", V + "cactus-organ", V + "cactus-young",
          V + "foliage-desert", V + "foliage-desert-3");
    private static final List<String> BADLANDS = glb(R + "block-0", R + "block-4", R + "boulder-2", R + "boulder-6",
          V + "foliage-barren");
    private static final List<String> TUNDRA = glb(V + "foliage-snow", V + "foliage-barren-2", R + "boulder-3",
          V + "foliage-snow-2");
    private static final List<String> ICE = glb(R + "boulder-5", R + "block-7", V + "foliage-snow-3");
    private static final List<String> BARREN = glb(R + "boulder-0", R + "boulder-7", R + "outcrop-3");
    private static final List<String> VOLCANIC = glb(V + "tree-volcano-spire", V + "tree-volcano-crown",
          V + "foliage-volcano", V + "foliage-volcano-2", R + "outcrop-6");
    private static final List<String> FARMLAND = glb(V + "orchard-round", V + "orchard-upright",
          V + "orchard-young", V + "foliage-temperate-2");

    private final Map<String, Texture> shared = new HashMap<>();
    private final Map<String, Optional<GlbProp>> props = new HashMap<>();
    private final Map<String, Optional<Texture>> details = new HashMap<>();
    /** The surfaces last loaded as texture arrays, their albedo and normal arrays; 0 for none. */
    private List<String> surfacesLoaded = List.of();
    private int albedoArray;
    private int normalArray;

    private static List<String> glb(String... models) {
        return Arrays.stream(models).map(model -> model + ".glb").toList();
    }

    /** How {@code hex}'s ground shows, by its StratCon terrain, its relief for any other. */
    static Ground ground(Hex hex) {
        String surfaces = "surfaces/";
        if (water(hex)) {
            return new Ground(surfaces + "sand-ground", true, 0, List.of(), PLOT_WATER);
        }
        Ground ground = switch (hex.terrain()) {
            case "Plains" -> new Ground(surfaces + "grassland-ground", false, 1.6f, TEMPERATE, PLOT_OPEN);
            case "Farmland" -> new Ground(surfaces + "biome-soil", false, 2.2f, FARMLAND, PLOT_OPEN);
            case "Steppe" -> new Ground(surfaces + "tundra-mat", false, 1.4f, STEPPE, PLOT_OPEN);
            case "Savannah" -> new Ground(surfaces + "desert-floor", false, 1.4f, SAVANNAH, PLOT_ARID);
            case "Forest" -> new Ground(surfaces + "grassland-floor", false, 5, WOODS, PLOT_WOODS);
            case "ColdForest" -> new Ground(surfaces + "snow-ground", false, 5, SNOW_WOODS, PLOT_WOODS);
            case "Jungle", "HotForest" -> new Ground(surfaces + "tropical-ground", false, 5, JUNGLE, PLOT_WOODS);
            case "Swamp" -> new Ground(surfaces + "biome-mud", false, 3, WETLAND, PLOT_WOODS);
            case "Hills" -> new Ground(surfaces + "grassland-ground", false, 2.5f, HIGHLAND, PLOT_HILLS);
            case "ColdHills" -> new Ground(surfaces + "snow-ground", false, 2, SNOW_PEAKS, PLOT_SNOW);
            case "HotHillsDry" -> new Ground(surfaces + "desert-ground", false, 2, DRY_PEAKS, PLOT_ARID);
            case "HotHillsWet" -> new Ground(surfaces + "tropical-floor", false, 3, WET_PEAKS, PLOT_HILLS);
            case "Mountain" -> new Ground(surfaces + "rock-ground", false, 3, PEAKS, PLOT_PEAKS);
            case "ColdMountain" -> new Ground(surfaces + "snow-ground", false, 2.5f, SNOW_PEAKS, PLOT_SNOW);
            case "HotMountainsDry" -> new Ground(surfaces + "desert-cap", false, 2.5f, DRY_PEAKS, PLOT_PEAKS);
            case "HotMountainsWet" -> new Ground(surfaces + "tropical-floor", false, 3, WET_PEAKS, PLOT_PEAKS);
            case "Desert" -> new Ground(surfaces + "desert-ground", false, 1.2f, DESERT, PLOT_ARID);
            case "Badlands" -> new Ground(surfaces + "arid-debris", false, 2, BADLANDS, PLOT_ARID);
            case "Tundra" -> new Ground(surfaces + "tundra-ground", false, 1.5f, TUNDRA, PLOT_SNOW);
            case "SnowField", "ArcticDesert" -> new Ground(surfaces + "snow-ground", false, 0.7f, ICE, PLOT_SNOW);
            case "Glacier" -> new Ground("ice/ice-sheet", false, 0.5f, ICE, PLOT_SNOW);
            case "FrozenSea" -> new Ground("ice/ice-sheet", false, 0, List.of(), PLOT_SNOW);
            case "GrayLunar" -> new Ground(surfaces + "lunar-ground", false, 1.5f, BARREN, PLOT_BARREN);
            case "Mars" -> new Ground(surfaces + "mars-ground", false, 1.5f, BARREN, PLOT_BARREN);
            case "Volcano" -> new Ground(surfaces + "volcano-ground", false, 2, VOLCANIC, PLOT_BARREN);
            case "Urban", "HotUrban", "ColdUrban", "Factory" ->
                  new Ground(surfaces + "concrete-ground", false, 0, List.of(), PLOT_BUILT);
            default -> null;
        };
        if (ground != null) {
            return ground;
        }
        if (hex.terrain().endsWith("Facility")) {
            return new Ground(surfaces + "concrete-ground", false, 0, List.of(), PLOT_BUILT);
        }
        return switch (hex.relief()) {
            case HIGH -> new Ground(surfaces + "rock-ground", false, 3, PEAKS, PLOT_PEAKS);
            case RAISED -> new Ground(surfaces + "grassland-ground", false, 2.5f, HIGHLAND, PLOT_HILLS);
            default -> new Ground(surfaces + "grassland-ground", false, 1.6f, TEMPERATE, PLOT_OPEN);
        };
    }

    /**
     * The cliff whose rock the bare rocks scattered over {@code ground} are textured with, as its own soil and stone
     * would weather.
     */
    static String rock(Ground ground) {
        String surface = ground.surface();
        String cliff;
        if (surface.contains("desert") || surface.contains("arid")) {
            cliff = "arid-cliff";
        } else if (surface.contains("mars")) {
            cliff = "mars-cliff";
        } else if (surface.contains("lunar")) {
            cliff = "lunar-cliff";
        } else if (surface.contains("volcano")) {
            cliff = "volcano-cliff";
        } else if (surface.contains("snow") || surface.contains("ice") || surface.contains("tundra")) {
            cliff = "rock-ground";
        } else {
            cliff = "temperate-cliff";
        }
        return "surfaces/" + cliff;
    }

    /** Whether {@code hex} is open water. */
    static boolean water(Hex hex) {
        return hex.relief() == Relief.WATER || hex.terrain().endsWith("Sea") && !"FrozenSea".equals(hex.terrain());
    }

    /**
     * GPU thread: the surfaces named, as an array texture of their albedo and one of their normals, layer i the i-th
     * named; loaded again only when the names change. One that won't load is plain grey and flat.
     *
     * @return the albedo array's and the normal array's GL names
     */
    int[] surfaces(List<String> names) {
        if (!names.equals(surfacesLoaded) || albedoArray == 0) {
            deleteSurfaces();
            albedoArray = surfaceArray(names, "_albedo.png", 0xFF8A7F70);
            normalArray = surfaceArray(names, "_normal.png", 0xFF8080FF);
            surfacesLoaded = List.copyOf(names);
        }
        return new int[] { albedoArray, normalArray };
    }

    private static int surfaceArray(List<String> names, String suffix, int fallback) {
        int layers = Math.max(1, names.size());
        int handle = Gdx.gl.glGenTexture();
        Gdx.gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, handle);
        Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 4);
        Gdx.gl30.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_RGBA8, SURFACE_SIZE, SURFACE_SIZE, layers, 0,
              GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, null);
        for (int layer = 0; layer < names.size(); layer++) {
            Pixmap pixels = surface(MODELS.resolve(SURFACES + names.get(layer) + suffix), fallback);
            ByteBuffer data = pixels.getPixels();
            data.rewind();
            Gdx.gl30.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, SURFACE_SIZE, SURFACE_SIZE, 1,
                  GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, data);
            pixels.dispose();
        }
        Gdx.gl.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);
        Gdx.gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_LINEAR_MIPMAP_LINEAR);
        Gdx.gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_LINEAR);
        Gdx.gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL20.GL_TEXTURE_WRAP_S, GL20.GL_REPEAT);
        Gdx.gl.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL20.GL_TEXTURE_WRAP_T, GL20.GL_REPEAT);
        float anisotropy = GLTexture.getMaxAnisotropicFilterLevel();
        if (anisotropy > 1) {
            Gdx.gl.glTexParameterf(GL30.GL_TEXTURE_2D_ARRAY, GL20.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                  Math.min(8, anisotropy));
        }
        Gdx.gl.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, 0);
        return handle;
    }

    /** The picture at {@code file}, {@link #SURFACE_SIZE} square in RGBA; {@code fallback} all over if it won't load. */
    private static Pixmap surface(Path file, int fallback) {
        Pixmap loaded = null;
        if (Files.isRegularFile(file)) {
            try {
                loaded = new Pixmap(new FileHandle(file.toFile()));
            } catch (RuntimeException e) {
                LOGGER.warn("War table: couldn't load {}", file.toAbsolutePath(), e);
            }
        } else {
            LOGGER.warn("War table: no surface at {}", file.toAbsolutePath());
        }
        Pixmap out = new Pixmap(SURFACE_SIZE, SURFACE_SIZE, Pixmap.Format.RGBA8888);
        out.setBlending(Pixmap.Blending.None);
        if (loaded == null) {
            out.setColor(((fallback & 0xFFFFFF) << 8) | 0xFF);
            out.fill();
            return out;
        }
        if (loaded.getWidth() == SURFACE_SIZE && loaded.getHeight() == SURFACE_SIZE
              && loaded.getFormat() == Pixmap.Format.RGBA8888) {
            out.dispose();
            return loaded;
        }
        out.setFilter(Pixmap.Filter.BiLinear);
        out.drawPixmap(loaded, 0, 0, loaded.getWidth(), loaded.getHeight(), 0, 0, SURFACE_SIZE, SURFACE_SIZE);
        loaded.dispose();
        return out;
    }

    private void deleteSurfaces() {
        if (albedoArray != 0) {
            Gdx.gl.glDeleteTexture(albedoArray);
            Gdx.gl.glDeleteTexture(normalArray);
        }
        albedoArray = 0;
        normalArray = 0;
        surfacesLoaded = List.of();
    }

    /** The model a facility of {@code type} is shown with, and the props round it; a generic bunker for none. */
    static Kit facility(@Nullable FacilityType type) {
        if (type == null) {
            return new Kit(MILITARY + "fortification.glb", List.of(MILITARY + "sandbags.glb"));
        }
        return switch (type) {
            case MekBase -> new Kit(AUTHORED + "frontier-fabrication-workshop.glb",
                  List.of(FRONTIER + "floodlight.glb", MILITARY + "cargo-crate.glb", FRONTIER + "generator.glb"));
            case TankBase -> new Kit(AUTHORED + "frontier-cargo-terminal.glb",
                  List.of(COLONY + "vehicle-gate.glb", FRONTIER + "storage-tank.glb", FRONTIER + "floodlight.glb"));
            case AirBase -> new Kit(AUTHORED + "frontier-cargo-terminal.glb",
                  List.of(FRONTIER + "beacon-mast.glb", FRONTIER + "cargo-container.glb", FRONTIER + "floodlight.glb"));
            case ArtilleryBase -> new Kit("buildings/turrets/modular/artillery-heavy.glb",
                  List.of(MILITARY + "sandbags.glb", MILITARY + "cargo-crate.glb", FRONTIER + "generator.glb"));
            case SpacePort -> new Kit(AUTHORED + "imperial-transit-terminal.glb",
                  List.of(FRONTIER + "beacon-mast.glb", FRONTIER + "cargo-container.glb", TANKS + "fuel_tank_medium.glb"));
            case DataCenter -> new Kit(AUTHORED + "utopia-office-tower.glb",
                  List.of(FRONTIER + "comms-mast.glb", FRONTIER + "solar-array.glb", FRONTIER + "generator.glb"));
            case IndustrialFacility -> new Kit(AUTHORED + "utopia-fabrication-works.glb",
                  List.of(FRONTIER + "storage-tank.glb", COLONY + "wall-tanks.glb", FRONTIER + "generator.glb"));
            case CommandCenter -> new Kit(AUTHORED + "frontier-command-comms.glb",
                  List.of(FRONTIER + "comms-mast.glb", MILITARY + "sandbags.glb", FRONTIER + "floodlight.glb"));
            case BaseOfOperations -> new Kit(AUTHORED + "frontier-command-comms.glb",
                  List.of(FRONTIER + "supply-crates.glb", FRONTIER + "floodlight.glb", FRONTIER + "cargo-container.glb"));
            case EarlyWarningSystem -> new Kit(FRONTIER + "comms-mast.glb",
                  List.of(COLONY + "radiator.glb", FRONTIER + "generator.glb", FRONTIER + "solar-array.glb"));
            case SensorPost -> new Kit(FRONTIER + "beacon-mast.glb",
                  List.of(FRONTIER + "generator.glb", MILITARY + "sandbags.glb", FRONTIER + "solar-array.glb"));
            case JammingStation -> new Kit(COLONY + "radiator.glb",
                  List.of(FRONTIER + "comms-mast.glb", FRONTIER + "generator.glb", COLONY + "light-mast.glb"));
            case OrbitalDefense -> new Kit(MILITARY + "orbital-gun.glb",
                  List.of(MILITARY + "sandbags.glb", FRONTIER + "generator.glb", FRONTIER + "floodlight.glb"));
            case SupplyDepot -> new Kit(TANKS + "fuel_tank_heavy.glb",
                  List.of(FRONTIER + "supply-crates.glb", FRONTIER + "cargo-container.glb",
                        TANKS + "fuel_tank_medium.glb"));
            case FieldFortifications -> new Kit(MILITARY + "fortification.glb",
                  List.of(MILITARY + "sandbags.glb", MILITARY + "trench.glb", MILITARY + "cargo-crate.glb"));
            case MilitiaBarracks -> new Kit(AUTHORED + "frontier-trade-exchange.glb",
                  List.of(MILITARY + "sandbags.glb", FRONTIER + "floodlight.glb", MILITARY + "cargo-crate.glb"));
        };
    }

    /** GPU thread: the board model at {@code path} under {@code data/models}, coarse; null if it won't load. */
    @Nullable GlbProp prop(String path) {
        return props.computeIfAbsent(path, ignored -> {
            Path file = MODELS.resolve(path);
            if (!Files.isRegularFile(file)) {
                LOGGER.warn("War table: no model at {}", file.toAbsolutePath());
                return Optional.empty();
            }
            try {
                return Optional.of(GlbProp.load(file, 2, shared));
            } catch (RuntimeException e) {
                LOGGER.warn("War table: couldn't load {}", file.toAbsolutePath(), e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * GPU thread: the surface {@code name}'s albedo as a repeating, mipmapped texture, to map over untextured models;
     * null if it won't load.
     */
    @Nullable Texture detail(String name) {
        return details.computeIfAbsent(name, ignored -> {
            Path file = MODELS.resolve(SURFACES + name + "_albedo.png");
            if (!Files.isRegularFile(file)) {
                LOGGER.warn("War table: no surface at {}", file.toAbsolutePath());
                return Optional.empty();
            }
            try {
                Texture texture = new Texture(new FileHandle(file.toFile()), true);
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
                return Optional.of(texture);
            } catch (RuntimeException e) {
                LOGGER.warn("War table: couldn't load {}", file.toAbsolutePath(), e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** GPU thread: {@code image} as a mipmapped texture, its top row at v = 0; the caller disposes it. */
    static Texture texture(BufferedImage image) {
        Pixmap pixels = new Pixmap(image.getWidth(), image.getHeight(), Pixmap.Format.RGBA8888);
        pixels.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                pixels.drawPixel(x, y, (argb << 8) | (argb >>> 24));
            }
        }
        Texture texture = new Texture(pixels, true);
        pixels.dispose();
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        return texture;
    }

    @Override
    public void dispose() {
        props.values().forEach(prop -> prop.ifPresent(GlbProp::dispose));
        props.clear();
        shared.values().forEach(Texture::dispose);
        shared.clear();
        details.values().forEach(detail -> detail.ifPresent(Texture::dispose));
        details.clear();
        deleteSurfaces();
    }
}
