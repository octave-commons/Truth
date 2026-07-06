---
uuid: "disk-regime-tag-unification"
title: "Unify divergent disk-regime tag vocabularies"
status: "todo"
priority: "P1"
labels: ["fix", "phase0", "chemistry"]
created_at: "2026-07-06T16:21:51.000000000Z"
source: "docs/specs/protoplanetary-disk-planet-formation-realspec.md"
category: "fix"
---

# Unify divergent disk-regime tag vocabularies

> Milestone M3. Spec: `docs/specs/protoplanetary-disk-planet-formation-realspec.md` §10.

Two sibling functions emit **different** regime tag sets:
- `disk-regime-map` (`stellar.clj:1568`) → `:core-accretion-zone` / `:fragmenting` / `:gravito-turbulent`.
- `disc-regime` (`stellar.clj:1544`) → `:stable-disc` / `:gravitationally-unstable` / `:unstable-no-fragment`.

Neither `field.clj:70` `regime-tags` nor `field.clj:75` `disc-regime-tags` contains the tags the live classifier actually produces, and `:streaming-zone` (research sketch) is never emitted anywhere.

**Fix:** pick one closed tag vocabulary, make both functions (or one merged function) emit it, and add it to the `law/field` schema so the schema's closed set matches reality.

**Done when:** one regime vocabulary exists; the Malli schema's closed set covers every tag the live classifier can emit; a test round-trips a classified disk against the schema.
