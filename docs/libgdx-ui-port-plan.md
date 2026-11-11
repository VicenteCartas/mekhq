# MekHQ libGDX UI port plan

Goal: MekHQ feels like a game, not a database front end. We redo and reorganize the whole UI on
MegaMek's libGDX UI kit and design tokens, porting screen by screen, with the Swing UI as the
fallback for anything not yet ported.

Decisions so far:
- The libGDX campaign shell is the main window. Unported screens open as Swing windows above it.
- Navigation: an HQ hub as home, plus a nav rail, hotkeys and a command palette.
- The first feature after the foundations is the 3D star map.

## Where we start from

**MekHQ (Swing)**
- `mekhq/gui` has about 714 files, 383 of them dialogs.
- `CampaignGUI` has 12 tabs (`MHQTabType`), a 7-menu menu bar, a top bar and a status bar.
- Giants: `InterstellarMapPanel` (10.2k lines), `PersonnelTableMouseAdapter` (4.8k), `PersonViewPanel`
  (4.1k), `StratConPanel` (3.3k), `BriefingTab` (2.4k), `CampaignOptionsPane`.
- Patterns: JTable plus `TableRowSorter` everywhere, right-click `JPopupMenu`s, modal dialogs, about
  40 nag dialogs, HTML report panes and Swing trees.
- No presentation layer: the GUI calls `Campaign` directly, and refreshes are imperative
  (`refreshAllTabs`) on event-bus `@Subscribe`. `CampaignController` is only 140 lines.
- Already game-like: `ImmersiveDialogCore` (speakers, portraits, responses), StratCon, the
  briefings, and the new start screen and star map.

**MegaMek UI kit**
- `megamek.client.ui.gdx` holds about 47 widgets (about 12k lines): buttons, dialogs, card grids,
  trees, virtual lists, tabs, segmented controls, popovers, menus, form fields, sliders, steppers,
  filter chips, sort headers, health bars, badges, charts and a file dialog. It is backend-neutral,
  and checkstyle keeps it free of AWT and game code.
- Tokens live in `design-tokens.json` (`DesignTokens`): colour roles, a colourblind palette,
  dark/light themes, APP/GAME presentation, a type scale, spacing, radii, sizes and motion. Swing
  reads them through `design/swing`.
- Hosting: one native window and scenes swapped by `GpuHost`. Swing dialogs float above the GL
  window through `GpuNativeDialogOwner`.
- Blocker: `GpuHost.Scene`, `show()`, `end()` and `post()` are package-private, so only MegaMek
  can host scenes today.

## Architecture

```
Campaign (domain, EDT-owned)  <--  Commands (EDT)  <--  GL screens (GPU thread)
        |                                                  ^
   MekHQ event bus --> view-model snapshot builders --> GpuHost.post(snapshot)
```

1. **Campaign stays on the EDT.** `Campaign` isn't thread-safe and every mutation runs on the EDT
   today. GL screens never touch `Campaign`.
2. **View models are immutable snapshots.** Records such as `RosterRow`, `UnitCard` and
   `FormationNode` are built on the EDT from the campaign, then posted to the GPU thread.
   - The builders are plain Java with no Swing or libGDX, so they are cheap to unit-test.
   - They also form the presentation layer MekHQ lacks today.
3. **Commands carry intent.** A screen sends something like `AssignPilot(personId, unitId)`. A
   command handler on the EDT calls the existing domain code (lifting logic out of
   `PersonnelTableMouseAdapter` and friends as we go), then the event bus triggers new snapshots.
4. **Dirty flags replace `refreshAllTabs`.** Each screen subscribes to the events it cares about
   and rebuilds only its own snapshot.
5. **Packages**
   - MekHQ: `mekhq.gui.gdx`: shell, screens and widgets specific to MekHQ.
   - MekHQ: `mekhq.gui.model.view`: snapshot builders and commands.
   - MegaMek: generic widgets that are missing go into `megamek.client.ui.gdx`, not MekHQ, so both
     apps share them and polish lands once.
6. **Hybrid period.** The GL shell is the main window. Any screen that isn't ported opens its Swing
   tab or dialog, owned by the GL window the way the lobby's native dialogs are. `CampaignGUI`
   stays available behind a fallback setting until the last tab is ported, then it is deleted.

