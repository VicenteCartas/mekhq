# MekHQ Command UI Pilot Plan

## Status

- **Phase:** Phase 1 implemented; visual review pending
- **Pilot scope:** Application shell and Command Center
- **Implementation branch:** `vc/command-ui-pilot`, based on `vc/immersive`
- **Evaluation mode:** Branch-only pilot; no permanent alternate UI mode
- **Broader application migration:** Not approved or estimated in detail until the pilot is reviewed

## Goal

Create a live, functional pilot that makes managing a MekHQ campaign feel like playing the role of a BattleTech force
commander rather than operating a themed spreadsheet. The pilot should present MekHQ as a coherent command interface
while preserving the existing campaign workflows, information density, keyboard behavior, and business logic.

For this project, **gamification** means presenting existing campaign state, decisions, consequences, progress, and
urgency through clear in-world context and responsive feedback. It does not mean adding arbitrary points, badges,
currencies, rewards, or new rules.

The pilot must answer these questions:

1. Does routine campaign administration feel more like making decisions in a running campaign?
2. Can players quickly understand the current situation, important changes, and useful next actions?
3. Does the command-interface visual language work for persistent application screens as well as immersive dialogs?
4. Does it improve hierarchy and feedback without making routine administration theatrical or slower?
5. Can the visual language be implemented through reusable components instead of screen-specific painting?
6. Does it remain usable across supported themes, platforms, display sizes, and GUI scaling levels?
7. Is the result strong enough to justify migrating the remaining tabs?

## Baseline: Current Command Center

The first baseline screenshot was captured on 2026-07-21 from an established campaign at approximately 1300x768.

### Existing Screen Structure

The current page is divided into six horizontal layers:

1. Application menus and window title
2. Location, markets, command summary, date, time controls, and campaign controls
3. Primary navigation tabs
4. Command Center content
5. Persistent glossary links
6. Active-location and temporary-personnel status

The Command Center content itself contains:

- A large Daily Activity Log with category tabs
- Faction identity and faction actions
- A campaign summary containing reputation, experience, personnel, composition, damage, transport, cargo, and facilities
- Current objectives
- A procurement table with related actions and total cost

### Baseline Strengths

- The page exposes a large amount of useful campaign state without hiding precise values.
- Current location, date, finances, readiness, objectives, reports, and work queues are all reachable from one screen.
- Detailed values frequently link to deeper reports.
- Existing tables and controls support repeated campaign administration efficiently.
- Campaign identity is represented through location and faction imagery.

### Baseline Problems To Test

- The current situation is distributed across the title, top strip, center summary, and footer.
- Most sections and actions have similar visual weight, so urgent information does not stand out from routine data.
- Empty or low-activity logs and work queues still occupy large portions of the page.
- Actions are often separated from the state they affect, requiring the player to infer context.
- The Daily Activity Log presents raw channels but no immediate digest of what changed or needs attention.
- The campaign summary is useful but reads as a dense list rather than a command assessment.
- Several layers of tabs, links, and status controls reduce the vertical space available to campaign content.
- The screen describes the campaign accurately but does not yet frame it as a situation requiring command decisions.

### Pilot Hypothesis

The same data and actions can feel substantially more like play if they are reorganized into four explicit purposes:

1. **Situation:** current location, date, travel, identity, funds, and readiness
2. **Attention:** new reports, warnings, blocked work, and active priorities
3. **Action:** the most relevant available commands in the context of affected state
4. **Ongoing work:** procurement, repairs, objectives, and other processes with meaningful status

The pilot should validate this hypothesis without removing the detailed reports, tables, links, or exact values used by
experienced players.

## Scope

### Included

- Main MekHQ window shell in `CampaignGUI`
- Application title/header treatment
- Primary navigation tabs
- Persistent campaign context strip
- Persistent status strip
- Command Center layout and presentation
- Existing Command Center actions and interactions
- Contextual presentation of existing decisions, alerts, progress, and consequences
- Clear feedback when important campaign state changes
- Reusable visual primitives required by the shell and Command Center
- Theme-aware colors and standard FlatLaf controls
- Responsive behavior and GUI scaling
- Accessibility and reduced-motion considerations
- Windows, Linux, and macOS fallback behavior

### Not Included

- Redesigning campaign rules or business logic
- Adding new currencies, scores, rewards, progression systems, or campaign mechanics
- Hiding information to create artificial uncertainty or suspense
- Renaming established campaign concepts solely for flavor
- Rewriting data models, table models, or event handling
- Migrating every tab during the pilot
- Restyling every secondary dialog
- Adding animation to routine tables, forms, or repeated actions
- Maintaining two permanent implementations of the Command Center
- Applying transmission distortion or CRT effects to non-communication content

