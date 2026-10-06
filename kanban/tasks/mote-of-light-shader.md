---
category: "specs"
labels: ["infra", "render", "spark", "spark-flight"]
write-id: "1791324160871-0.6pox29jw7fylp316ynr"
source: "kanban/tasks/mote-of-light-shader.md"
title: "Mote-of-light render: bespoke core + halo + heading flare (replaces particle sprite)"
priority: "P2"
status: "todo"
estimate: "5"
uuid: "mote-of-light-shader"
created_at: "2026-07-23T00:00:00Z"
---

# Mote-of-light render

> spark-flight epic, Wave 4 card 9 of 10. Design: `docs/designs/spark-flight-and-camera.md` §6.2.
> The spark should read as a mote of light / mini-star with a sense of a vessel —
> bright core, soft halo, and a heading flare that points where the nose faces.
> Distinct from real stars, but narratively still a proto-star.

## Grounded integration (from design investigation, cite file:line)
- Today the spark is a flat screen-space `:particle` point sprite (pale cyan,
  28–72 px, sized by focus intensity) in `player-overlay-shapes`
  (`src/infra/render/scene/hud.clj:29-46`) — no world presence, no orientation, no
  depth. Replace it with a world-space mote.
- **Heading flare uses `c/orientation`** (card 1) — a subtle flare/elongation
  along the body-forward axis so the player can see which way the nose points
  (essential once flight lands). This is why the card depends on orientation.
- Bright core + soft halo; coherence modulates brightness (coherence already
  drives opacity at `scene/bodies.clj:152` — reuse the coupling). Keep it visually
  separable from real stars (which use the emissive/bloom body path).
- Mind the renderer's two coordinate paths: a world-space mote likely wants the
  `:particle`/`:line` raw-position path or a small custom pass, NOT necessarily the
  `:body` model-matrix path (`CLAUDE.md` Coordinates). Shader lives in
  `infra.render`; after edits, `(w/reload-shaders!)` via the nREPL to hot-load
  (`CLAUDE.md` Dev service).

## Done when (player-visible via live pm2 window)
- The mote renders as a glowing core + halo with a visible heading flare that
  tracks the nose as it rotates.
- Brightness responds to coherence (dims toward decoherence).
- It reads as distinct from background stars.
- Offscreen GL→PNG still works headlessly (`clojure -M:run demo` /
  `w/take-screenshot!`).
- `clojure -M:test` + architecture-test + `bin/analyze --strict` green.

## Risks
The `:body` vs `:particle`/`:line` coordinate split (debug markers can't validate
body placement) — verify placement visually. Shader must degrade gracefully in the
headless PNG path. Don't let the halo wash out at true scale.

## Dependencies
Card 1 (orientation for the heading flare). Pairs with card 8 (trails).

---
2026-10-06 planning-only readiness repair at composed source b402939931575e377ae4409d9cd4061dff11df06: docs/notes/2026-10-06-mote-render-readiness.md grounds the narrow amendment to docs/designs/spark-flight-and-camera.md section 6.2. Original card body and TODO5 scope are preserved. Corrected facts: existing particle is already anchored at ECS position, with depth testing but no depth writes before solids; nominal 28–72 pixel size and fixed cyan color do not supply orientation or coherence brightness. The old bodies.clj:152 opacity coupling cite is stale. Card 1 unambiguously means spark-orientation-angular-momentum, currently REVIEW5 with its integrated single-writer substrate; ordinary input still has no attitude torque producer. The shared +Y forward/+X right/+Z up frame is only the pinned PR9 proposal at 9f2d772, pending review and owned there by common-frame work. Open before implementation: review grounding, reconcile that shared-axis contract and ownership, specify bounded graphical extent/coherence response/depth tests, record scope and claim legally through Rheos. Static-pose/native-offscreen tests can establish shader response but cannot establish ordinary-input turning or player-proof acceptance. Existing facing-cue HUD work remains separate. No new feature task, status transition, implementation, source/config change, external review request or native input occurred; no readiness admission is claimed.
---