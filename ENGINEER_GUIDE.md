# Engineer Guide — Board Annotate (Interactive Board App)

> Onboarding + working reference for any engineer (human or AI coding agent) picking
> up this project. Read this first. It explains **what** we're building, **why** it's
> built this specific way, **how** to run it, and **where** to make changes.

---

## 1. What this is, in one paragraph

This is the foundation of an **interactive classroom whiteboard** for Android
**interactive flat panels (IFPs)** — the big touchscreen "smart boards" in classrooms.
The full product vision is large (AI lesson prep, infinite canvas, geometry tools,
collaboration). **This repo is deliberately NOT that yet.** It is **Phase 0**: the
smallest possible app — write, erase, undo — whose only job is to prove one thing on
real hardware: **does the ink keep up with the teacher's hand, with no lag?** If we
can't make writing feel instant, nothing else matters. So we de-risk that first.

## 2. The prime directive: latency

Classroom panels have weak GPUs and add their own touch + display latency. A naively
built whiteboard feels like the ink is "dragging" 60–100ms behind your finger, and
teachers reject it instantly. **Our entire architecture exists to beat that.** The
target is **≤ ~33 ms** (about 8 frames at 240fps) from finger movement to ink on glass.

**The one rule you must not break:** the live stroke is drawn to the **front buffer**
(see §6). Do **not** "simplify" the ink rendering into a normal custom `View` with
`onDraw()` + `invalidate()`. That is the laggy path and the reason this project exists.

## 3. Current status

- **Phase 0 scaffold is complete and uploaded.** It compiles conceptually and is
  structured correctly. The coding agent is expected to finish it against the exact
  current AndroidX API versions (resolve any signature mismatches by building — that's
  normal Android work).
- **It has NOT yet been latency-tested on a panel.** That test (see §9) is the gate
  that decides whether we proceed to Phase 1.
- Repo: **https://github.com/Anandingle71/interactive-board-app** (public).

## 4. Repository map

| File | What it's for |
|---|---|
| `ENGINEER_GUIDE.md` | **This file** — start here. |
| `README.md` | Quick build/run + the latency test, in brief. |
| `AGENT_TASKS.md` | The brief for the AI coding agent: guardrails + the 5 most likely API fixes needed to make it build/run. |
| `PREFLIGHT.md` | Checklist to run on emulator/desk **before** the hardware test (compile → smoke tests → code sanity). |
| `PHASE1_BACKLOG.md` | The "move further" plan — 7 epics of ticket-sized tasks, gated behind a passing latency test. |
| `app/src/main/java/.../ink/InkSurfaceView.kt` | **The engine.** Two-layer low-latency renderer. The heart of the project. |
| `app/src/main/java/.../ink/Stroke.kt` | The stroke data model + eraser hit-testing. |
| `app/src/main/java/.../MainActivity.kt` | Screen + toolbar wiring. |
| `app/src/main/res/layout/activity_main.xml` | The floating toolbar UI. |
| `app/build.gradle.kts` | Dependencies (incl. the two latency-critical AndroidX libs) + SDK levels. |

## 5. Prerequisites & environment setup

1. **Android Studio** (Hedgehog 2023.1+ or newer). Install via the official installer.
2. **JDK 17** — Android Studio bundles a compatible JDK; no separate install needed
   unless you build purely from the CLI.
3. **Android SDK** — installed through Android Studio's SDK Manager. We target:
   - `compileSdk = 34`, `targetSdk = 34`
   - `minSdk = 29` (Android 10 — the front-buffer rendering path requires it).
4. **A test panel** running **Android 10+** (or an emulator with API 30+ for first
   smoke testing — but real latency can ONLY be judged on the panel).
5. **adb** (Android Debug Bridge) — ships with the SDK platform-tools.

## 6. Architecture deep-dive (read this before changing the engine)

### The two-layer model

We split the canvas into two layers so we never have to redraw the whole board while
the teacher is writing:

```
                 finger / stylus moves
                          │
                          ▼
        ┌─────────────────────────────────┐
        │  WET LAYER  (front buffer)        │  ← drawn EVERY motion event
        │  the single stroke being drawn    │     cheap: only the newest segment
        │  now. Bypasses the compositor →   │     + a predicted point ahead
        │  lowest possible latency.         │
        └─────────────────────────────────┘
                          │  on ACTION_UP -> commit()
                          ▼
        ┌─────────────────────────────────┐
        │  DRY LAYER  (cached Bitmap)       │  ← redrawn ONLY on
        │  every stroke already finished.   │     commit / erase / undo / clear
        │  Blitted in one drawBitmap call.  │     never per motion event
        └─────────────────────────────────┘
```

- **Wet layer** = `CanvasFrontBufferedRenderer` (from `androidx.graphics:graphics-core`).
  The front buffer is the memory the display scans out directly, so writing to it skips
  a compositor frame. The front buffer is **not cleared between renders within a stroke**,
  so on each motion event we only draw the *newest line segment* — extremely cheap.
- **Dry layer** = a `Bitmap` we own. When a stroke finishes, we bake it into this bitmap
  and call `commit()`, which presents the bitmap and clears the front buffer for the
  next stroke. Erase/undo/clear rebuild this bitmap from the stroke list, then commit.

### Data flow (one stroke)

1. `ACTION_DOWN` → create a `Stroke`, draw its first point to the front buffer.
2. `ACTION_MOVE` → for each point (including batched **historical** samples for
   smoothness), append to the `Stroke` and draw the new segment to the front buffer;
   also draw **one predicted point** (via `MotionEventPredictor`) so ink leads the
   finger by ~1 frame. The predicted point is drawn but NOT stored.
