# Swing → JavaFX Migration: Analysis, Pitfalls & Caveats

This document captures what we learned from migrating a single, small dialog
(`AddFundsDialog`, the **Finances → "Add Transaction (GM)"** dialog) from
AWT/Swing to JavaFX, and what a full migration of MekHQ would entail.

It is a feasibility/architecture note, not a commitment to migrate.

---

## 1. Verdict: incremental migration is viable

A dialog-by-dialog migration **is** technically realistic because JavaFX ships
official Swing interop in both directions:

- **`JFXPanel`** — embeds a JavaFX `Scene` inside a Swing container. (Used here.)
- **`SwingNode`** — embeds a Swing component inside a JavaFX scene graph.

So the surrounding application stays Swing while individual windows are converted
one at a time. The key discipline is **preserving each migrated class's public
Java API** so existing call sites do not change. For `AddFundsDialog` we kept
`getClosedType()`, `getTransactionType()`, `getFundsQuantityField()` and
`getFundsDescription()` identical, so `FinancesTab` needed **zero** edits.

What was added to support this:

- `org.openjfx.javafxplugin` Gradle plugin + `javafx { modules = ['javafx.controls', 'javafx.swing'] }` in `MekHQ/build.gradle`.
- `mekhq.gui.baseComponents.AbstractMHQJavaFXDialog` — a reusable base class that
  centralises all the interop plumbing so individual dialogs cannot re-introduce
  the pitfalls below.

---

## 2. Pitfalls we actually hit

### 2.1 `implicitExit` shuts down the whole toolkit (caused a full app hang)

**Symptom:** open the dialog once → fine. Close it, open it a second time → the
entire application hangs.

**Cause:** JavaFX defaults to `Platform.setImplicitExit(true)`. When the last
JavaFX window/embedded panel is disposed, the FX runtime **shuts itself down**.
On the second open the new `JFXPanel` cannot restart the platform, so the
`Platform.runLater(...)` that builds the scene never executes, and the Swing EDT
blocks forever on the `CountDownLatch.await()` used to wait for the scene.

**Fix:** call `Platform.setImplicitExit(false)` **once** for the JVM lifetime.
This is now done inside `AbstractMHQJavaFXDialog` so no subclass can forget it.

**Lesson:** a per-dialog interop mistake can hang the *entire* application, not
just that dialog. This is the strongest argument for shared base plumbing and for
setting `implicitExit=false` globally at application startup in a real migration.

### 2.2 Two UI threads, forever

Swing uses the **Event Dispatch Thread (EDT)**; JavaFX uses the **JavaFX
Application Thread**. They are different threads and neither may touch the other's
components directly. Concretely:

- Build/modify JavaFX nodes only via `Platform.runLater(...)`.
- Hide/dispose the Swing window only via `SwingUtilities.invokeLater(...)`.
- Pass user input back across the boundary through `volatile` fields (the FX
  event handler writes them; the EDT getters read them after close).

This dual-thread choreography is the recurring "tax" of every migrated component
and persists until the **last** Swing component is gone.

### 2.3 Blocking the EDT while the scene is built

We need the dialog packed to its content size before showing it, but the scene is
built on the FX thread. We bridge with a `CountDownLatch`, which means the EDT
**blocks** until the FX thread finishes. That is acceptable for a tiny modal
dialog but is a latent deadlock source (see 2.1) and would not scale to large or
slow-to-build scenes. Alternatives (async sizing, pre-warming the FX runtime at
startup) should be considered for bigger views.

---

## 3. Caveats for a full migration

### 3.1 Dual look-and-feel during the transition

Swing (FlatLaf/system L&F) and JavaFX (CSS-themed) will not look identical while
both are on screen. A shared theme/CSS strategy is needed so migrated dialogs do
not look out of place next to un-migrated ones.

### 3.2 Shared widget library must be ported

MekHQ/MegaMek rely on a large set of custom **Swing** widgets and helpers, e.g.:

- `JMoneyTextField`, `MMComboBox`, `RoundedJButton`, and many `AbstractMHQ*` base
  components.
- The preferences system (`JWindowPreference` / `PreferencesNode`) is **Swing
  window**-oriented; `AddFundsDialog` still uses it because the outer window
  remains a `JDialog`. A pure-JavaFX window would need an equivalent.

Each reusable widget either needs a JavaFX equivalent, or must keep being used via
`SwingNode`. Until that library is ported, "JavaFX" dialogs are really
Swing-shells with JavaFX content (as `AddFundsDialog` is).

### 3.3 Packaging, modules, and native artifacts

- JavaFX ships **platform-classified native jars** (win/mac/linux). The
  distribution, launch4j config, and any module-path/`--add-modules` setup must
  account for this before shipping.
- Cross-platform builds must include the right native bundles for each target OS.

### 3.4 Startup cost

The first `JFXPanel` construction boots the JavaFX runtime, adding a one-time
delay. For a fully migrated app you would likely pre-initialise the FX toolkit
during splash/loading rather than lazily on first dialog open.

### 3.5 Testing & tooling

- UI tests that drive Swing (AssertJ-Swing, Robot, etc.) do not see JavaFX nodes;
  JavaFX needs its own (TestFX) tooling.
- Accessibility, focus traversal, keyboard shortcuts, and HiDPI scaling behave
  differently between the toolkits and must be re-verified per screen.

---

## 4. Recommended approach if pursued

1. **Set `Platform.setImplicitExit(false)` once at application startup** (not per
   dialog) and pre-warm the FX runtime during loading.
2. **Migrate leaf dialogs first** (small, self-contained, clear input/result
   contract) — exactly the `AddFundsDialog` profile.
3. **Always preserve the existing public Java API** of a migrated class so call
   sites are untouched; migrate call sites later, separately.
4. **Funnel all interop through `AbstractMHQJavaFXDialog`** (and a future
   `SwingNode`-based equivalent) so the threading/implicit-exit pitfalls are
   solved exactly once.
5. **Port the shared widget library deliberately**, prioritising the most-reused
   controls (money field, combo boxes, buttons, preference persistence).
6. **Establish a shared CSS theme** early so the half-migrated UI stays coherent.
7. **Add TestFX** alongside the existing Swing test tooling.

---

## 5. Files involved in the reference migration

- `MekHQ/build.gradle` — JavaFX plugin + modules.
- `MekHQ/src/mekhq/gui/baseComponents/AbstractMHQJavaFXDialog.java` — reusable
  Swing/JavaFX interop base class.
- `MekHQ/src/mekhq/gui/dialog/AddFundsDialog.java` — the migrated dialog.
- `MekHQ/src/mekhq/gui/FinancesTab.java` — **unchanged** caller (API preserved).