## Design Principles

### Make Administration Feel Like Play

The pilot should change how existing campaign management is presented, not change the underlying game. Each major area
should help the player answer three questions:

1. What is happening now?
2. What requires my attention?
3. What decisions or actions are available?

Gamification should come from the campaign itself:

- Present objectives as active command priorities, including progress and blocked states.
- Present daily reports as dispatches whose categories and urgency are easy to distinguish.
- Present procurement, repairs, travel, and personnel processes as work in progress with visible completion state.
- Surface meaningful changes after advancing time instead of requiring players to rediscover them in tables.
- Connect actions to their likely effect using existing tooltips, descriptions, and game information.
- Use faction identity, location art, unit imagery, portraits, and campaign status to maintain continuity.
- Preserve detailed tables as operational tools, but place them within a clearer situation-and-action hierarchy.

Avoid superficial gamification:

- Do not award points or badges for routine administration.
- Do not add decorative meters without real campaign meaning.
- Do not replace precise values with vague visual indicators.
- Do not add extra confirmation steps solely to make an action feel dramatic.
- Do not invent terminology when an established campaign term is clearer.

### One Command System, Different Workstations

The application should feel like one command system whose screens serve different operational purposes. It should not
make every page look like an incoming transmission.

| Area | Intended presentation |
|---|---|
| Command Center | Command information center |
| Briefing Room | Mission planning console |
| Personnel | Personnel records terminal |
| Hangar | Asset control |
| Repair Bay | Maintenance control |
| Warehouse | Logistics inventory |
| Infirmary | Medical operations |
| Finances | Financial ledger |
| Navigation | Astrogation console |

### Preserve Operational Clarity

- Keep dense operational screens dense and scannable.
- Retain familiar labels and workflows.
- Use headings, dividers, alignment, and spacing before introducing additional containers.
- Avoid nested cards and excessive decorative frames.
- Keep standard controls visually standard so their behavior remains predictable.

### Use Custom Painting Selectively

Custom painting is appropriate for:

- Background grids
- Angular section frames
- Technical section headers
- Telemetry and status displays
- Maps, diagrams, charts, and communication screens

Standard FlatLaf styling should remain responsible for:

- Buttons
- Text fields
- Combo boxes
- Spinners
- Tables
- Scrollbars
- Menus
- Checkboxes and radio buttons

### Keep Motion Contextual

- Transmission screens may use the CRT reveal and signal effects.
- Important telemetry changes may use a brief, restrained state transition.
- Routine navigation and data entry should not wait for animation.
- The shared visual system must support reduced motion before animation is used outside immersive dialogs.

### Reuse Existing Assets

- Use campaign faction icons, location art, unit art, portraits, and existing status components.
- Do not redraw existing icons or wrap already-framed assets in additional frames.
- Add new visual assets only when the existing data set has no suitable source.

## Proposed Pilot Layout

```text
┌ UNIT NAME / COMMAND NETWORK ───────────── LOCATION ─ DATE ─ STATUS ┐
├ Current location ─ Readiness ─ Markets ─ Time controls ───────────┤
│                                                                  │
│ Daily dispatch       Unit overview               Objectives       │
│ and report channels  Faction identity            and alerts      │
│                      Reputation / funds                           │
│                                                                  │
├ Procurement and logistics queue ──────────────────────────────────┤
└ Active location ─ Temporary personnel ─ Availability ─────────────┘
```

This is a composition target, not a requirement to rename or replace the existing controls. The pilot should reuse the
current location panel, command summary, faction identity, reports, objectives, procurement table, market actions, and
time controls wherever their existing behavior is sound.

## Proposed Architecture

The reusable visual language must not live inside `immersiveDialogs`. The current immersive-dialog implementation can
consume shared primitives, but transmission-specific animation and portrait effects should remain local to dialogs.

Provisional shared package:

```text
mekhq.gui.visual
├── MekHQVisualTheme
├── ConsoleBackdropPanel
├── ConsoleHeaderPanel
├── ConsoleSectionPanel
├── TelemetryPanel
└── ConsoleComponentStyler
```

The final class list should remain smaller if existing components can cover these responsibilities cleanly.

### Responsibilities

**MekHQVisualTheme**

- Resolve colors from `UIManager` and the active theme
- Define semantic color roles: signal, information, nominal, warning, critical, muted, and border
- Define shared spacing and typography constants
- Expose motion settings without owning animations

