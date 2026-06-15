# Phase 1 backlog — the shippable single-user board

**Precondition:** the Phase 0 latency test has PASSED on the lowest-spec panel.
Do not start these until then.

Goal of Phase 1: a board a teacher can actually use for a full class — write,
organise across pages, and not lose their work. Still **offline, single-user, no AI**.

Each item is sized to be one focused agent session. Keep the front-buffer ink
architecture untouched; everything here builds *around* it.

---

## Epic 1 — Pen feel & ink quality
- [ ] **P1-1 Pressure-variable width.** Render the wet/dry stroke as variable-width
      (per-segment width from pressure) instead of constant. Stylus first.
      *Done when:* a stylus press visibly thickens the line; finger stays uniform.
- [ ] **P1-2 Pen types.** Marker (semi-transparent, flat), highlighter (wide, multiply
      blend, behind ink), pencil (textured/low-opacity). Share one Stroke model + a
      `brush` enum.
- [ ] **P1-3 Palm rejection.** Ignore large-area / non-primary touches while a stylus
      is down (`getToolType`, `getTouchMajor`). *Done when:* resting a hand while
      writing with the stylus doesn't draw stray marks.

## Epic 2 — Eraser suite
- [ ] **P2-1 Pixel eraser.** Erase within a radius (not whole strokes). Needs the dry
      layer to support partial removal — easiest via destination-out paint on the dry
      bitmap; reconcile with the stroke model (split or flag strokes).
- [ ] **P2-2 Select & erase / lasso clear.** Drag a region, delete everything inside.
- [ ] **P2-3 Eraser size UI.** S/M/L eraser, with a live cursor ring.

## Epic 3 — Select & edit
- [ ] **P3-1 Selection model.** Tap/lasso to select strokes; show a bounding box.
- [ ] **P3-2 Move / resize.** Drag to move, handles to scale (transform the points).
- [ ] **P3-3 Duplicate / delete / color-restyle** on the current selection.

## Epic 4 — Shapes
- [ ] **P4-1 Basic shapes.** Line, rectangle, ellipse, arrow as first-class objects
      (not freehand) with the same select/move/resize.
- [ ] **P4-2 Shape recognition (optional).** Snap a rough freehand circle/rect to a
      clean shape on pause. Defer if time-constrained.

## Epic 5 — Pages
- [ ] **P5-1 Page model.** A document = ordered list of pages; each page owns its
      stroke list + dry bitmap. Switch the active page.
- [ ] **P5-2 Page controls.** Add, delete, duplicate, reorder, rename. Thumbnail strip.
- [ ] **P5-3 Page nav gestures.** Next/prev page; page indicator.

## Epic 6 — Persistence & export
- [ ] **P6-1 Local save/load.** Serialize the document (strokes as ids+points+style)
      to internal storage. Auto-save on background. Model stays CRDT-friendly
      (stable ids, no positional-only references).
- [ ] **P6-2 Open / new / recent.** Simple file list on launch.
- [ ] **P6-3 Export.** Current page → PNG; document → multi-page PDF
      (`PdfDocument`). Share via `Intent`.

## Epic 7 — Canvas basics (bridge to Phase 2)
- [ ] **P7-1 Pan & zoom.** A view transform (matrix) applied to both layers. Keep ink
      latency: draw wet stroke in screen space, store points in world space.
- [ ] **P7-2 Fit-to-content / reset view.**
      *(Full infinite canvas + mini-map is Phase 2 — only the transform plumbing here.)*

---

## Suggested order
P5 (pages) and P6 (save) first — losing work is the #1 classroom dealbreaker —
then P1/P2 (ink + eraser polish), then P3/P4 (select + shapes), then P7 (transform).

## Definition of "Phase 1 shippable"
A teacher can: open the app, write with a few pen styles, erase cleanly, organise
several pages, and have it all still there tomorrow — exported to PDF if they want.
That is the first build worth putting in front of real users.