## Navigation

The 12 tabs and the menu bar are replaced by three things that work together:

- **HQ hub (home).** A 3D command post: a holotable in a dim room, projecting the star map
  around the force, with the campaign's top bar (date, location, funds, alerts) over it. The day
  starts here, and it's where you return to. It replaced an earlier idea of making the start
  screen's command-post art clickable.
- **Nav rail.** A slim, always-visible bar with an icon for each area (Command, Map, Forces,
  Roster, Hangar, Logistics, Finances), with hotkeys 1-7. It's for fast travel during long
  campaigns. An area with several screens opens a flyout listing them.
- **Command palette.** Ctrl+K, type ("repair", "hire", a person's name) and jump there. It covers
  everything the menu bar does today: screens, people, units and the menu items.

## Order of work

Each phase ships something playable and keeps everything else reachable through Swing.

**Phase 0: foundations (MegaMek + MekHQ)**
- MegaMek: make a small public hosting API: `GpuHost.Scene`, `show`, `end`, `post`, native dialog
  ownership. The start screen did this narrowly already; generalize it.
- MegaMek: let tokens be extended by an app (an overlay JSON for MekHQ roles) without forking the
  shipped file.
- MekHQ: the `CampaignSession` bridge (the EDT owner, the event-bus-to-snapshot pump and the command
  dispatch) and a first view-model test harness. Reuse the lobby's probes and smoke-test style.
  - In `mekhq.gui.model.view`: `CampaignSession`, `ViewModel`, `CampaignCommand` and the first
    snapshot, `TopBar`. Tests drive a session with `SessionHarness` (unittests), which gives it its own
    event bus and runs the Swing and GPU threads as queues by hand.
- MekHQ: the campaign shell scene: a top bar (date, funds, location, alerts), the HQ hub, the nav
  rail and the command palette. Every destination still opens the Swing tab for now.
  - In `mekhq.gui.gdx.hub`: `CampaignHub` opens from the start screen's HQ (Experimental) entry.
    It builds the campaign window hidden, shows it on the tab the rail or palette asks for, and
    hides it again when its close box is clicked. Menu items run from the palette with their
    dialogs over the hub. `HubArea` maps the rail's areas to the tabs, and `PaletteIndex` and
    `PaletteSearch` gather and rank the palette's entries.
- MekHQ: the startup welcome (licensing and disclaimer) shows on the start screen as MegaMek's
  libGDX `StartWelcome`, named for MekHQ; the Swing `LicensingDialog` is only the fallback when no
  start screen opens.

**Phase 1: the star map**
- The 3D interstellar map grows out of the start screen's star map. It replaces the 10k-line
  `InterstellarMapPanel` and `NavigationTab`.
- Features: travel routes, jump paths, faction borders by date, contracts and markets pinned on
  systems, and a time scrubber.
- It ships in slices, each playable:
  1. **The map (done).** The rail's Map (key 2), or a click on the holotable's map, opens a
     full-screen 3D map in the hub. The camera dives down onto the table's disc until it looks
     straight down, then the room fades into the map at the same view, coreward up. The hub's
     own chrome and its rail slide away (keys 1 to 7 and Ctrl+K still work), and the map's chrome
     slides in around it:
     - a header with the way back, where the force is, today and a search for a system;
     - the factions in view, at the bottom left;
     - a compass to coreward and the controls, at the bottom right;
     - the chosen system's detail, at the right.

     Leaving flies the map back to that view and reverses the dive. A click or Esc skips either
     way. Each system glows in its owner's colour, and the map pans, zooms toward the cursor and
     turns. The force's system has a pulsing ring with its jump range, and the current route runs
     star to star. Hovering a system names its owners. Clicking opens its detail: population, HPG,
     star, recharge, and the distance and jumps from the force. Plot a course still opens the
     Swing Navigation tab.
     - In `mekhq.gui.model.view`: `StarMap`, the snapshot of the systems, the force's system and
       its route, and `StarMap.detail` for a chosen system. The hub watches it only while the map
       is open.
     - In `mekhq.gui.gdx.hub`: `StarMapView` (camera, meshes, picking, search, legend),
       `StarMapShaders` and `StarLabels` (the names, laid out so they don't overlap).
       `HoloTableView` dives and fades into the map; `HubScreen` runs the transition and chrome.
  2. **Travel (done).** Choosing a system previews the course there, plotted as the Navigation tab
     plots it: the route dashes star to star on the map, and the detail adds the days in transit,
     the hours recharging on the way and, when the campaign pays for transport, the cost. Set
     course confirms it from the detail. The Navigation tab's checks, confirmation, payment and
     departure run as they do there, their dialogs over the hub. The button stays disabled, with
     the reason, while the force is under way or the course is blocked. Under way, the route is
     solid as far as the force has come, a marker shows where it is between its first two
     systems, and the map's header says where it is bound, the jumps and days left and whether
     it is making for the jump point, charging its drives or flying in. Navigation still opens
     the Swing tab, for multi-stop routes and route options.
     - `StarMap` adds `Detail.course` (`Course`: route, jumps, days, recharge, cost, `Departure`)
       and `StarMap.voyage` (`Voyage`: destination, jumps and days left, `Stage`, progress on
       the leg). `StarMap.plot` and `StarMap.assess` give the course to set out on.
     - `mekhq.gui.BeginTransit` sets the force out for both the Navigation tab and the hub.
       While the hub hosts the hidden campaign window, `ImmersiveDialogCore` still parents its
       dialogs on it (`CampaignGUI.isHosted`), so they show over the hub.
     - `StarMapView` draws routes with `StarMapShaders.ROUTE_*` and the force's marker;
       `HubScreen` adds Set course and the voyage line; `CampaignHub.travel` runs the departure.
  3. **Borders by date (done).** The map shows the realms' territory as soft glowing regions
     with crisp, two-tone borders; disputed ground is striped in the claimants' colours. A
     timeline along the bottom ("Inner Sphere history") scrubs the map's date from 2150 to
     3152: the territory, the systems' owners and colours, and the legend follow it. Era bands
     sit on the track, and a notch marks the campaign's date. Drag, click or scroll the timeline;
     `[` and `]` step a year (Shift: a decade). Today (key T) returns to the campaign's date.
     Scrubbing never changes the campaign's date; the map opens on it.
     - Territory is not a port of the Swing map's hex grid. It is a GPU influence field: each
       held system spreads its realm's influence about 45 ly. Space belongs to the strongest
       realm once that influence passes a threshold, so a lone world holds about 33 ly.
     - `Borders` is a per-date snapshot of the owners' claims, built off the GPU thread.
       `Borders.Realms` numbers realms the same on every date.
     - `BorderHistory` reads dates on a worker thread with an LRU cache and prefetch; the newest
       date asked for wins, so a frame never waits. It is invalidated when the systems change.
     - `InfluenceMap` (in `mekhq.campaign.universe.territory`) spreads the claims into a 4 ly grid
       off the GPU thread: per texel, its holder, the distance to their edge, and any dispute.
       `TerritoryField` uploads it as an integer texture. The territory shader interpolates
       reaches realm by realm and morphs one date's land into the next over 0.6 s, so borders
       sweep, not fade.
     - `TimeScrubber` is the timeline; `HubScreen` hosts it.
     - The Swing map's territory logic moved to `mekhq.campaign.universe.territory`
       (`TerritoryGrid` and friends). `InterstellarMapPanel` uses it unchanged in behaviour.
     - Game-map layer, reimagining the Swing map's overlays for 3D:
       - Lenses (key L) recolour the systems by a property and raise a column from each system
         when the camera tilts, which it does on its own when a lens comes on: the higher the
         level, the taller and brighter the column, glowing at its foot. A legend counts each
         level and says so. Through a lens only the best rated systems keep their names.
       - Realm names in wide capitals across their territory, with the faction's emblem under
         them; system names give way by rank and fade rather than pop.
       - Capital beacons (realm and region), an animated HPG backbone (H), and the force's jump
         reach as a wave spreading jump by jump (J).
       - Administrative regions (R) drawn where a realm's regions meet, read as the Swing map
         reads them; empty systems can be hidden (E).
       - Hovering a realm, or one of its systems, spotlights it; over open ground the card shows
         its holdings (systems, people, capital).
       - Contested ground is striped boldly in both claimants' colours, sliding slowly, inside a
         dashed rim; hovering it names both sides and what each holds.
       - Shift-drag measures distance in light years and jumps; Esc clears it.
       - Right-click opens a context menu (details, measure from the force, centre, zoom, today).
       - Deep-sky backdrop with nebulae and a starfield, a scale bar, and operation and base
         markers that stay visible zoomed out.
       - The header carries the route, not the controls: the voyage underway (leg progress,
         an eye to show or hide the route, frame it, cancel the trip with a confirming second
         press) or the course plotted to the chosen system (Set course, frame, clear). A help
         button lists the controls.
       - The system card's Details opens the system record beside it (`SystemDossier`, read on
         the Swing thread): the system, then a tab per world with its profile, environment,
         society, socio-industrial ratings and description, as the Swing planet view has them.
         Beside it, an icon opens the Navigation tab's route planner.
       - The system card and hover card show what matters to the force: hiring hall, academies,
         capital, A/B industry, recharge stations and any health advisory.
       - Hover cards stay off the chrome (header, legend, panels).
     - Still to come: bloom.
  4. **Pins.** Contracts and markets on their systems.
- Alongside the map, **transmissions (done).** The immersive dialogs (`ImmersiveDialogCore` and its
  subclasses) that the map's actions and other hub flows raise come in over the hub as
  transmissions instead of Swing windows. The hub dims and blurs behind them. The channel powers
  on like an old screen (a dot, a line, then the panel unfolds, and then the header decrypts) while the
  signal bars climb, the speakers' feeds tune in through the dialog's signal quality (snow, a
  rolling picture, colour split), the message types in with a voice trace and a pulse on the
  speaker's feed, and the responses light up. A feed stays live (a drifting interference bar, a
  colour fringe that grows with a rougher signal). The panel powers off to a line and a dot, and a
  queued transmission cuts straight in instead. A click or Enter skips the opening, which keeps
  the text readable within about 0.7s, and Reduce Motion drops the effects. Number keys take a
  response, Enter the focused one, Tab and the arrows move the focus, and Esc closes without a
  response, as the window's close box does. Extra controls (number spinners, combo boxes,
  checkboxes and their labels) show as the kit's and set the dialog's own, so totals the dialog
  works out update live. Callers don't change: a modal dialog still blocks its caller, which
  waits in a `SecondaryLoop`, so the Swing thread keeps dispatching and nothing deadlocks.
  - `Transmission` (in `mekhq.gui.model.view`) is the snapshot (speakers, message, responses,
    note, controls, signal) and the thread contract: a registered `Presenter` gets it on the
    Swing thread and returns a `Handle`; the answer comes back through the dialog's `Channel`.
  - `TransmissionRelay` (in the immersive dialogs package) offers each dialog to the presenter
    as it is shown, applies answers and control changes to the real dialog, and falls back to
    the window when nothing presents it or the dialog has something a transmission can't show
    (a picture among its controls, a text field, a custom speaker panel such as Ronin's).
  - `CampaignHub` presents only while the hub is in front (not under the campaign window or a
    Swing modal) and, as the hub ends, sends any unanswered transmission back to its window.
    `TransmissionView` draws them in `HubScreen`, over everything, and takes all input while
    one shows.
  - MegaMek's kit gained `UiRichText` (the dialogs' HTML, typing in), `UiSignalFeed`,
    `UiBackdropBlur` and `UiResponseButton`.

