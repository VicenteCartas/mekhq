# Command UI Pilot: Phase 0 Inventory

## Status

- **Phase:** 0 - Baseline and inventory
- **Branch:** `vc/command-ui-pilot`
- **Production UI changes:** None
- **Primary code owners:** `CampaignGUI`, `CommandCenterTab`
- **Baseline reference:** Established campaign at approximately 1300x768, captured 2026-07-21

## Purpose

This inventory records the current shell and Command Center behavior that the pilot must preserve. It identifies the
existing data that can support meaningful game feel without adding campaign mechanics.

The inventory is organized around four player needs:

1. **Situation:** What is happening now?
2. **Attention:** What changed or requires intervention?
3. **Action:** What can the player do next?
4. **Ongoing work:** What is currently in progress?

## Source Files Reviewed

### Shell

- `mekhq.gui.CampaignGUI`
- `mekhq.gui.menus.MekHQMenuBar`
- `mekhq.gui.enums.MHQTabType`
- `mekhq.gui.view.CurrentLocationPanel`
- `mekhq.gui.view.CommandSummaryPanel`
- `mekhq.gui.view.AdvanceTimePanel`
- `mekhq.resources.CampaignGUI`

### Command Center

- `mekhq.gui.CommandCenterTab`
- `mekhq.gui.DailyReportLogPanel`
- `mekhq.campaign.enums.DailyReportType`
- `mekhq.campaign.CampaignSummary`
- `mekhq.gui.model.ProcurementTableModel`
- `mekhq.resources.DailyReportType`

### Existing Visual Language

- `mekhq.gui.baseComponents.immersiveDialogs.ImmersiveDialogStyle`
- `mekhq.gui.baseComponents.roundedComponents.RoundedLineBorder`
- `mekhq.gui.utilities.BriefingStyle`
- `mekhq.gui.baseComponents.GradientMarkerBar`
- `mekhq.gui.baseComponents.SegmentedBar`
- `mekhq.gui.baseComponents.MHQCollapsiblePanel`

## Application Shell Inventory

| Region | Current component | Data/state | Actions | Refresh source | Pilot role |
|---|---|---|---|---|---|
| Window identity | `JFrame` title | Campaign title, faction, date | Window controls | New day, campaign activation | Persistent campaign identity |
| Application menu | `MekHQMenuBar` | File, markets, reports, view, campaign, help | Global commands | Menu construction and options changes | Quiet global command layer |
| Primary navigation | `EnhancedTabbedPane` | 12 standard tabs; StratCon may be absent or disabled | Switch workstation | Options changes, explicit tab activation | Workstation navigation |
| Location context | `CurrentLocationPanel` | Planet/system, transit or jump-point state, atmosphere, gravity, technology, industry, output, population, recharge/travel status | Open recruitment | Location, transit, mission events | Primary situation signal |
| Markets | `createMarketsPanel` | Automatic or manual market mode | Contract market, unit market | Campaign options, new day | Contextual acquisition actions |
| Command summary | `CommandSummaryPanel` | Funds, active-loan state, reputation and experience, StratCon combat strength | None | New day, options, transactions, loans, assets | Compact readiness telemetry |
| Time control | `AdvanceTimePanel` | Current campaign date | Advance one day, advance multiple days | New day | Primary turn action |
| Campaign controls | `createCampaignControlPanel` | Empty-campaign state, GM mode | Company generator, GM mode, glossary, bug report | Options and campaign state | Secondary/system actions |
| Location scope | Active-location combo | All forces, main force, or player base | Filter Hangar, Personnel, Warehouse, Repair Bay | Bases and selection changes | Persistent operational scope |
| Temporary personnel | Status labels and grouped panels | Temporary AsTechs, medics, soldiers, battle armor, vehicle crew, vessel crew | None | Personnel and options events | Logistics telemetry |
| Parts availability | Status label | AtB parts availability modifier or hidden for any-tech acquisition | None | Missions, logistics personnel, new day, options | Logistics telemetry |

## Primary Navigation Inventory

The standard workstations and existing keyboard mnemonics are defined by `MHQTabType`.

| Order | Tab | Operational interpretation | Dynamic behavior |
|---:|---|---|---|
| 1 | Command Center | Command information center | Always present |
| 2 | Navigation | Astrogation console | Always present |
| 3 | TO&E | Force organization | Always present |
| 4 | Briefing Room | Mission planning | Always present |
| 5 | Area of Operations | StratCon operations | Added/removed with options; disabled in mapless mode |
| 6 | Personnel | Personnel records | Always present |
| 7 | Hangar | Asset control | Always present |
| 8 | Repair Bay | Maintenance control | Always present |
| 9 | Warehouse | Logistics inventory | Always present |
| 10 | Infirmary | Medical operations | Always present |
| 11 | Finances | Financial ledger | Always present |
| 12 | Mek Lab | Design/refit workstation | Always present |

The pilot must retain tab order, dynamic StratCon behavior, mnemonics, and programmatic focus methods.

## Command Center Inventory

### Daily Activity Log

**Current presentation**

