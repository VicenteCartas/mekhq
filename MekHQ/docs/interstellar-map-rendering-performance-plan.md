# Interstellar Map Rendering Performance Plan

## Objective

Restore the preferred intrinsic star colors and crisp ownership or analytical rings without making the map feel crowded or sacrificing interaction performance on ordinary hardware.

The development laptop is substantially faster than the intended baseline hardware. A result near 16.7 ms on this machine is therefore not sufficient. The dense-map target on this machine is an average frame time below 10 ms, leaving meaningful headroom for slower systems.

## Non-Negotiable Behavior

- Never hide systems, labels, overlays, or controls merely because the user is panning or zooming.
- Keep the ambient star animation removed.
- Remove animated HPG information packets; retain only the already-dense static HPG network and station cues.
- Do not reintroduce blurred, individually rasterized ring glyphs.
- Hidden empty systems must not leave black circles or other artifacts.
- Preserve selection, hover, current location, route, capital, operation, GM override, restricted-system, base, HPG, and navigation semantics.
- Preserve equal faction segmentation for shared systems.
- Treat visual simplification as a semantic-zoom decision, not a temporary interaction-quality reduction.
- Keep the current ringless renderer available as a measured baseline until a higher-fidelity path meets the performance target.

## Performance Targets

Measure after warm-up at the same window size, date, map layer, route state, and camera path.

### Primary Dense Scenario

- Approximately 500 visible systems at medium zoom.
- Continuous representative panning for at least 10 seconds.
- Average frame time below 10 ms on the development laptop.
- 95th-percentile frame time below 16.7 ms.
- No warm interaction frame above 33 ms except a separately identified operating-system or JVM event.
- No rendering work may cause visible content to disappear during interaction.

### Additional Scenarios

- Detail zoom with full stars, rings, labels, and service markers.
- Long zoom with strategic contacts and priority information.
- Map-layer transition between faction and analytical modes.
- Active and proposed routes, including warnings and waypoint badges.
- Hover and selection movement across dense systems.
- Date change, resize, display-scale change, and empty-system visibility toggle.
- First render and cache regeneration, reported separately from warm pan performance.

Record average, median, p95, p99, maximum, frames over 16.7 ms, frames over 33 ms, visible-system count, cache regeneration count, and per-phase timings. Use JFR to investigate CPU and allocation regressions. Use `-Dsun.java2d.trace=count` only for pipeline diagnosis because tracing itself distorts timing.

## Current Baselines

These samples establish direction, not exact apples-to-apples comparisons, because pan paths and cache refresh counts differed.

| Renderer | Visible systems | Average frame | Systems phase | Other notable work |
| --- | ---: | ---: | ---: | --- |
| Intrinsic stars plus crisp vector rings | 560 | 19.5 ms | 7.5 ms | Territory 5.2 ms; overlays 3.2 ms |
| Ringless cores with redundant stars | 511 | 12.7 ms | 3.5 ms | Territory 4.2 ms; overlays 1.4 ms |
| Ringless cores without stars | 492 | 17.6 ms | 2.5 ms | Static 10.4 ms; territory 6.2 ms; overlays 4.6 ms |

The ringless experiment proves that live ring rasterization is expensive. It also proves that removing rings alone is insufficient: the no-star sample still exceeded the revised target because full-screen static layers and remaining overlays dominated that pan.

## Rendering Model

Organize rendering into three ownership groups.

### Retained Cartography

Content that is stable across ordinary pan frames:

- Background.
- Territory fills, borders, and faction emblems.
- Static HPG links for the selected detail level.
- System visual appropriate to the current semantic zoom band.
- Non-priority labels when enabled by the current band.

Render this content at exact device-pixel resolution. Reuse it through translation during pan and repaint only exposed regions. Never scale a small ring raster to screen size.

### Dynamic World Overlays

Content that changes independently of the retained cartography:

- Active and proposed routes.
- Route warnings and waypoint badges.
- Selection and hover.
- Current location and system-hop state.
- Operations, restrictions, GM overrides, and bases.
- Measurement and reachability annotations.

### Fixed Interface

Navigation instruments, layer controls, dialogs, and other Swing interface elements remain separate from the map surface.

## Semantic Zoom Strategy

The map should communicate less information when many systems compete for attention and progressively reveal detail as the visible population falls.

### Atlas Band

- Use compact selected-layer contacts.
- Hide ordinary system names.
- Keep only priority labels: selected system, hovered system, current location, route endpoints or waypoints, and other explicitly actionable exceptions.
- Prefer territory shape and faction emblems over per-system detail.

### Navigation Band