**Phase 2: the game loop**
- The day loop is MekHQ's main loop, so it should feel like "End Turn".
- **Advance Day**: a large end-turn control. The ~40 nag dialogs become an inbox of alerts and
  blockers shown before you advance, each with its fix-it action.
- **Daily report**: becomes an event feed with categories, icons, links and toasts, not HTML panes.
- **Command Center**: becomes the HQ home: today's events, contracts, alerts and quick actions.

**Phase 3: people and forces**
- **Roster**: a master-detail screen. Portrait cards or virtual rows on one side, the person dossier
  on the other. It replaces `PersonnelTab`, the `PersonViewPanel` sheet and the right-click
  monster.
- **Formations (TOE)**: an org chart with drag-and-drop assignment of units and crews. It replaces
  the Swing tree.
- **Hangar**: unit cards with condition, crew and readiness.

**Phase 4: contracts and operations**
- **Contract market**: job boards with cards. Negotiation becomes an immersive conversation built
  on `ImmersiveDialogCore` ideas.
- **Briefing**: the mission and scenario room.
- **StratCon**: a 3D hex operational map on the board renderer, replacing `StratConPanel`.
- **Scenarios**: launching a battle and resolving it becomes an after-action screen
  (`ResolveScenarioWizardDialog`).

**Phase 5: logistics**
- Repair: a work-order queue per tech, like a kanban.
- Warehouse and parts: inventory cards plus a shopping list.
- Infirmary: a ward view with doctors and patients.
- Finances: a dashboard with charts, a ledger and loans.
- Markets: unit, personnel and parts markets become shop screens.