- Ten icon-only report tabs in an `EnhancedTabbedPane`
- HTML report content in a width-tracking `JTextPane`
- Hyperlinks navigate to related campaign UI
- New reports color the Command Center tab and relevant report tab
- Selecting a report channel clears its attention state

**Report channels**

| Channel | Existing meaning | Existing attention state |
|---|---|---|
| General | Daily campaign events | Per-channel unread flag |
| Battle | Combat and scenario outcomes | Per-channel unread flag |
| Personnel | Hiring, promotions, training, injuries, character events | Per-channel unread flag |
| Medical | Treatment, recovery, injuries, outcomes | Per-channel unread flag |
| Finances | Financial events | Per-channel unread flag |
| Acquisitions | Purchases, sales, assets, supplies | Per-channel unread flag |
| Technical | Repairs, refits, malfunctions, engineering | Per-channel unread flag |
| Politics | Standing changes, wars, political events | Per-channel unread flag |
| Skill checks | Skill and training rolls | Per-channel unread flag |
| Aggregate | Combined reports when enabled | Per-channel unread flag |

**Pilot opportunity**

- Preserve all channels and exact report content.
- Replace color-only attention with an accessible unread marker and count/digest if available without parsing HTML.
- Present the selected channel as a dispatch, not a generic text area.
- Do not invent severity by parsing prose during the pilot.

### Unit Assessment

The current campaign summary includes:

- Force reputation
- Experience rating
- Mission success rate
- Personnel summary
- Optional HR capacity
- Unit composition
- Unit damage status
- Transport capacity
- Cargo summary
- Optional facility capacities

Several values are links to detailed reports. Most values currently arrive as formatted HTML strings from
`CampaignSummary`.

**Pilot opportunity**

- Preserve exact values and report links.
- Group values into identity, readiness, people, mobility, and support.
- Avoid deriving new semantic status from formatted HTML where structured data is unavailable.
- Record structured-data needs separately if stronger progress or warning displays prove useful.

### Faction Identity

**Current presentation**

- Campaign faction icon scaled to 150 pixels
- Change Faction
- Faction Standing Report
- Diplomacy Report

**Pilot opportunity**

- Integrate faction art with unit identity and assessment instead of maintaining a separate narrow action column.
- Keep all three actions available as secondary commands.
- Do not add another frame around the existing faction icon asset.

### Current Objectives

The panel has two modes:

1. Story-arc campaigns display `getCurrentObjectives()`.
2. Other campaigns display net worth, monthly profit, active mission names, and current scenario deadlines.

Scenario deadlines already include days relative to the campaign date and warning color.

**Pilot opportunity**

- Treat current scenarios and story objectives as command priorities.
- Distinguish objective, deadline, financial context, and empty states.
- Keep exact mission/scenario names and dates.
- Never infer completion percentage when no structured progress exists.

### Procurement

**Current table**

- Name
- Type
- Cost per item
- Total cost
- Target
- Next check
- Quantity
- Priority

**Current actions**

- Purchase parts / parts market
- Parts Needed Report
- Parts In Use Report
- Procurement Priority
- Pause Procurement
- Resume Procurement
- Instant Repair/Salvage
- Increase/decrease quantities from keyboard or context menu

**Existing states**

- Empty or populated queue
- Procurement processing active or paused
- Total cost affordable or greater than available funds
- Item target, next check, quantity, and priority

**Pilot opportunity**

- Present the queue as ongoing logistics work.
- Keep the full table and keyboard behavior.
- Move pause/resume into one stateful control.
- Group reports and market access as secondary actions.
- Surface affordability and process state without obscuring exact costs.

## Existing Decision and Feedback States

The pilot can use these existing states without campaign-model changes.

| State | Source | Potential presentation | Player relevance |
|---|---|---|---|
| On planet / in transit / at jump point | Current location | Situation context, image, travel/recharge status | Determines available activity and time context |
| Hiring available / unavailable | Personnel market and hiring hall | Recruitment action state and explanation | Determines whether personnel can be recruited |
| Automatic / manual markets | Campaign options | Action label and mode | Changes contract/unit acquisition workflow |
| Active loan | Finances | Funds telemetry with loan context | Financial obligation |
| Report channel unread | Existing log nag flags | Accessible attention marker | New campaign event exists |
| Scenario due or overdue | Scenario date vs campaign date | Priority/deadline state | Can block advancing time |
| Overdue loan | Finances check | Critical blocker | Blocks advancing time |
| Invalid faction for date | Faction validity | Critical blocker | Blocks advancing time |
| Procurement running / paused | `isProcessProcurement()` | Ongoing-work state | Determines whether checks run |
| Procurement affordable / unaffordable | Funds vs total cost | Warning state with exact amounts | Determines whether queue can be funded |
| Procurement empty / populated | Shopping list | Empty state or work queue | Indicates pending logistics work |
| Unit damage summary | `CampaignSummary` | Readiness assessment | Indicates repair burden |
| Transport capacity | `CampaignSummary` | Mobility assessment | Indicates deployment capability |
| Cargo capacity | `CampaignSummary` | Logistics assessment | Indicates current load/capacity |
| Temporary staff pools | Human resources | Support telemetry | Indicates available temporary labor |
| Parts availability modifier | Campaign rules | Logistics telemetry | Affects acquisition difficulty |
| GM mode | Campaign state | Clearly marked system mode | Enables manual overrides |
| Empty campaign | Units, bases, personnel | Company Generator action | Initial setup path |

