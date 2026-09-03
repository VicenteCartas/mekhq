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
package mekhq.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.JViewport;

import org.junit.jupiter.api.Test;

class InterstellarMapPanelRenderLayerCacheTest {
    @Test
    void hiddenEmptySystemsRenderOnlyWhenRequiredForNavigation() {
        assertFalse(InterstellarMapPanel.shouldRenderSystem(true, true, false, false));
        assertTrue(InterstellarMapPanel.shouldRenderSystem(true, true, true, false));
        assertTrue(InterstellarMapPanel.shouldRenderSystem(true, true, false, true));
        assertTrue(InterstellarMapPanel.shouldRenderSystem(true, false, false, false));
        assertFalse(InterstellarMapPanel.shouldRenderSystem(false, false, true, true));
    }

    @Test
    void renderPerformanceTrackerAggregatesAndResetsSamples() {
        InterstellarMapPanel.RenderPerformanceTracker tracker =
              new InterstellarMapPanel.RenderPerformanceTracker(0L);

                tracker.record(20_000_000L, 8_000_000L, 3_000_000L, 2_000_000L, 1_000_000L,
              2_000_000L, 7_000_000L, 3_000_000L, 100);

        assertFalse(tracker.shouldReport(4_999_999_999L));
        assertTrue(tracker.shouldReport(5_000_000_000L));
        String report = tracker.reportAndReset(5_000_000_000L);
        assertTrue(report.contains("frames=1"));
        assertTrue(report.contains(">16ms=1"));
        assertTrue(report.contains("background=3.0ms territory=2.0ms logos=1.0ms"));
        assertTrue(report.contains("visibleSystems=100"));
        assertFalse(tracker.shouldReport(10_000_000_000L));
    }

    @Test
    void optionViewLayoutComparisonDetectsGeometryChanges() {
        JPanel control = new JPanel();
        JViewport view = new JViewport();
        Rectangle controlBounds = new Rectangle(10, 20, 200, 300);
        Dimension viewSize = new Dimension(180, 280);
        Rectangle viewBounds = new Rectangle(1, 1, 198, 298);
        Point viewPosition = new Point(0, 0);
        control.setBounds(controlBounds);
        view.setView(new JPanel());
        view.setBounds(viewBounds);
        view.setViewSize(viewSize);
        view.setViewPosition(viewPosition);

        assertTrue(InterstellarMapPanel.isOptionViewLayoutCurrent(
              control, view, controlBounds, viewSize, viewBounds, viewPosition));
        assertFalse(InterstellarMapPanel.isOptionViewLayoutCurrent(
              control, view, new Rectangle(10, 20, 201, 300), viewSize, viewBounds, viewPosition));
    }

    @Test
    void identicalViewReusesRasterAcrossAnimationStyleRepaints() {
        InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.RenderViewKey> cache =
              new InterstellarMapPanel.RenderLayerCache<>();
        InterstellarMapPanel.RenderViewKey viewKey =
              InterstellarMapPanel.RenderViewKey.create(80, 60, 12.5, -7.25, 2.0);
        AtomicInteger rendererCalls = new AtomicInteger();

        BufferedImage first = cache.getOrRender(viewKey, 80, 60, graphics -> {
            rendererCalls.incrementAndGet();
            graphics.setColor(Color.CYAN);
            graphics.fillRect(0, 0, 80, 60);
        });
        BufferedImage second = cache.getOrRender(viewKey, 80, 60, graphics -> rendererCalls.incrementAndGet());

        assertSame(first, second);
        assertEquals(1, rendererCalls.get());
        assertEquals(1, cache.getRenderCount());
        assertEquals(Color.CYAN.getRGB(), second.getRGB(40, 30));
    }