**ConsoleBackdropPanel**

- Paint the optional low-contrast command grid
- Remain inexpensive when covering a large application window
- Support light and dark themes

**ConsoleHeaderPanel**

- Provide technical heading typography and dividers
- Support optional leading and trailing content
- Avoid owning page-specific text or actions

**ConsoleSectionPanel**

- Provide restrained angular or subtle framing
- Support titled and untitled sections
- Avoid card nesting

**TelemetryPanel**

- Present compact label/value/status information
- Support accessible names and semantic state colors
- Avoid decorative status labels that do not communicate state
- Support meaningful progress, change, warning, and blocked states using existing campaign data

**ConsoleComponentStyler**

- Apply FlatLaf client properties consistently to existing tabs, tables, split panes, and scroll panes
- Avoid replacing standard Swing controls with custom-painted equivalents

## Implementation Phases

### Phase 0: Baseline and Visual Specification

**Status:** Complete for implementation. Additional baseline screenshots remain useful validation inputs but do not
block Phase 1.

Estimated effort: **2–3 developer days**

- Capture current screenshots at representative resolutions and scaling levels. One 1300x768 Command Center baseline
	has been captured; narrow, dense, and high-scaling references are still needed.
- Inventory all controls and information shown in the shell and Command Center.
- Inventory the decisions, ongoing processes, alerts, state changes, and likely next actions shown on those screens.
- Document semantic color roles, typography, spacing, borders, and motion rules.
- Define how the pilot communicates situation, attention, action, progress, and consequence.
- Identify which immersive-dialog code should be extracted and which must remain dialog-specific.
- Confirm whether the pilot is evaluated only on a branch or exposed through a temporary preview entry.

Deliverable: a short visual specification and screenshot baseline.

Phase 0 deliverables:

- [`command-ui-phase-0-inventory.md`](command-ui-phase-0-inventory.md)
- [`command-ui-visual-spec.md`](command-ui-visual-spec.md)
- Initial 1300x768 Command Center screenshot supplied on 2026-07-21

### Phase 1: Shared Visual Primitives

**Status:** Implemented on `vc/command-ui-pilot`; pending review in the live component gallery.

Estimated effort: **3–5 developer days**

- Introduce theme-aware visual tokens.
- Extract reusable grid, header, divider, and frame behavior.
- Add style helpers for standard FlatLaf controls.
- Keep immersive dialogs using the same visual tokens without moving their communication effects.
- Add focused paint, sizing, theme, and lifecycle tests for custom components.

Deliverable: reusable components demonstrated in a small component gallery or test panel.

Implemented Phase 1 deliverables:

- `MekHQVisualTheme`: theme-aware semantic colors, typography, and scaled spacing
- `ConsoleBackdropPanel`: scalable, nonanimated command grid
- `ConsoleHeaderPanel`: technical heading, centered rule, state, and trailing-control support
- `ConsoleSectionPanel`: angular, subtle, and divider-only sections
- `TelemetryPanel`: exact values with semantic and accessible state
- `ConsoleComponentStyler`: standard FlatLaf button, toggle, field, table, tab, split-pane, and scroll-pane styling
- `CommandUiComponentGalleryDialog`: branch-only live review surface under Manage Campaign
- Shared theme/grid integration for immersive dialogs without moving transmission-specific effects
- Focused dark/light, painting, accessibility, and FlatLaf-property tests

Rounded-control migration policy:

- `RoundedJButton` and `RoundedMMToggleButton` are migration targets, not shared visual foundations.
- New command UI code uses standard Swing controls with FlatLaf styling.
- Existing rounded controls are replaced as each shell/workstation area is migrated.
- A repository-wide mechanical replacement is out of scope because roughly 200 references across 48 files include
  layout assumptions and specialized subclasses that require local review.

### Phase 2: Application Shell Pilot

Estimated effort: **3–5 developer days**

Status: **Implementation complete; first live-feedback pass incorporated and final visual acceptance pending.**

Implemented in place:

- Theme-aware grid canvas around an opaque workstation surface, so shell texture does not bleed into tab content.
- Technical, semantic title borders for location, markets, command summary, and campaign date.
- Standard FlatLaf buttons and toggles with primary emphasis reserved for Advance Day.
- Underlined primary navigation with a fixed-size, non-color unread marker for Command Center reports.
- Conventional separate title and menu rows for the campaign frame; immersive dialogs retain integrated headers.
- Technical labels at the regular UI font size and primary tabs without vertical separators.
- One-line status telemetry with lightweight group separators instead of nested boxes.
- Original component order, grouping, constrained dimensions, tab order, mnemonics, and workflows preserved.

