# Command UI Pilot: Visual Specification

## Status

- **Version:** Phase 0 draft
- **Applies to:** Application shell and Command Center pilot
- **Primary goal:** Make campaign management feel like command play without reducing operational clarity
- **Reference:** Existing immersive-dialog visual language, adapted for persistent UI

## Experience Model

Every major pilot region should serve one of four purposes.

| Purpose | Question answered | Typical content |
|---|---|---|
| Situation | What is happening now? | Location, date, travel, identity, funds, readiness |
| Attention | What needs intervention? | New dispatches, deadlines, warnings, blocked work |
| Action | What can I do? | Advance time, markets, priority-specific commands |
| Ongoing work | What is in progress? | Procurement, objectives, repairs, travel, assignments |

Detailed tables remain available, but the page must communicate these four purposes before requiring table inspection.

## Visual Character

The interface should feel like a practical BattleTech command terminal maintained by a working force. It should be:

- Technical, not futuristic-glossy
- Utilitarian, not military cosplay
- Information-rich, not card-heavy
- Worn through context and imagery, not texture overlays
- Responsive to campaign events, not continuously animated
- Recognizably MekHQ, not a separate game launcher

## Semantic Color Roles

Colors must be resolved from the active theme and expressed as semantic roles. Exact values may vary between themes.

| Role | Meaning | Initial direction | Usage limits |
|---|---|---|---|
| Canvas | Application background | Active FlatLaf panel background | Dominant neutral |
| Surface | Section/readout background | Slight mix toward foreground or signal | Must remain close to canvas |
| Border | Structural separation | Theme component/separator color | Quiet by default |
| Signal | Active command-system identity | Teal/cyan family from immersive pilot | Headers, selected technical accents; not all text |
| Interactive | Clickable or selected state | Theme link/selection color | Links and standard control states |
| Information | Context that is useful but not urgent | Theme-aware amber or blue | Sparse supporting notices |
| Nominal | Healthy, complete, available | Theme-aware green | Always paired with text/icon/state |
| Caution | Degraded, due soon, constrained | Theme-aware amber | Meaningful campaign condition only |
| Critical | Blocked, overdue, unaffordable, invalid | Theme/user warning red | Rare and high-priority |
| Muted | Secondary metadata | Disabled/muted foreground | Must retain readable contrast |

### Color Rules

- Do not make the entire shell teal.
- Do not use color as the only indication of state.
- Preserve the user-configured warning and negative colors where campaign reports already use them.
- Keep standard FlatLaf selection, focus, disabled, and input states unless a tested semantic override is necessary.
- Derive subtle surfaces by mixing theme colors rather than hardcoding dark-only backgrounds.

## Typography

### Families

- Use the active FlatLaf/default application font for body text, controls, tables, and values.
- Continue using Noto Sans in HTML content where the application already relies on it.
- Use a monospaced font only for short technical headings, status identifiers, dates, codes, and compact telemetry.

### Hierarchy

| Level | Usage | Treatment |
|---|---|---|
| Application identity | Unit/campaign name in shell | Largest shell text; bold, restrained |
| Workstation title | Command Center or active tab context | Strong heading below application identity |
| Section heading | Dispatches, Assessment, Priorities, Logistics | Compact monospaced bold with rule |
| Body | Reports, descriptions, labels | Standard application font |
| Telemetry value | Funds, readiness, counts, dates | Standard or monospaced based on content |
| Metadata | Categories, timestamps, source labels | Smaller muted text |

### Type Rules

- Letter spacing remains zero.
- Do not scale font size with viewport width.
- Avoid all-caps paragraphs; reserve uppercase for short technical labels.
- Exact values remain selectable/readable where they are currently links or table cells.
- Long localized labels must wrap or yield space before shrinking below normal UI readability.

## Spacing and Dimensions

All dimensions are logical GUI pixels and must use the existing GUI-scaling utilities.

| Token | Initial value | Use |
|---|---:|---|
| Hairline | 1 | Rules and quiet borders |
| Thin gap | 2–4 | Closely related telemetry |
| Control gap | 6–8 | Controls in one action group |
| Section gap | 10–12 | Distinct content within a workstation |
| Region gap | 16 | Major shell/Command Center regions |
| Section inset | 8–12 | Frame-to-content spacing |

Fixed-format elements must declare stable dimensions or constraints:

- Title/header rows
- Navigation tabs
- Context/status strips
- Faction and location art
- Icon buttons
- Telemetry rows
- Table headers

Dynamic content must not resize these elements during refresh.

## Surfaces and Framing

### Application Canvas

- Use one full-window neutral canvas.
- A low-contrast command grid may appear behind unframed shell and Command Center regions.
- The grid must not reduce table or text contrast and must not animate.

### Major Regions

- Use unframed layouts or one restrained angular/subtle frame per major region.
- Do not place framed cards inside framed cards.
- Prefer a heading and divider over a complete border when the surrounding layout already establishes containment.

### Tables and Lists

- Keep standard FlatLaf table/list rendering.
- Use existing row sorting, selection, context menus, and keyboard actions.
- Avoid decorative grid lines unless they materially improve column tracking.
- Empty queues should display a compact empty state and yield space where the layout permits.

## Shell Specification

### Application Header

Must communicate:

- Campaign/unit identity
- Faction identity where useful
- Current date
- Current location/travel state
- High-level command status when a real state exists

The main title must be the campaign/unit name, not a generic slogan.

### Primary Navigation

- Preserve tab order and mnemonics.
- Present tabs as workstations within one command system.
- Use standard selected, hover, focus, disabled, and attention states.
- An attention marker must not rely only on changing the tab background color.

### Context Strip

The styling pass preserves the existing component order, grouping, constrained widths, and internal panel geometry.
It should express the following priorities through typography, color, borders, and interaction styling alone:

1. Location/travel
2. Date and time actions
3. Funds/readiness summary
4. Market access when relevant

Company Generator, GM mode, Glossary, and Bug Report are secondary/system controls and should not compete visually
with the campaign situation. Any ordering or placement change happens in a separate prototype after explicit live
review; it is not part of the initial styling pass.

### Status Strip

- Active Location remains a persistent scope control.
- Temporary personnel and parts availability remain exact telemetry.
- Preserve their existing order and one-line placement during the initial styling pass.
- Group related crew values without nested decorative boxes.
- Hide unavailable categories as current behavior does.
- Do not use the status strip for transient notifications.

## Command Center Specification

### Dispatches

- Daily reports remain category-based and retain full HTML content and links.
- The selected report should read as a dispatch surface.
- Unread channels receive a marker with accessible text; color is supplementary.
- Empty/date-only channels should not dominate the page.
- Do not infer severity by parsing report prose.

### Command Assessment

Group the current summary into:

- Identity: faction and campaign/unit name
- Readiness: reputation, experience, mission success, damage
- People: personnel and HR capacity
- Mobility: transport and cargo
- Support: facilities and other capacity

Exact values and existing links remain available. The faction icon is part of this assessment, not a separate card.

### Priorities

- Story objectives and active scenarios are primary priorities.
- Scenario deadlines show exact time remaining.
- Financial context is secondary unless it becomes a warning or blocker.
- Empty objectives use a compact nominal state rather than a large blank panel.
- Advance-day blockers use critical state only when the existing rules identify a blocker.

### Logistics Work

- Preserve the full procurement table.
- Show processing state: running or paused.
- Show exact total cost and affordability.
- Place queue actions adjacent to the queue state they affect.
- Consolidate pause/resume into one stateful command in the pilot.
- Keep reports and market access as secondary actions.
- Use a compact empty state when no procurement work exists.

## Controls

- Use standard FlatLaf controls by default.
- Use familiar icon-only buttons only when the icon is established and a tooltip/accessibility name is present.
- Use text or icon-plus-text for commands whose meaning is not universal.
- Use toggles for true binary modes such as GM mode.
- Use menus for large option sets and tabs for peer views.
- Keep visible focus indicators and logical keyboard traversal.
- Do not make routine controls look like alert banners.

## Imagery

### Reuse

- Campaign faction icon
- Location/planet/system art
- JumpShip art during jump-point states
- Existing advance-time glyphs
- Existing MekHQ identity image when application branding is needed
- Existing unit and personnel imagery in later workstations

### Avoid

- Legacy bitmap monitor/button frame fragments as scalable shell chrome
- Decorative atmospheric art that displaces operational content
- Additional frames around already-framed icon assets
- Newly drawn substitutes for existing campaign assets

## Motion and Feedback

### Allowed in pilot

- Brief emphasis when meaningful telemetry changes
- Accessible attention-state appearance for new dispatches
- Short completion feedback for existing actions
- Existing immersive-dialog communication effects

### Not allowed in pilot

- Delayed page navigation
- Continuous scanning, pulsing, or blinking
- Table row entrance animations
- Decorative background motion
- CRT reveals on routine workstations

### Reduced motion

No new shell/Command Center motion ships without a single preference or system-aware mechanism that disables it.

## Responsive Composition

Initial modes, to be tuned with live prototypes:

### Standard and wide

- Persistent context strip across the top
- Dispatches, assessment, and priorities visible together where space permits
- Logistics work spans the lower region

### Compact desktop

- Preserve situation and attention above administrative detail
- Allow assessment/priorities to reflow rather than shrink text
- Keep primary actions visible
- Keep procurement table scrollable without forcing the entire page wider than the window

The supported minimum remains 1024x768. The pilot must also be checked at 1300x768 because that is the first supplied
real-world baseline.

## Accessibility

- All custom-painted state has a text or accessible-name equivalent.
- Attention markers expose the relevant report category.
- Color roles meet readable contrast against their surfaces.
- Focus remains visible on every standard and custom control.
- Icons have tooltips and accessible descriptions.
- Table and list keyboard behavior remains unchanged.
- Reduced motion is available before new persistent-screen animation.

## Phase 1 Component Contracts

### Theme tokens

- Resolve all colors at paint/use time or refresh correctly after theme changes.
- Expose semantic roles, not page-specific color names.

### Backdrop

- Paint a scalable, nonanimated grid.
- Remain visually subordinate to all content.
- Support opaque content surfaces without artifacts.

### Section header

- Support title, optional status, and optional trailing control.
- Keep compact stable height across content updates.

### Section panel

- Support framed and divider-only variants.
- Avoid requiring nested panels for padding.

### Telemetry

- Support label, exact value, semantic state, tooltip, and accessible description.
- Never replace exact values with color alone.

### Component styler

- Apply FlatLaf client properties without replacing component classes.
- Be safe when a non-FlatLaf look and feel is active.

## Phase 0 Decisions

- The pilot is branch-only on `vc/command-ui-pilot`.
- It is based on `vc/immersive`, but `vc/immersive` remains independently shippable.
- The first implementation target is the shell and Command Center only.
- The pilot preserves current workflows and campaign mechanics.
- Standard controls remain FlatLaf controls.
- Communication-specific effects remain in immersive dialogs.
- The first baseline target is approximately 1300x768 with a supported minimum of 1024x768.

## Remaining Validation Inputs

These improve validation but do not block Phase 1:

- Narrow 1024x768 baseline screenshot
- Dense campaign with reports, objectives, and procurement entries
- 150% or 200% GUI-scale baseline
- Representative light-theme screenshot
- Linux and macOS screenshots during Phase 4
