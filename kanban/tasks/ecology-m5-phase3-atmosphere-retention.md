---
category: "specs"
labels: ["phase0", "chemistry", "handoff", "epic-ecology-water-gate-snowline"]
write-id: "1784747748034-0.kg7paq8twxm30i0zqj"
source: "kanban/tasks/ecology-m5-phase3-atmosphere-retention.md"
title: "M5 Handoff Phase 3: atmosphere retention"
priority: "P2"
status: "done"
estimate: "3"
uuid: "ecology-m5-phase3-atmosphere-retention"
created_at: "2026-07-10T00:00:00Z"
---

# M5 Handoff Phase 3: atmosphere retention

> Parent spec: `kanban/tasks/ecology-water-gate-snowline.md` (§4, §6 Phase 3)
> Parent kanban: `kanban/tasks/ecology-water-gate-snowline.md`

First-pass atmosphere class from escape velocity vs thermal velocity, plus the
set of retained species.

**Scope:**
- Add `domain.stellar/atmosphere-class` (pure): `v_esc = sqrt(2 G M / R)` vs
  `v_thermal = sqrt(2 k_B T / μ)`, bucketed `:none | :thin | :substantial |
  :thick` per parent §4.
- Estimate `:retained-species` (H/He gated at ratio > 6; H2O/CO2/N2 at > 3).
- Write `:component/atmosphere-class` and `:component/retained-species` as a
  fan-out emitter (single writer).
- Schemas in `law/`.

**Done when (plus global DoD):**
- Tests: `earth-like-retains-n2`, `moon-like-loses-atmosphere`,
  `gas-giant-retains-h2`.
- Single-writer preserved; `architecture-test` green.

---
Triage 2026-07-10: scoped 3pt, clear retention calculation. Ready for implementation.

Research grounding 2026-07-22 (Claude, deep-research agent): note at docs/research/atmosphere/planetary-atmosphere-retention-classifier.md (+ toy model). Implement Phase 3 against it. Classifier: atmosphere-class{:mass :radius :temperature :material-class :thermal-band} -> {:atmosphere-class :none|:thin|:substantial|:thick :retained-species #{:H2 :He :H2O :N2 :CO2}}. Uses v_esc=sqrt(2GM/R), rms v_th=sqrt(3kT/m), ratio r=v_esc/v_th. Composition gate FIRST (gaseous->H2/He; rocky/icy/mixed->N2/CO2, +H2O only if thermal-band temperate/warm/hot). Buckets: :none r<3, :thin 3-6, :substantial 6-10, :thick r>=10. Retention: H2/He need r>6 (lambda>36); H2O/N2/CO2 need r>3 (lambda>9) — asymmetry grounded in Volkov 2011 / Fossati-Kubyshkina / early XUV exposure. Cosmic shoreline (Zahnle&Catling 2017, I_XUV~v_esc^4) is an OPTIONAL diagnostic only (Phase 0 lacks tracked XUV history). Sanity table: Earth/Mars/Jupiter/Titan/Pluto/hot-super-earth all sensible.

TWO FLAGS TO RESOLVE DURING IMPL: (1) the card's literal 'moon-like-loses-atmosphere' test does NOT return :none under grounded Jeans physics (real Moon -> :thin; its airlessness is volatile-poor formation + solar-wind sputtering, not Jeans). Swap that test to a genuinely small/hot fragment (~5e20 kg, 300 km, 600 K) which classifies :none cleanly. (2) Unit-convention mismatch: existing domain.chemistry/can-retain-gas? uses rms v_th + uniform threshold 6; the parent spec uses most-probable-speed + 3/6 — ~22% disagreement. Reconcile to ONE convention (recommend rms, matching shipped code) during Phase 3.

Triage 2026-07-22 (Claude): Phases 1-2 done + committed (c1b88c5). Dispatching Sonnet impl agent grounded in the research note, resolving both flags. ready -> in_progress.

Implementation complete + independently verified 2026-07-22 (Claude). stellar-classification-test 9/18 green; architecture-test 6/23 green; full suite 655/13488 (was 652/13482) 0 failures; write-conflicts {}. Landed: pure atmosphere-class (Jeans ratio r=v_esc/v_th, composition-gated species, :none/:thin/:substantial/:thick) per the research note; new law/atmosphere.clj with constants + SHARED escape-velocity/thermal-velocity-rms/retention-ratio helpers. FLAG 2 resolved: reconciled domain.chemistry/can-retain-gas? and the classifier to one RMS convention via the shared helper (no external callers, behavior-preserving). FLAG 1 resolved: replaced moon-like-loses-atmosphere with hot-fragment-loses-atmosphere (5e20kg/300km/600K -> :none) citing note §6.1 — CARD DEVIATION: the literal moon-like test name is intentionally gone. BONUS: fixed a latent boxed divide-by-zero (ArithmeticException, not ##Inf) when T_eff=0 for unignited protostars -> retention-ratio now returns +Inf. Committed a73b483. Sanity: Earth->:thick{N2,CO2,H2O}, Jupiter->:thick{H2,He}, fragment->:none. in_progress -> done.
---