- Apply the visual language to the main frame content.
- Prototype the integrated title/header treatment with platform fallbacks.
- Restyle primary navigation without changing tab behavior.
- Restyle the existing top panel in place, preserving component order, grouping, dimensions, and child-panel geometry.
- Restyle the bottom status strip in place, preserving its order and one-line grouping.
- Make current date, location, travel state, important readiness information, and available time actions immediately
  legible as the persistent campaign situation.
- Provide restrained feedback after advancing time or receiving important campaign updates.
- Keep menu, focus, keyboard, and window behavior intact.
- Treat relocation or reprioritization as a separate live-review step performed only with explicit user guidance.

Deliverable: a functional shell around the unchanged existing tabs.

### Phase 3: Command Center Pilot

Estimated effort: **5–8 developer days**

Status: **Styling-in-place implementation complete; live visual acceptance pending.**

Implemented in place:

- Shared technical framing for campaign assessment, objectives, daily activity, and procurement regions.
- Underlined daily-report navigation with fixed-size non-color unread markers and accessible tooltip updates.
- Standard FlatLaf faction and procurement controls with all existing listeners and enable-state logic preserved.
- Shared table and scroll-pane treatment for objectives and procurement without changing models, sorters, or shortcuts.
- Technical/muted static assessment labels while exact dynamic values, hyperlinks, warnings, and report HTML remain intact.
- Original `GridBagLayout` constraints, panel order, faction art, report channels, procurement actions, and tutorial links
  preserved.

- Apply the shared visual language to existing Command Center sections without moving them in the initial pass.
- Review proposed relocation separately and recompose only the sections approved during live feedback.
- Preserve all report channels, links, objectives, procurement actions, and faction actions.
- Present reports as categorized dispatches with meaningful attention states.
- Present objectives and procurement as active work with visible status and progress.
- Make important changes and available next actions easier to identify after advancing time.
- Replace repeated custom-rounded controls with standard FlatLaf controls where appropriate.
- Apply shared section, table, tabs, and telemetry styles.
- Ensure the page responds correctly to narrow, standard, and wide windows.
- Keep animation limited to content that represents communications or meaningful state changes.

Deliverable: a fully usable Command Center using real campaign data.

### Phase 4: Validation and Review

Estimated effort: **3–5 developer days**

- Test supported themes and theme switching.
- Test Windows, Linux, and macOS.
- Test GUI scaling at 100%, 150%, and 200%.
- Test at 1024x768, 1920x1080, 2560x1440, and a wide desktop viewport.
- Test long localized strings and variable campaign data.
- Test keyboard navigation, focus visibility, accessible names, and reduced motion.
- Capture before/after screenshots and collect structured reviewer feedback.

Deliverable: review report, screenshots, defects, and recommendation for or against broader migration.

## Estimates

These are developer-effort estimates, not calendar estimates.

| Milestone | Estimate |
|---|---:|
| First reviewable visual mock-up | 5–8 developer days |
| Functional shell and Command Center pilot | 12–20 developer days |
| Production-ready pilot after review fixes | 15–25 developer days |
| Broad migration of major tabs, if approved | Additional 8–14 person-weeks |
| Secondary views, dialogs, and final polish | Additional 6–12 person-weeks |

The broader migration should be re-estimated after the pilot establishes stable components and exposes platform,
scaling, and localization costs.

## Acceptance Criteria

### Functionality

- Every current shell and Command Center action remains available.
- Existing campaign data and event updates continue to refresh correctly.
- No campaign rule or persistence behavior changes.
- Tables retain sorting, selection, context menus, and keyboard behavior.
- Navigation tabs, menus, links, and split panes remain functional.

### Visual Quality

- The shell and Command Center read as one coherent command interface.
- Information hierarchy is clearer than the current layout.
- Standard controls retain recognizable FlatLaf states.
- No text, controls, window buttons, or dynamic content overlap.
- The design works in both light and dark themes.
- The interface is not dominated by a single hue.
- Campaign, faction, location, and operational data remain the main visual signals.

### Game Feel

- The shell communicates the current campaign situation before presenting detailed administration controls.
- Important changes, warnings, completed work, and blocked work receive clear and proportionate feedback.
- Objectives and ongoing processes expose meaningful progress using existing campaign data.
- Available actions appear in the context of the state they affect.
- Advancing time produces understandable feedback about what changed and what now requires attention.
- Faction, unit, location, and personnel identity are visible where they provide useful campaign context.
- Detailed tables remain available without being the only way to understand the campaign state.
- The pilot adds no arbitrary rewards, scores, or mechanics.

