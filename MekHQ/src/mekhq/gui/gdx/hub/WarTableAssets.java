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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import jakarta.annotation.Nullable;
import megamek.logging.MMLogger;
import mekhq.campaign.digitalGM.stratCon.facility.StratConFacility.FacilityType;

/**
 * The models the war table stands on the board the game's renderer draws, from the board's own art: the buildings and
 * props the facilities are modelled with, and the forces' icons as textures. The mapping is static; the models load on
 * the GPU thread the first time they're asked for and stay until the table is disposed.
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

    private final Map<String, Texture> shared = new HashMap<>();
    private final Map<String, Optional<GlbProp>> props = new HashMap<>();

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
    }
}
