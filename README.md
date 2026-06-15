# Board Annotate — Phase 0 latency spike

A deliberately tiny Android whiteboard. The **only** goal of this build is to prove
that ink keeps up with the hand on a real interactive flat panel (IFP). Everything
else (pages, shapes, AI, save) comes later and is intentionally **not** here.

## What it does
- Full-screen white canvas
- Pen (finger or stylus), 4 colours, 3 widths
- Element eraser (erases whole strokes you touch)
- Undo / Redo / Clear

## Why it's built this way
The latency-critical idea is a **two-layer** renderer:
- **Wet layer** — the stroke in progress, drawn to the **front buffer** every motion
  event via `androidx.graphics.lowlatency.CanvasFrontBufferedRenderer`. This bypasses
  the normal compositor, which is what removes the "ink trails my finger" lag.
- **Dry layer** — a `Bitmap` of everything already committed, blitted only on
  commit / erase / undo / clear (never per motion event).

Plus **motion prediction** (`androidx.input:input-motionprediction`) to draw ~1 frame
ahead, and **historical sample** consumption for smoothness on fast strokes.

> ⚠️ The one thing not to "refactor": do not replace this with a custom `View` +
> `onDraw()` + `invalidate()`. That is the laggy path this project exists to avoid.

## Build & run
1. Open the `board-app/` folder in **Android Studio** (Hedgehog or newer).
   - It will download Gradle 8.9 and generate the Gradle wrapper automatically.
   - (CLI alternative: run `gradle wrapper` once, then `./gradlew assembleDebug`.)
2. Enable **Developer options → USB debugging** on the panel and connect it
   (USB, or `adb connect <panel-ip>` over the network).
3. Run the app, or sideload the APK:
   ```
   ./gradlew installDebug
   ```
   APK output: `app/build/outputs/apk/debug/app-debug.apk`

Requires **Android 10 (API 29)+** on the panel — the front-buffer path needs it.

## The actual test (this is the deliverable)
1. Run on the **lowest-spec panel** you intend to ship on.
2. Film your hand + the screen with a phone in **slow-motion (240fps)**.
3. Draw fast circles and zig-zags. Count frames between the fingertip and the ink tip.
   - **≤ ~8 frames (~33ms): good — proceed to Phase 1.**
   - Visibly trailing: fix before building anything else (see AGENT_TASKS.md → Tuning).

See **AGENT_TASKS.md** for the exact checklist, known API gotchas, and what to do
if something doesn't compile or render.