**Phase 6: options and editors (last)**
- Campaign options: a searchable settings screen with presets and descriptions.
- GM tools and editors (the planetary system, contract and scenario editors) can stay Swing the
  longest; they're tools, not gameplay.
- Finally delete `CampaignGUI` and the Swing tabs.

## Swapping UI patterns for game patterns

| Swing pattern today | Game pattern |
| --- | --- |
| 12 tabs plus a 7-menu menu bar | HQ hub, nav rail, hotkeys and a command palette (see Navigation) |
| JTable with sorters | Card grids or virtual lists with filter chips, a sort control and saved views; tables only where density wins (ledgers) |
| Right-click `JPopupMenu` | A contextual action bar on the selection, plus a radial or popover menu; multi-select actions |
| Modal option dialogs | Side drawers and inline editing; modals only for real confirmations |
| Nag dialogs on advance day | An alert inbox and blockers in an end-turn panel ("3 issues before you advance") |
| HTML daily report | An event feed: typed entries, icons, links to the person, unit or contract, and toasts |
| Swing TOE tree | An org chart with drag-and-drop |
| Person dialog full of fields | A dossier: portrait, stat bars, traits as chips, history timeline, family graph |
| Market dialogs | Shop and job-board screens with cards, compare, and buy/hire buttons |
| Contract negotiation form | An immersive negotiation conversation with offer sliders |
| Campaign options tree | A settings screen with search, presets and difficulty-style summaries |
| Progress bars and waits | Motion: transitions between rooms, count-ups, the time-lapse while days advance |
| Text-only status | Iconography, status colours from tokens, badges and health bars |

