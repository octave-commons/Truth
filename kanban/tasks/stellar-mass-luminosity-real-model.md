---
uuid: "stellar-mass-luminosity-real-model"
title: "Real stellar mass–luminosity(–radius) model (replace the toy star-luminosity clamp)"
status: "todo"
priority: "P3"
labels: ["domain", "physics", "stellar", "deferred", "research"]
created_at: "2026-07-24T00:00:00Z"
source: "kanban/tasks/stellar-mass-luminosity-real-model.md"
category: "specs"
estimate: 5
---

# Real stellar mass–luminosity(–radius) model

> Provisional-toy tracking card (owner decision 2026-07-24, ledger A14/A15):
> the current stellar luminosity is a deliberate toy clamp. Toy is acceptable
> **only** as an explicitly provisional stand-in with a research-grounded real
> model carded for later. This is that card. It must NOT be resolved in a way
> that assumes the toy's current thermal landscape.

## Current state (the toy)
`domain.stellar.thermodynamics/star-luminosity` (`thermodynamics.clj:200-216`):
computes a toy fusion power (`fusion-rate · (4/3)π r³`), multiplies by `1e50`,
and **clamps to [1e26, 1e29] W**. Docstring is explicit: "scale from the toy
fusion power to a range that makes nearby debris/planets visibly hot
(~hundreds of K) without boiling the whole nebula."

Consequences (measured, seed 42):
- Star 491 (0.279 M☉) radiates `1.0e29` W = **261 L☉** — the clamp ceiling.
- Luminosity is **mass-independent** at the ceiling: any fusing star whose
  `raw·1e50` exceeds `1e29` pins to 261 L☉, so a 0.28 M☉ and a 2 M☉ star are
  indistinguishable. No mass–luminosity relation exists.
- This is why the first-ever planet-candidate (eid 1010, 9 AU) reads
  `:temperate` (342 K); at a real M-dwarf L≈0.008 L☉ it would be ~30 K frozen.

## The seam is clean (low lock-in — verified 2026-07-24)
`star-luminosity` is the SOLE toy input to `c/luminosity` (via
`domain.stellar.fusion` fan-out, the sole `c/luminosity` writer). Everything
downstream is real physics in SI/Kelvin:
- `classifier/equilibrium-temperature` — grey-body Stefan-Boltzmann.
- `classifier/thermal-band` — physical K thresholds (150/250/350/450 K).
- candidate gate — 150–400 K (`candidate-min/max-equilibrium-temp`).

**Swapping the toy = replacing ONE function.** The gates stay in Kelvin; only
which planets pass changes. **Lock-in guard for current gameplay-loop work:
key gameplay on the SEMANTIC gate (`c/planet-candidate`, thermal-band label),
never on "the temperate planet is at ~9 AU" — that fact is a toy artifact and
will move to the true HZ (~0.1–0.2 AU for an M-dwarf) when this lands.**

## What the real model should do (design, then implement)
- Main-sequence mass–luminosity relation with regime breaks (L ∝ M^~2.3 low
  mass → ^~4 solar → ^~3 high mass), a mass–radius relation, and effective
  temperature from L, R (Stefan-Boltzmann). Candidate analytic fits: Tout et
  al. 1996 (M–L–R polynomials in [Fe/H]); Baraffe et al. 1998/2015 tracks for
  low-mass/M-dwarfs; Pecaut & Mamajek 2013 empirical sequence.
- Pre-main-sequence luminosity for `:protostar` (Hayashi/Henyey tracks) so the
  toy's ignition-time behaviour is preserved physically; tie into
  `domain.stellar.fusion` promotion.
- Metallicity dependence (the sim already carries composition/metallicity).
- Keep a bounded/graceful floor so the nebula is not "boiled" — the toy's
  playability intent, achieved physically, not by a flat clamp.

## Research first (deep-research card companion)
Write `docs/research/stellar/mass-luminosity-radius.md` (arxiv-cited, toy
models + Clojure pseudocode) BEFORE implementing — per the owner's condition
that toy replacements be research-grounded. Use the `deep-research` skill.

## Impact when it lands (cross-links)
- Outer survivors (1.6–9 AU) refreeze → candidate emergence then depends on
  planets surviving in the true HZ (~0.1–0.2 AU), i.e. the **inner planets that
  currently scatter**. This makes the residual-scattering work
  (`planet-orbit-circularization-blocker` §residual, ledger q13 option B)
  critical. Do NOT land this before inner-planet survival, or candidates
  vanish again.

## Done when
- `star-luminosity` (or its successor) returns mass/metallicity/age-appropriate
  L; stars of different mass have visibly different L; a research note grounds
  the fits; full suite green; a test pins L(M) at ≥3 masses against the note.
- The candidate pipeline still yields ≥1 physically-real candidate (gated on
  inner-planet survival landing first).

## Dependencies
Companion to the residual-scattering follow-up. Downstream of nothing; this is
deferred behind the gameplay-loop focus (owner 2026-07-24).