- Continue using compact selected-layer contacts instead of ownership or analytical rings.
- Keep ordinary labels hidden initially, then fade in only near the detail-band boundary if density permits.
- Show route and navigation information required for planning.
- Delay service markers and secondary annotations until they are legible and useful.

This extends the existing long-zoom simplification into the busy medium-zoom range. It should improve both readability and performance.

### Detail Band

- Restore intrinsic spectral star colors.
- Restore crisp ownership rings, equally segmented for shared faction ownership.
- Restore crisp analytical rings for non-faction layers.
- Show ordinary labels and detailed service markers.
- Preserve the existing detailed selection, hover, capital, route, and current-location treatments.

Full-fidelity vectors become active only when zoom has reduced the number of visible systems enough to make them visually useful and computationally affordable.

### Transition Rules

- Base thresholds on the existing semantic reference derived from the configured planet-name threshold.
- Move the navigation-to-detail transition later than its current approximately 2.4-3.0 start and 3.6-4.2 completion range.
- Tune labels, rings, and secondary markers independently rather than using one alpha for every detail category.
- Use short crossfades and hysteresis so wheel movement near a boundary does not flicker or repeatedly invalidate caches.
- Do not change semantic bands merely because a drag is in progress.

## Implementation Phases

### Phase 0: Reproducible Benchmark

1. Define saved camera positions and pan paths for dense, medium, and detail views.
2. Extend aggregate profiling with percentile reporting and explicit cache-hit, strip-refresh, and full-regeneration counts.
3. Record display resolution, Java2D pipeline, device scale, visible-system count, semantic band, and active layers with every benchmark.
4. Capture current ringless and restored ringed baselines under identical conditions.

Exit gate: repeated runs are stable enough to distinguish a 1 ms change without relying on subjective panning.

### Phase 1: Earlier Density Reduction

1. Split semantic zoom into independently tunable contact, ring, ordinary-label, and secondary-marker transitions.
2. Keep compact layer-colored contacts through medium zoom.
3. Hide ordinary labels in dense medium views while retaining priority labels.
4. Delay rings, stellar detail, service markers, and other nonessential annotations until detail zoom.
5. Conduct visual review at each transition and verify that navigation information remains available.

Exit gate: medium zoom is materially less busy. Record the performance delta at approximately 500 systems. If frame time remains above target, continue without weakening visuals further.

### Phase 2: Restore Detail Fidelity

1. Restore the accepted intrinsic star renderer in the detail band.
2. Restore direct vector ownership and analytical rings in the detail band.
3. Keep compact segmented cores in atlas and navigation bands.
4. Restore legend language and swatches for the band-specific behavior.
5. Verify that no tiny cached glyph or scaled intermediate softens the rings.

Exit gate: visual review accepts the detail renderer, and detail-zoom performance remains within the target because fewer systems are visible.

### Phase 3: Consolidated Pannable Cartography

1. Introduce an immutable cache key containing viewport dimensions, device scale, map scale, date, data revision, map mode, semantic band, relevant layer options, and empty-system visibility.
2. Render background, territory, faction emblems, static HPG links, semantic-band system art, and eligible labels into one exact-resolution retained surface.
3. Reuse the existing overscan and exposed-strip logic rather than redrawing the full surface during pan.
4. Keep dynamic overlays out of this surface so hover, selection, routes, and animations do not invalidate it.
5. During map-mode transitions, crossfade prepared previous and target retained surfaces without rerasterizing both on every animation frame.
6. Regenerate asynchronously from immutable prepared data where possible, retain the previous valid surface until replacement is ready, and swap on the EDT.

Exit gate: the primary dense scenario averages below 10 ms with p95 below 16.7 ms while using the intended medium-band visuals.

### Phase 4: GPU-Aware Java2D Surfaces

Only proceed if Phase 3 misses the target or full-screen blits remain unexpectedly expensive.

1. Capture `sun.java2d.trace=count` output for the default Windows pipeline.
2. Compare default, forced Direct3D, and OpenGL Java2D pipelines in isolated runs.
3. Test a `GraphicsConfiguration`-compatible image or validated `VolatileImage` mirror for settled retained content.
4. Confirm acceleration with `Image.getCapabilities(...)` and primitive tracing rather than assuming a JVM flag worked.
5. Reject any pipeline that causes visual differences, driver instability, or slower fallback behavior.

Do not rewrite frequently modified cache pixels directly in a GPU surface each frame. That can force repeated uploads and defeat acceleration. Prefer immutable retained surfaces or tiles that are uploaded once and blitted repeatedly.

Exit gate: adopt a non-default pipeline or accelerated surface only when it produces a repeatable gain across supported systems without correctness regressions.

