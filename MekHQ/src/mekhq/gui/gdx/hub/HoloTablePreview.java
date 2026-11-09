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
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import com.badlogic.gdx.ApplicationListener;
import jakarta.annotation.Nullable;
import megamek.client.ui.clientGUI.boardview.gpu.GpuHost;
import megamek.client.ui.clientGUI.boardview.gpu.StartScreenContent.StarChart;
import megamek.logging.MMLogger;
import mekhq.MHQConstants;
import mekhq.gui.StartStarChart;

/**
 * Developer preview: the HQ holotable alone in the native window, the Inner Sphere of 3025 projected over it.
 * {@code gradlew :MekHQ:holotablePreview -Pglb=<file>}; without a file, {@code data/models/hq/holotable.glb}.
 */
public final class HoloTablePreview implements GpuHost.Scene {
    private static final MMLogger LOGGER = MMLogger.create(HoloTablePreview.class);

    private final Path glb;
    private final @Nullable StarChart chart;
    private final @Nullable Path capture;

    private HoloTablePreview(Path glb, @Nullable StarChart chart, @Nullable Path capture) {
        this.glb = glb;
        this.chart = chart;
        this.capture = capture;
    }

    /** Arguments: the GLB, then optionally a PNG to write one settled frame to. */
    public static void main(String... args) {
        Path glb = Path.of(args.length > 0 ? args[0] : "data/models/hq/holotable.glb");
        Path capture = args.length > 1 ? Path.of(args[1]) : null;
        StarChart chart = StartStarChart.read(new File(MHQConstants.PLANETARY_SYSTEM_DIRECTORY_PATH, "canon_systems.zip"),
              new File("data/universe/factions"), "3025-01-01", "Galatea");
        GpuHost.show(new HoloTablePreview(glb, chart, capture));
        // The window's thread doesn't keep the program alive; ended() exits it
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public ApplicationListener listener() {
        return new HoloTableView(glb, chart, capture);
    }

    @Override
    public String title() {
        return "MekHQ - HQ holotable preview";
    }

    @Override
    public void closeRequested() {
        GpuHost.end(this);
    }

    @Override
    public void ended(@Nullable Throwable failure) {
        if (failure != null) {
            LOGGER.error(failure, "The holotable preview failed");
        }
        System.exit(failure == null ? 0 : 1);
    }
}
