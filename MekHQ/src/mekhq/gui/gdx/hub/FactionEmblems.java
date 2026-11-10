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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import jakarta.annotation.Nullable;
import megamek.client.ratgenerator.RATGenerator;
import megamek.logging.MMLogger;

/**
 * The factions' emblems for the star map, white on clear so a batch tints them in a realm's colour. Each is found and
 * decoded off the GPU thread the first time it is asked for, and handed over as a texture a later frame; until then
 * it is absent. GPU thread, but for the decoding.
 */
final class FactionEmblems implements Disposable {
    private static final MMLogger LOGGER = MMLogger.create(FactionEmblems.class);
    private record Decoded(String key, @Nullable Pixmap pixmap) {
    }

    private final Map<String, Texture> emblems = new HashMap<>();
    private final Map<String, Boolean> asked = new HashMap<>();
    private final ConcurrentLinkedQueue<Decoded> decoded = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "StarMapEmblems");
        thread.setDaemon(true);
        return thread;
    });

    /** The emblem of the faction coded {@code code} in {@code year}, or null while it decodes or when it has none. */
    @Nullable Texture emblem(String code, int year) {
        if (code.isBlank()) {
            return null;
        }
        String key = code + '@' + year;
        if (asked.putIfAbsent(key, Boolean.TRUE) == null) {
            worker.execute(() -> decoded.add(new Decoded(key, decode(code, year))));
        }
        return emblems.get(key);
    }

    /** Uploads what the worker has decoded since the last frame. */
    void upload() {
        for (Decoded done = decoded.poll(); done != null; done = decoded.poll()) {
            if (done.pixmap() != null) {
                Texture texture = new Texture(done.pixmap(), true);
                texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
                emblems.put(done.key(), texture);
                done.pixmap().dispose();
            }
        }
    }

    private static @Nullable Pixmap decode(String code, int year) {
        try {
            File file = RATGenerator.getFactionLogoFile(year, code);
            if (!file.isFile()) {
                return null;
            }
            Pixmap source = new Pixmap(new FileHandle(file));
            Pixmap white = new Pixmap(source.getWidth(), source.getHeight(), Pixmap.Format.RGBA8888);
            for (int y = 0; y < source.getHeight(); y++) {
                for (int x = 0; x < source.getWidth(); x++) {
                    white.drawPixel(x, y, 0xFFFFFF00 | (source.getPixel(x, y) & 0xFF));
                }
            }
            source.dispose();
            return white;
        } catch (RuntimeException ex) {
            LOGGER.warn(ex, "Could not read the emblem of {}", code);
            return null;
        }
    }

    @Override
    public void dispose() {
        worker.shutdownNow();
        upload();
        emblems.values().forEach(Texture::dispose);
        emblems.clear();
    }
}