3. `ACTION_UP` → add the finished `Stroke` to the scene, bake it into the dry bitmap,
   `commit()`.

### Why strokes are id'd objects (`Stroke.kt`)

Each stroke is a standalone object: `id + points + style`. This makes three things trivial:
- **Element erase** = "remove the stroke whose path you touched" (`Stroke.hitTest`).
- **Undo/redo** = add/remove whole strokes (see the `Action` sealed class).
- **Future collaboration** = stable ids are exactly what a CRDT needs to sync. We get
  this for free without building a CRDT now.

### Smoothing

Live segments use round-capped lines (continuous-looking). The dry (committed) version
is redrawn as a quadratic-smoothed `Path` through point midpoints — cheap and smooth.
Pressure is captured on every point but width is currently constant (Phase 1 makes it
pressure-variable — see backlog P1-1).

## 7. Build & run

### In Android Studio (recommended)
1. `File → Open` → select the `board-app/` folder. Let Gradle sync (it downloads
   Gradle 8.9 and generates the wrapper automatically).
2. Plug in the panel (or start an emulator). Click **Run ▶**.

### From the command line
```bash
# one-time, if the wrapper jar isn't present:
gradle wrapper

./gradlew assembleDebug      # build the APK
./gradlew installDebug       # build + install on the connected device
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

### Sideloading onto a panel
```bash
adb devices                         # confirm the panel is listed
adb install -r app-debug.apk        # install / reinstall
# Network adb (no USB): on the panel enable wireless debugging, then:
adb connect <panel-ip>:5555
```

## 8. How to make common changes (pointers)

- **Add a pen color** → add a swatch `View` in `activity_main.xml` and a click handler
  in `MainActivity.onCreate` calling `pick(color)`.
- **Add a stroke width** → add a button + `ink.toolWidth = <px>` handler.
- **Tune smoothing** → `InkSurfaceView.drawStrokeSmoothed()` (dry layer) and the
  segment drawing in `onDrawFrontBufferedLayer` (wet layer).
- **Tune prediction** → in `ACTION_MOVE`, where `predictor.predict()` is used; draw 2
  predicted points instead of 1 if latency still feels high.
- **Change eraser size/feel** → `eraseAt()`'s `radius`.
- **Add a new tool (Phase 1)** → follow `PHASE1_BACKLOG.md`; keep the front-buffer path
  intact and add tool logic around it, not inside the render callbacks.

## 9. The hardware latency test (the gate)

This is the actual deliverable of Phase 0.
1. Install on the **lowest-spec panel** you intend to ship on.
2. Film your hand + the screen with a phone in **slow motion (240fps)**.
3. Draw fast circles/zig-zags. Count frames between the fingertip and the ink tip.
   - **≤ ~8 frames (~33 ms): PASS — proceed to Phase 1.**
   - Visibly trailing: tune (see §8 and `AGENT_TASKS.md → Tuning`) before writing more.
4. Record the result in `AGENT_TASKS.md → Latency test result`.

## 10. Known gotchas / verify-on-first-build

See `AGENT_TASKS.md` for the full list with code fixes. The big ones:
1. Confirm the latest stable versions of `androidx.graphics:graphics-core` and
   `androidx.input:input-motionprediction`; adapt if `MotionEventPredictor` signatures differ.
2. If the dry layer doesn't refresh after erase/undo/clear, the fix (render a no-op
   transparent point before `commit()`) is in AGENT_TASKS §2.
3. First frame may be black until the first commit — confirm `presentDry()` fires.
4. If ink is rotated/offset, apply the `transform` array in `onDrawFrontBufferedLayer`.
5. Palm rejection is NOT implemented (Phase 1).

## 11. Conventions & do-not rules

- **DO** keep all live-ink drawing on the front-buffer path.
- **DO** keep `ACTION_MOVE` cheap — no full-scene redraws, no per-event `Bitmap`/`Path`
  allocation inside the render callbacks.
- **DON'T** replace the renderer with `View.onDraw()` + `invalidate()`.
- **DON'T** start Phase 1 features until the latency test passes.
- **DON'T** add the AI / cloud features here — they are a later phase, server-side.

## 12. Roadmap

`PHASE1_BACKLOG.md` has the detail. Order after the latency gate passes:
**Pages + Save first** (losing work is the #1 classroom dealbreaker) → pen/eraser
polish → select + shapes → pan/zoom plumbing. Later phases: infinite canvas + geometry
(P2), AI Hub via cloud (P3), real-time collaboration + CRDT (P4).

## 13. Glossary

- **IFP** — Interactive Flat Panel; the classroom touchscreen we target.
- **Front buffer** — the framebuffer the display scans out directly; writing to it
  skips a compositor frame, lowering latency.
- **Wet / dry layer** — in-progress stroke vs. already-committed strokes.
- **Historical samples** — extra input points batched inside one `MotionEvent`
  (`getHistoricalX/Y`); using them keeps fast strokes smooth.
- **Motion prediction** — estimating the next input point to draw ahead of the finger.
- **CRDT** — Conflict-free Replicated Data Type; the data structure that will power
  real-time multi-user editing later.

## 14. Command cheat sheet

```bash
git clone https://github.com/Anandingle71/interactive-board-app.git
cd interactive-board-app
./gradlew assembleDebug          # build
./gradlew installDebug           # build + install on connected device
adb devices                      # list devices/panels
adb logcat | grep -i annotate    # app logs
```

## 15. Git workflow

- `main` is the integration branch.
- Work on a feature branch per backlog ticket: `git switch -c p5-1-page-model`.
- Open a PR into `main`; keep PRs scoped to one ticket where possible.