### Responsiveness

- No horizontal clipping at the supported minimum window size.
- Layout remains stable at 100%, 150%, and 200% GUI scaling.
- Long labels wrap or resize without covering adjacent controls.
- Fixed-format elements have stable dimensions and do not shift during updates.

### Accessibility

- Keyboard traversal remains logical.
- Focus indicators remain visible.
- Custom controls expose useful accessible names.
- Semantic colors are not the only indication of state.
- Motion can be disabled before motion is introduced outside communication screens.

### Engineering Quality

- Shared presentation behavior is implemented once and reused.
- Dialog-specific effects do not leak into general-purpose components.
- No duplicated alternative implementation is retained after the pilot decision.
- Production and test compilation pass.
- Main and test checkstyle pass.
- Focused tests cover custom painting, sizing, lifecycle, and theme behavior.

## Test Matrix

| Dimension | Cases |
|---|---|
| Platform | Windows, Linux, macOS |
| Theme | Default dark, representative light, theme switch at runtime |
| GUI scale | 100%, 150%, 200% |
| Window | 1024x768, 1920x1080, 2560x1440, wide desktop |
| Campaign state | New campaign, established campaign, Clan campaign, GM mode |
| Content | Empty lists, large reports, many objectives, long location names, large procurement queue |
| Input | Mouse, keyboard-only, context menus, tab traversal |
| Motion | Enabled and reduced/disabled |

## Risks and Mitigations

### The Pilot Becomes a Functional Rewrite

**Risk:** Visual work expands into model and workflow changes.

**Mitigation:** Preserve existing models and actions. Log workflow problems separately unless they block the pilot.

### The Style Becomes a Gimmick

**Risk:** Excessive grids, labels, terminology, or animation reduce readability.

**Mitigation:** Use the command-console metaphor to clarify real data. Keep routine operational controls quiet.

### Gamification Becomes Cosmetic or Manipulative

**Risk:** The pilot adds decorative meters, excessive alerts, or artificial rewards without improving campaign
decisions.

**Mitigation:** Every status, progress display, alert, and transition must represent existing campaign state and help
the player understand a situation, consequence, or available action.

### Theme and Platform Differences

**Risk:** Full-window treatment and custom colors behave differently across platforms or themes.

**Mitigation:** Keep platform fallbacks, derive colors from `UIManager`, and include all supported platforms in Phase 4.

### GUI Scaling and Localization

**Risk:** Fixed dimensions or technical headings fail at high scaling or with long translations.

**Mitigation:** Test scaling and long labels during each phase instead of postponing them until final QA.

### Parallel Classic and Command UIs

**Risk:** A permanent optional mode doubles maintenance and allows behavior to diverge.

**Mitigation:** Use a branch or temporary preview mechanism for evaluation. If accepted, migrate the production view and
remove the pilot path.

### Broad Refactors Produce Unreviewable Pull Requests

**Risk:** Shell, shared components, and Command Center changes become one large review.

**Mitigation:** Land the work in reviewable phases: primitives, shell, Command Center, then validation fixes.

## Decision Gate

The team should review the pilot before any additional tab is migrated.

The review should answer:

1. Does MekHQ feel more like managing a living campaign and less like operating a spreadsheet?
2. Can players identify the current situation, important changes, and useful next actions more quickly?
3. Is the visual direction accepted?
4. Is the information hierarchy measurably better?
5. Are the shared components sufficiently general?
6. Should the design replace the current UI or remain experimental?
7. Which tab group should be migrated next?
8. What changes are required before estimating the broader conversion?

If the pilot is accepted, the suggested migration order is:

1. Briefing Room and Navigation
2. Personnel and Infirmary
3. Hangar, Repair Bay, and Warehouse
4. Finances and remaining operational tabs
5. Frequently used secondary dialogs
6. Long-tail views and final consistency pass

## Open Decisions

- Whether the main frame should use full-window content on Windows during the pilot
- Whether the context strip remains visible on every tab
- Which light theme is the required reference theme
- Where the reduced-motion preference should live
- Whether the final visual system is mandatory or selectable after acceptance

## Immediate Next Step

Begin Phase 1 with a small visual component gallery for semantic theme tokens, the scalable command grid, technical
section headers, section framing, telemetry states, and standard FlatLaf component styling. Do not change the live
Command Center layout until those primitives are reviewed together.