## Tokens v3 and the kit

- **One source of truth.** MegaMek's `design-tokens.json` stays the base. MekHQ adds an overlay
  for its own semantic roles: personnel status, injury, morale, money up and down, faction colours,
  rarity and quality.
- **Use the GAME presentation mode** for MekHQ screens and APP mode for tools and editors.
- **New generic widgets go into MegaMek's kit**: dossier, kanban column, org chart node, ledger
  table, event feed item, inbox item, chart and command palette, each with its own UI library entry
  in the existing `GpuUiLibraryWindow`.
- **Unported Swing screens read the same tokens** through `design/swing`, so the hybrid period
  doesn't look like two programs.
- **Polish targets for the kit**: motion (enter, exit, stagger), focus and keyboard and controller
  navigation, colourblind checks, and UI scale.

## Risks

- **Scale.** About 700 files. Mitigation: port by screen, and have the Swing fallback reachable
  from every screen.
- **Hidden logic in GUI classes**, especially the mouse adapters and dialogs. Moving it into
  commands is a big part of the work, and it's worth it: the logic becomes tested and shared.
- **Threading.** Strict rule: the GPU thread never touches `Campaign`. Snapshots only.
- **Upstream churn.** MekHQ `main` keeps changing the Swing UI. Mitigation: port the stable domain
  surfaces first, and rebase the UI branch often.
- **Mods and plugins** that hook Swing tabs, and accessibility (screen readers), which Swing gives
  for free. Plan keyboard navigation and text scale early.
