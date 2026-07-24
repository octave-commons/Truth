---
uuid: "mote-of-light-shader"
title: "Mote-of-light render: bespoke core + halo + heading flare (replaces particle sprite)"
status: "todo"
priority: "P2"
labels: ["infra", "render", "spark", "spark-flight"]
created_at: "2026-07-23T00:00:00Z"
source: "kanban/tasks/mote-of-light-shader.md"
category: "specs"
estimate: 5
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
