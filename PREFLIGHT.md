# Pre-hardware preflight checklist

Run this **before** carrying the panel out for the slow-mo latency test. The point
is to burn down compile errors and obvious bugs on an emulator/desk first, so the
hardware session is spent measuring latency, not chasing crashes.

Hand this list to the coding agent: "work top to bottom, fix as you go."

## A. It compiles & installs
- [ ] `./gradlew assembleDebug` succeeds (or Android Studio build is green).
- [ ] Resolve any AndroidX signature mismatches (see AGENT_TASKS.md §Known things).
- [ ] `./gradlew installDebug` puts the app on an emulator (API 30+) without crash.
- [ ] App launches to a **white** canvas with the toolbar visible at top-center.
      (If black: the `commit()`/initial-paint note in AGENT_TASKS.md §3.)

## B. Pen smoke test (emulator mouse is fine here)
- [ ] Dragging draws a continuous line that follows the cursor.
- [ ] Line is **smooth**, not a chain of visible straight segments.
- [ ] Releasing and drawing again keeps the previous stroke on screen.
- [ ] Each new stroke does NOT flicker/erase the existing drawing on commit.

## C. Tools
- [ ] Black / Red / Blue / Green each change the next stroke's colour.
- [ ] S / M / L change stroke thickness visibly.
- [ ] Eraser mode: tapping/dragging over a stroke removes that whole stroke.
- [ ] Eraser does not remove strokes you didn't touch.

## D. History
- [ ] Undo removes the last drawn stroke; repeated Undo walks back correctly.
- [ ] Redo re-adds them in order.
- [ ] Undo after an erase brings the erased stroke(s) back.
- [ ] Clear empties the board; Undo after Clear restores it.

## E. Robustness (quick)
- [ ] Rotate / resize (or fold the nav bar): existing drawing is preserved
      (handled in `onSizeChanged`, confirm it actually redraws).
- [ ] Background the app and return: no crash, drawing still there.
- [ ] Rapid scribble for ~20s: no crash, no unbounded memory growth
      (watch Android Studio memory profiler — `WetSegment` churn should be flat-ish).

## F. Code sanity (agent self-review)
- [ ] No drawing work happens on `ACTION_MOVE` other than front-buffer renders
      (no full-scene redraw per event).
- [ ] No allocations in the hot path beyond the unavoidable `WetSegment`
      (no `Path`/`Bitmap`/list allocation inside `onDrawFrontBufferedLayer`).
- [ ] `renderer.release()` is called in `onDetachedFromWindow` (no leak).
- [ ] Pressure is read and stored (even if width is constant for now).

## When A–F pass
Go to the panel and run the slow-mo test in `README.md`. Record the result in
`AGENT_TASKS.md → Latency test result`. Only then start `PHASE1_BACKLOG.md`.
