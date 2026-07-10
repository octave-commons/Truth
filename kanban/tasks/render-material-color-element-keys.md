---
uuid: "render-material-color-element-keys"
title: "Render material color reads retired composition keys (live bug)"
status: "done"
priority: "P1"
labels: ["fix", "phase0", "render", "chemistry"]
created_at: "2026-07-06T16:21:51.000000000Z"
source: "kanban/tasks/render-material-color-element-keys.md"
category: "fix"
---

# Render material color reads retired composition keys

> Milestone M2. Spec: `kanban/tasks/render-material-color-element-keys.md` §6.5.

`infra.render/composition->material-color` (`render.clj:816`) reads `:metals :ice :H2O :volatiles` — none of which exist in the element-resolved composition map (the `:metals` lump was retired, `law/composition.clj:9`). Result: `metals=0`, `ice=0`, `gas=1.0` always → **every body renders gas-tan regardless of actual composition.**

**Fix:** read `domain.chemistry/bulk-categories` (`:rock :metal :ice :gas`, derived from `c/comp-condensed`) and map those to color. Depends on M1 (metals must exist) to show any variation.

**Done when:** a live run renders rocky/icy/metallic/gaseous bodies distinguishably; a test asserts a high-Fe body maps to the metal color and a high-H₂O body to the ice color.

---
Triage 2026-07-10 (todo→in_review): DONE-IN-CODE per code check — render/color.clj composition->material-color uses bulk-categories {gas rock metal ice}; retired-key lookups gone; the live bug is fixed. Staged for close pending sign-off.

2026-07-10 → DONE (triage batch-close): implemented + test-covered in code; owner approved close on triage evidence.
---
