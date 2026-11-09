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

- **HQ hub (home).** The command-post art from the start screen, with clickable parts: the
  monitors open the star map, the hangar door opens the hangar, the briefing table opens contracts,
  and so on. The day starts here, and it's where you return to.
- **Nav rail.** A slim, always-visible bar with an icon for each area (Command, Map, Forces,
  Roster, Hangar, Logistics, Finances), with hotkeys 1-9. It's for fast travel during long
  campaigns.
- **Command palette.** Ctrl+K, type ("repair", "hire", a person's name) and jump there. It covers
  everything the menu bar does today.

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

**Phase 1: the star map**
- The 3D interstellar map grows out of the start screen's star map. It replaces the 10k-line
  `InterstellarMapPanel` and `NavigationTab`.
- Features: travel routes, jump paths, faction borders by date, contracts and markets pinned on
  systems, and a time scrubber.

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