    @Test
    void changedSameSizeKeyReusesAndFullyClearsRasterBeforeOneRerender() {
        InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.RenderViewKey> cache =
              new InterstellarMapPanel.RenderLayerCache<>();
        InterstellarMapPanel.RenderViewKey firstKey = viewKey(4, 2, 0.0, 0.0, 1.0);
        InterstellarMapPanel.RenderViewKey changedKey = viewKey(4, 2, 1.0, 0.0, 1.0);
        AtomicInteger rendererCalls = new AtomicInteger();
        BufferedImage first = cache.getOrRender(firstKey, 4, 2, graphics -> {
            rendererCalls.incrementAndGet();
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 4, 2);
        });

        BufferedImage changed = cache.getOrRender(changedKey, 4, 2, graphics -> {
            rendererCalls.incrementAndGet();
            graphics.setColor(Color.GREEN);
            graphics.fillRect(0, 0, 1, 1);
        });
        BufferedImage unchanged = cache.getOrRender(changedKey, 4, 2,
              graphics -> rendererCalls.incrementAndGet());

        assertSame(first, changed);
        assertSame(changed, unchanged);
        assertEquals(Color.GREEN.getRGB(), changed.getRGB(0, 0));
        assertEquals(0, changed.getRGB(3, 1), "transparent regions must not retain pixels from the old key");
        assertEquals(2, rendererCalls.get());
        assertEquals(2, cache.getRenderCount());
    }

    @Test
    void changedDimensionsAllocateNewRaster() {
        InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.RenderViewKey> cache =
              new InterstellarMapPanel.RenderLayerCache<>();
        BufferedImage first = cache.getOrRender(viewKey(4, 2, 0.0, 0.0, 1.0), 4, 2, graphics -> { });

        BufferedImage resized = cache.getOrRender(viewKey(5, 2, 0.0, 0.0, 1.0), 5, 2, graphics -> { });

        assertNotSame(first, resized);
        assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, resized.getType());
        assertEquals(2, cache.getRenderCount());
    }

    @Test
    void renderCacheSizeGuardAccepts4kAndRejectsOversizedOrInvalidLayers() {
        assertTrue(InterstellarMapPanel.canCacheRenderLayer(3840, 2160));
        assertFalse(InterstellarMapPanel.canCacheRenderLayer(8192, 8192));
        assertFalse(InterstellarMapPanel.canCacheRenderLayer(0, 2160));
        assertFalse(InterstellarMapPanel.canCacheRenderLayer(3840, -1));
    }

    @Test
    void viewAndCartographyDependenciesInvalidateOnlyAffectedLayers() {
        InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.RenderViewKey> backgroundCache =
              new InterstellarMapPanel.RenderLayerCache<>();
        InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.TerritoryRenderKey> territoryCache =
              new InterstellarMapPanel.RenderLayerCache<>();
        LocalDate date = LocalDate.of(3151, 4, 12);
        InterstellarMapPanel.RenderViewKey view = viewKey(80, 60, 0.0, 0.0, 1.0);

        renderLayers(backgroundCache, territoryCache, view, date, 1);
        renderLayers(backgroundCache, territoryCache, view, date, 1);
        assertEquals(1, backgroundCache.getRenderCount());
        assertEquals(1, territoryCache.getRenderCount());

        view = viewKey(80, 60, 1.0, 0.0, 1.0);
        renderLayers(backgroundCache, territoryCache, view, date, 1);
        view = viewKey(80, 60, 1.0, 0.0, 2.0);
        renderLayers(backgroundCache, territoryCache, view, date, 1);
        view = viewKey(96, 72, 1.0, 0.0, 2.0);
        renderLayers(backgroundCache, territoryCache, view, date, 1);
        assertEquals(4, backgroundCache.getRenderCount());
        assertEquals(4, territoryCache.getRenderCount());

        renderLayers(backgroundCache, territoryCache, view, date.plusDays(1), 1);
        renderLayers(backgroundCache, territoryCache, view, date.plusDays(1), 2);
        assertEquals(4, backgroundCache.getRenderCount(), "date and data do not affect the grid");
        assertEquals(6, territoryCache.getRenderCount());
    }

    @Test
    void liveAlphaChangesReuseRasterAndCompositeIndependently() {
          InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.TerritoryRenderKey> cache =
              new InterstellarMapPanel.RenderLayerCache<>();
          InterstellarMapPanel.TerritoryRenderKey key = new InterstellarMapPanel.TerritoryRenderKey(
              viewKey(4, 1, 0.0, 0.0, 1.0), LocalDate.of(3151, 4, 12), 1);
        BufferedImage layer = cache.getOrRender(key, 4, 1, graphics -> {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, 4, 1);
        });
        BufferedImage lowAlpha = new BufferedImage(4, 1, BufferedImage.TYPE_INT_ARGB);
        BufferedImage highAlpha = new BufferedImage(4, 1, BufferedImage.TYPE_INT_ARGB);

        drawLayer(lowAlpha, layer, 0.25);
        BufferedImage reusedLayer = cache.getOrRender(key, 4, 1, graphics -> {
            throw new AssertionError("animation progress must not rerender the raster");
        });
        drawLayer(highAlpha, reusedLayer, 0.75);

        assertSame(layer, reusedLayer);
        assertEquals(1, cache.getRenderCount());
        assertEquals(64, alphaAt(lowAlpha, 0));
        assertEquals(191, alphaAt(highAlpha, 0));
    }

        @Test
        void guiScaleSensitiveMetricsInvalidateOnlyTheFactionLogoRaster() {
          InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.FactionLogoRenderKey> cache =
              new InterstellarMapPanel.RenderLayerCache<>();
          InterstellarMapPanel.TerritoryRenderKey territoryKey = new InterstellarMapPanel.TerritoryRenderKey(
              viewKey(8, 6, 0.0, 0.0, 1.0), LocalDate.of(3151, 4, 12), 1);
          InterstellarMapPanel.FactionLogoRenderKey original =
              new InterstellarMapPanel.FactionLogoRenderKey(territoryKey, 36, 24, 100, 8, 2);
          InterstellarMapPanel.FactionLogoRenderKey scaled =
              new InterstellarMapPanel.FactionLogoRenderKey(territoryKey, 54, 36, 150, 12, 3);

          cache.getOrRender(original, 8, 6, graphics -> { });
          cache.getOrRender(original, 8, 6, graphics -> { });
          cache.getOrRender(scaled, 8, 6, graphics -> { });

          assertEquals(2, cache.getRenderCount());
        }

    @Test
    void cachedStaticLayersRemainBehindDynamicNavigationPixels() {
        BufferedImage territory = solidLayer(3, 1, Color.RED);
        BufferedImage emblem = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        emblem.setRGB(1, 0, Color.GREEN.getRGB());
        BufferedImage output = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        InterstellarMapPanel.drawRenderLayer(graphics, territory, 1.0);
        InterstellarMapPanel.drawRenderLayer(graphics, emblem, 0.5);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(2, 0, 1, 1);
        graphics.dispose();

        assertEquals(Color.RED.getRGB(), output.getRGB(0, 0));
        assertTrue(((output.getRGB(1, 0) >>> 8) & 0xff) > 0, "emblem alpha remains independent");
        assertEquals(Color.BLUE.getRGB(), output.getRGB(2, 0), "navigation paints after static cartography");
    }

    @Test
    void explicitClearReleasesCurrentRasterAndForcesRegeneration() {
        InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.RenderViewKey> cache =
              new InterstellarMapPanel.RenderLayerCache<>();
        InterstellarMapPanel.RenderViewKey key = viewKey(8, 6, 0.0, 0.0, 1.0);
        BufferedImage first = cache.getOrRender(key, 8, 6, graphics -> { });

        cache.clear();

        assertFalse(cache.hasImage());
        BufferedImage second = cache.getOrRender(key, 8, 6, graphics -> { });
        assertNotSame(first, second);
        assertTrue(cache.hasImage());
        assertEquals(2, cache.getRenderCount());
    }

    @Test
    void territoryLookupNeverBuildsAndExplicitDatePreparationDoes() {
        InterstellarMapPanel.PreparedRenderData<InterstellarMapPanel.TerritoryDataKey, String> preparedData =
              new InterstellarMapPanel.PreparedRenderData<>();
        AtomicInteger builderCalls = new AtomicInteger();
        InterstellarMapPanel.TerritoryDataKey firstDate = new InterstellarMapPanel.TerritoryDataKey(
              LocalDate.of(3151, 4, 12), 3);
        InterstellarMapPanel.TerritoryDataKey nextDate = new InterstellarMapPanel.TerritoryDataKey(
              firstDate.date().plusDays(1), 3);

        assertNull(preparedData.get(firstDate));
        assertEquals("atlas-1", preparedData.prepare(firstDate,
              () -> "atlas-" + builderCalls.incrementAndGet()));
        assertEquals("atlas-1", preparedData.get(firstDate));
        assertNull(preparedData.get(nextDate), "a render lookup cannot lazily build or return stale data");
        assertEquals(1, builderCalls.get());

        assertEquals("atlas-2", preparedData.prepare(nextDate,
              () -> "atlas-" + builderCalls.incrementAndGet()));
        assertEquals(2, preparedData.getPreparationCount());
    }

        @Test
        void hiddenRequestsDoNothingAndVisibleRequestsCoalesceToLatestKey() {
          AtomicBoolean showing = new AtomicBoolean();
          ArrayDeque<Runnable> eventLoop = new ArrayDeque<>();
          AtomicInteger preparationCalls = new AtomicInteger();
          AtomicReference<InterstellarMapPanel.TerritoryDataKey> preparedKey = new AtomicReference<>();
          InterstellarMapPanel.StaticCartographyPreparationQueue<InterstellarMapPanel.TerritoryDataKey> queue =
              new InterstellarMapPanel.StaticCartographyPreparationQueue<>(showing::get, eventLoop::addLast, key -> {
                preparationCalls.incrementAndGet();
                preparedKey.set(key);
              });
          InterstellarMapPanel.TerritoryDataKey firstDate = new InterstellarMapPanel.TerritoryDataKey(
              LocalDate.of(3151, 4, 12), 3);
          InterstellarMapPanel.TerritoryDataKey latestDate = new InterstellarMapPanel.TerritoryDataKey(
              firstDate.date().plusDays(1), 4);

          queue.request(firstDate);
          queue.request(latestDate);

          assertTrue(eventLoop.isEmpty(), "hidden map requests must not enqueue preparation");
          assertEquals(0, preparationCalls.get());

          showing.set(true);
          queue.request(firstDate);
          queue.request(latestDate);

          assertEquals(1, eventLoop.size(), "one event-loop runnable must serve the visible request burst");
          assertEquals(0, preparationCalls.get());
          eventLoop.removeFirst().run();
          assertEquals(1, preparationCalls.get());
          assertEquals(latestDate, preparedKey.get());
        }

    private static void renderLayers(
          InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.RenderViewKey> backgroundCache,
          InterstellarMapPanel.RenderLayerCache<InterstellarMapPanel.TerritoryRenderKey> territoryCache,
          InterstellarMapPanel.RenderViewKey view, LocalDate date, long revision) {
        backgroundCache.getOrRender(view, view.width(), view.height(), graphics -> { });
        InterstellarMapPanel.TerritoryRenderKey territoryKey =
              new InterstellarMapPanel.TerritoryRenderKey(view, date, revision);
        territoryCache.getOrRender(territoryKey, view.width(), view.height(), graphics -> { });
    }

    private static InterstellarMapPanel.RenderViewKey viewKey(
          int width, int height, double centerX, double centerY, double scale) {
        return InterstellarMapPanel.RenderViewKey.create(width, height, centerX, centerY, scale);
    }

    private static BufferedImage solidLayer(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static void drawLayer(BufferedImage destination, BufferedImage layer, double alpha) {
        Graphics2D graphics = destination.createGraphics();
        InterstellarMapPanel.drawRenderLayer(graphics, layer, alpha);
        graphics.dispose();
    }

    private static int alphaAt(BufferedImage image, int x) {
        return image.getRGB(x, 0) >>> 24;
    }
}
