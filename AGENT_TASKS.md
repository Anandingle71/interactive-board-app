# Agent task brief (Claude Code / Antigravity)

This file is the working spec for the AI coding agent. The scaffold compiles
conceptually but **you are expected to finish it against the current AndroidX
APIs** — resolve any version/signature mismatches by building, that's normal
Android work, not a failure of the scaffold.

## Ground rules (do not violate)
1. **Keep the front-buffer architecture.** The wet stroke must render through
   `CanvasFrontBufferedRenderer` (front buffer). Never move ink drawing into a
   `View.onDraw()` + `invalidate()` loop.
2. **Scope is frozen.** This build is ONLY: pen, eraser, colours, widths,
   undo/redo/clear, full-screen canvas. Do not add pages, shapes, save, zoom, or
   AI. Those are later phases.
3. **Target the lowest-spec panel** as the benchmark device.

## Definition of done
- [ ] Project builds and installs on an Android 10+ panel via `installDebug`.
- [ ] Pen draws smoothly with finger and stylus; colour + width selectors work.
- [ ] Eraser removes whole strokes you touch.
- [ ] Undo / Redo / Clear behave correctly.
- [ ] Slow-mo latency test recorded; result noted in this file (below).

## Known things to verify / likely fixes
1. **Dependency versions.** Confirm latest stable:
   - `androidx.graphics:graphics-core` (scaffold uses `1.0.1`)
   - `androidx.input:input-motionprediction` (scaffold uses `1.0.0-beta05` — bump if a newer one exists)
   If `MotionEventPredictor.newInstance(view)` or `record/predict` signatures
   differ, adapt to the installed version's API.
2. **`commit()` repaint on erase/undo/clear.** `presentDry()` calls `renderer.commit()`
   with an empty front buffer. If the dry layer does NOT visually refresh after an
   erase/undo/clear on your device, render a no-op transparent point first:
   ```kotlin
   renderer?.renderFrontBufferedLayer(WetSegment(-1f, -1f, Color.TRANSPARENT, 0f, true))
   renderer?.commit()
   ```
   or use the renderer's clear API if the installed version exposes one.
3. **Initial white screen.** First frame before any commit may show black. If so,
   call `presentDry()` once after `dryBitmap` is created in `onSizeChanged` (already
   wired) — confirm it fires; if not, trigger a commit in `onAttachedToWindow` after
   the surface is ready.
4. **Buffer transform / pre-rotation.** `onDrawFrontBufferedLayer` receives a
   `transform` array. Fixed-landscape panels are usually identity so we ignore it.
   If ink is rotated/offset, apply the transform to the canvas before drawing.
5. **Palm rejection.** Not implemented (spike). If the panel registers palm touches,
   gate `ACTION_DOWN` on `getToolType(0)` and ignore large `getTouchMajor()` blobs.

## Tuning if latency is too high
- Increase predicted points (draw 2 predicted samples instead of 1).
- Make sure `setZOrderOnTop(false)` and that the SurfaceView is hardware-accelerated.
- Reduce per-event allocation in the hot path (`WetSegment` is a data class — if GC
  shows in profiling, switch to primitive args / a pooled object).
- Profile with **Android Studio Profiler** + **GPU rendering bars** (adb shell
  `dumpsys gfxinfo`), and check `Choreographer` skipped-frame logs.

## Latency test result (fill in after testing)
- Panel model / chipset / Android version:
- Touch sample rate (Hz):
- Frames of lag (240fps):  ___ frames  ≈  ___ ms
- Verdict: ☐ proceed to Phase 1   ☐ needs tuning

## After this passes — Phase 1 (do NOT start until the test passes)
Pen suite refinement (pressure-variable width), pixel eraser, basic shapes,
select/move/resize, multi-page, save/load, PNG/PDF export. Single-user, offline.