### Phase 5: Tiled and Parallel Preparation

Only proceed if consolidated full-surface regeneration causes visible hitches or excessive upload traffic.

1. Partition retained cartography into bounded exact-scale tiles with overlap sufficient for antialiased rings and labels.
2. Generate missing tiles from immutable scene snapshots on background workers.
3. Upload completed tiles once and reuse them until their cache key changes.
4. Use a bounded least-recently-used cache with an explicit memory budget.
5. Resolve labels that cross tile boundaries through padded rendering, deterministic ownership, or a separate retained label layer.

Suggested initial memory budget: no more than 192 MiB of incremental map-render caches at 4K, subject to measurement.

Exit gate: cache regeneration no longer causes interaction stalls and warm dense panning remains below target.

### Phase 6: Dedicated GPU Renderer Decision

Proceed only if the retained Java2D design cannot meet the target on the development laptop or representative lower-end hardware.

1. Extract an immutable `MapSceneSnapshot` containing prepared visual data, geometry, style, and interaction state without campaign-domain lookups during drawing.
2. Define a narrow renderer boundary shared by Java2D and an experimental GPU backend.
3. Build a small 2D GPU spike that renders the dense benchmark, not a full application rewrite.
4. Use instanced quads or point sprites for stars, analytic shader rings, batched routes, retained territory meshes or textures, and a glyph atlas for labels.
5. Evaluate native packaging, Swing composition, HiDPI, multiple displays, screenshots, printing, headless tests, and driver compatibility before selecting a library.
6. Retain Java2D as a compatibility, export, printing, and test backend unless there is a demonstrated reason to remove it.

Prefer a maintained 2D GPU abstraction if it satisfies integration and packaging requirements. Do not choose raw Vulkan for this workload. JavaFX Canvas is not sufficient evidence of GPU acceleration and should not be treated as the automatic migration target.

Exit gate: the spike demonstrates a large, repeatable margin over the Java2D implementation and has an acceptable distribution and maintenance cost.

## Cache Correctness Requirements

Every retained layer or tile must invalidate correctly for:

- Camera scale, viewport size, and device-pixel ratio.
- Campaign date and cartography revision.
- Map mode and map-mode transition endpoints.
- Territory, faction, and system data changes.
- Empty-system visibility.
- HPG detail and other included layer settings.
- User-configured planet-name threshold and resulting semantic band.
- Theme or display changes that affect colors, fonts, strokes, or scaling.

Pan translation may reuse pixels only at the exact rendered scale. Fractional device-pixel movement must either trigger a correctly aligned refresh or use a proven phase-aware strategy.

## Validation

### Automated

- Semantic-band boundary and hysteresis tests.
- Priority-label visibility tests in atlas and navigation bands.
- Full-fidelity detail-band star and ring tests.
- Equal shared-faction segmentation tests.
- Cache-key invalidation tests.
- Pannable reuse and exposed-strip equivalence tests.
- Map-mode crossfade and stale-layer rejection tests.
- Hidden-empty-system regression tests.
- Existing route, capital, operation, territory, and interaction suites.
- Checkstyle, full MekHQ tests before publication, and `git diff --check`.

### Visual

- Review atlas, medium navigation, and detail bands on the real Swing display path.
- Verify crisp rings at normal and HiDPI scaling.
- Verify that medium zoom is calmer without concealing actionable systems.
- Verify transitions while slowly zooming in both directions.
- Verify no overlap among labels, markers, routes, and controls.
- Verify uninterrupted content during rapid pan and zoom.

### Performance

- Run the fixed benchmark matrix at least three times per candidate.
- Report medians across runs as well as individual p95 and p99 values.
- Profile representative lower-end hardware before declaring the work complete; the development-laptop target is a proxy, not proof of broad performance.

## Decision Gates

- If earlier semantic simplification alone reaches the target, retain it for readability and still restore full detail visuals.
- If Phase 3 reaches the target, stop architectural escalation and keep Java2D.
- If Phase 3 is fast when warm but hitches during regeneration, proceed to tiling and background preparation.
- If Java2D blitting or compositing keeps the dense scenario above target, perform the dedicated GPU spike.
- Do not permanently trade away the preferred detail visuals until the retained and GPU-aware paths have been measured.

## Expected Outcome

At medium zoom, the map should be quieter and faster because it uses compact contacts and priority-only labels. At detail zoom, intrinsic star colors and crisp rings should return when fewer systems are visible. During pan, retained cartography should move as cached pixels or tiles while only dynamic overlays are redrawn.

This design targets performance by reducing repeated work and matching detail to information density, rather than by degrading the display only while the user interacts with it.
