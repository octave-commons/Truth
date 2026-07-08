---
uuid: "gammie-alpha-beta-coupling"
title: "Derive disk viscous alpha from cooling beta (Gammie steady state)"
status: "todo"
priority: "P2"
labels: ["fix", "phase0", "chemistry"]
created_at: "2026-07-06T16:21:51.000000000Z"
source: "kanban/tasks/gammie-alpha-beta-coupling.md"
category: "fix"
---

# Derive disk viscous alpha from cooling beta

> Milestone M3. Spec: `kanban/tasks/law-planet-formation-namespace.md` §7.

`disk-viscous-alpha` is a `^:const` (`stellar.clj:1419`) used directly in `disk-viscous-timescale` (`stellar.clj:1468`). The cooling ratio β is already computed (`cooling-time-ratio`, `stellar.clj:1514`) but never used to set α. The self-regulating gravito-turbulent link is absent.

**Fix:** compute `α(β) = 1/((9/4)·γ·(γ−1)·β)` (Gammie 2001 steady state), with a floor (small background α for stable large-β disks) and a ceiling (avoid divergence near fragmentation). Feed it into `disk-viscous-timescale` and the runaway-gas viscous supply (core-accretion spec §5).

**Done when:** `alpha-tracks-cooling-beta` passes (smaller β → larger α → shorter viscous timescale); the stable-disk limit reproduces the previous constant-α behavior within the floor.