## Event and Refresh Matrix

| Event/change | Existing shell response | Existing Command Center response | Pilot implication |
|---|---|---|---|
| New day | Title/date, markets, parts availability, all tabs | Procurement, summary, objectives, all report channels | Primary feedback moment |
| New report | Command Center tab attention | Relevant report channel attention and append | Dispatch attention state |
| Transaction/loan/asset | Funds and parts-related shell data | Campaign summary | Financial telemetry update |
| Mission/scenario | Parts availability where relevant | Summary and/or objectives | Priority/readiness update |
| Transit complete/location change | Location panel and filters | Objectives | Situation transition |
| Person/unit/acquisition | Temp pools or availability where relevant | Summary and/or procurement | Readiness/logistics update |
| Options change | Tab presence, crew visibility, market labels, all tabs | Summary, procurement, faction icon | Layout/state visibility update |
| Active location selection | Filtered tabs refresh | No Command Center change | Persistent scope change |

The pilot should reuse these subscriptions. It should not add polling or duplicate campaign calculations.

## Action Hierarchy

### Primary

- Advance Day
- Resolve a blocking priority or due scenario when one exists
- Open the currently relevant command priority

### Contextual

- Recruitment when available
- Contract and unit markets
- Procurement queue actions
- Objective/mission/report links
- Advance multiple days

### Secondary

- Faction and diplomacy reports
- Parts reports
- Procurement settings
- Glossary

### System / exceptional

- GM mode
- Company Generator for an empty campaign
- Bug report
- Instant Repair/Salvage override

This hierarchy affects placement and emphasis only. It must not remove or disable actions beyond existing rules.

## Existing Asset Inventory

### Reuse

- Campaign faction icon returned by the campaign
- Current planet/system art selected by `CurrentLocationPanel`
- JumpShip art used while at jump points
- Existing advance-day and advance-multiple-days glyphs
- Existing MekHQ hexagonal identity image where application identity art is useful
- Existing unit, person, location, and faction imagery throughout the application

### Keep Theme-Drawn

- Command grid
- Section frames and dividers
- Status backgrounds
- Selection, hover, focus, warning, and critical states
- Tables, buttons, tabs, menus, scrollbars, fields, and spinners

### Do Not Adopt As General Shell Chrome

- Legacy tiny `widgets/monitor_*` bitmap tiles and frame fragments
- Legacy skinned button fragments
- Any asset that would require bitmap stretching at GUI scaling levels

## Reusable Component Inventory

### Strong candidates for reuse or evolution

- `BriefingStyle` for theme-aware section borders
- `MHQCollapsiblePanel` for optional detail groups
- `GradientMarkerBar` and `SegmentedBar` for real ranges or categorical state
- `ScalingWidthConstrainedPanel` for responsive shell regions
- `EnhancedTabbedPane` for existing navigation and report channels
- Standard FlatLaf controls and client properties

### Extract from immersive-dialog work

- Semantic color resolution
- Theme luminance handling
- Low-contrast grid painting
- Technical heading typography
- Centered rules and angular frame geometry

### Keep specific to immersive dialogs

- Portrait signal distortion
- Signal-quality profiles
- CRT aperture reveal
- Transmission source labels
- Incoming-transmission header/status language
- Game Information strip semantics

## Data Gaps and Constraints

### Formatted summary strings

Several `CampaignSummary` values are preformatted HTML. They can be regrouped and restyled, but reliable semantic
severity should not be inferred by parsing these strings. Any future structured status API should be proposed as a
separate improvement.

### Reports are HTML streams

Daily reports provide category and unread state, but not a normalized severity or concise summary. The pilot can improve
channel attention and presentation without attempting automatic prose classification.

### Objectives have mixed sources

Story objectives, financial entries, mission names, and scenario deadlines currently share a single list. The pilot
should visually distinguish known types during construction, not parse the final HTML strings afterward.

### Shell dimensions

The current shell uses fixed top-panel height and constrained widths. The pilot must preserve the 1024x768 supported
minimum. The first shell pass must retain the existing component order, grouping, dimensions, and internal panel
geometry while applying the visual language. Responsive recomposition and relocation are separate review steps that
require explicit user guidance.

### Platform behavior

Full-window content is currently proven only for immersive dialogs on supported Windows/FlatLaf configurations. The
main shell requires an explicit cross-platform prototype and fallback before acceptance.

## Phase 1 Inputs

The inventory supports these first shared primitives:

1. Semantic theme tokens
2. Scalable command-grid backdrop
3. Technical section header and centered rule
4. Restrained angular/subtle section panel
5. Compact telemetry label/value/status component
6. FlatLaf styling helpers for existing standard components

No Command Center layout should move until these primitives have a small gallery or focused visual test surface.
