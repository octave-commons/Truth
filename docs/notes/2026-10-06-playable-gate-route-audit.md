# Playable Gate route audit: natural life to embodied action

(己, p=1.00) Read-only source and canonical Rheos audit, 2026-10-06. This
supplements [the dependency plan](truth-playable-gate-dependency-plan.md), owned
by research card `a2b46a04-d541-4714-aed9-bf07d9a9c923` (REVIEW, 3), under
`embodied-character-voxel-mode`. Recommendations below are proposed scopes,
not newly created or implementation-ready cards. No world state, production
code, or board state changed during this audit. GPL-3.0-or-later.

## Verified frontier and the next playable result

(世, p=1.00) The [two natural runs](2026-10-06-two-seed-natural-formation.md)
now establish more than the earlier dependency note: seeds 42 and 43 each kept
24 planets through 12,000 ticks; all 48 exact births were bound at
0.130426–24.325336 AU. At least 20 and 19 bodies respectively stayed bound at
every recorded post-birth sample. Current final eligibility was **0 / 1**,
distinct from **6 / 4** stored candidate records. Natural life events were
prebiotic → prokaryotic: seed 42 body 1012 at tick 11,280; seed 43 bodies
1012/1024 at tick 11,328. Both arcs ended at `:arc/life-emergence`. These are
sampled headless natural runs, not native flight, complex civilization, or Gate
evidence.

(世, p=0.99) The next visible payoff already has implementation and acceptance
owners: fly the actual Spark near a naturally formed world; sustain attention;
cross commitment; resolve the voxel band; use **T / Shift+T / Y** to uplift,
heat/melt through volcanism, or erode it. The previous
[manual run](2026-10-06-manual-flight-precision-boundary.md) demonstrated coarse
travel but not overlap. The [Cruise/Fine verification](https://github.com/octave-commons/Truth/blob/6944df4ec266ec25059c89181fe3d07522d076b3/.%CE%B7%CE%BC/diagnostics/flight-precision/native-24cbffd/README.md)
proves real settings controls and preservation of the existing intent path; it
does not yet prove pursuit of a moving target or this full sequence.

## What the normal controls actually do

| Boundary | Current implementation and exact acceptance owner |
| --- | --- |
| Manual attention follows flight | [`focus-follow`](../../src/domain/player/focus.clj) is queued from the [render loop](../../src/infra/dev/window/loop.clj#L327): focus = Spark position + persistent arrow offset. `focus-follows-pilot` is REVIEW, 3; its acceptance explicitly requires manual fly → bind → commit → voxel **without debug/follow mode**. |
| Binding and commitment | [`domain.narrowing`](../../src/domain/narrowing.clj#L187) admits positioned stored candidate components. Focus intensity must be ≥0.5 and focus/body separation ≤1 AU; binding grows 0.02 per physics tick, so threshold 0.85 takes 43 uninterrupted accrual ticks plus snapshot lag. `ready-to-commit?` uses the arc and stored candidate, not fresh production eligibility. `narrowing-binding-mechanic` and `narrowing-commitment-horizon` are DONE. |
| Visible progress | [`binding-readout-entry`](../../src/infra/render/hud.clj#L244) shows binding or “world committed”. `narrowing-hud-binding-commitment-readout` is REVIEW. It does not supply target range/bearing or fresh eligibility. |
| Terrain resolution | [`voxel-focus`](../../src/domain/voxel/focus.clj) seeds the committed world's field from its actual candidate record, then owns field/band/queue/diffs. [`scene.voxel`](../../src/infra/render/scene/voxel.clj) renders that band's real cubes. `voxel-band-render-path` is REVIEW, 5; its acceptance is a visibly material-tagged patch after commitment. |
| Paid sculpting | [`action-palette`](../../src/infra/render/input.clj#L25) already dispatches T = uplift, Shift+T = volcanism, Y = erosion. [`request-op`](../../src/domain/voxel/sculpt.clj#L479) requires commitment, an armed planetary ability, and Resonance, then queues an operation whose anchor comes from focus relative to the world. `voxel-sculpt-verb-palette-wiring` is REVIEW, 2. |

(己, p=0.99) Two concrete limits must stay separate. First, the existing
[pilot seam tests](../../test/domain/pilot_resolve_seam_test.clj) put the Spark
at requested positions and inject a committed fixture for sculpting; they do
not prove controlled pursuit through production physics. Second, the native
focus callback [invokes `player-key` outside the GLFW_PRESS guard](../../src/infra/render/input.clj#L186),
so arrows and comma/period also fire on release/repeat. The prior native single
period press changed intensity 1.0 → 0.25, crossing below the binding floor.
Repairing one-press semantics fits the existing manual-override acceptance of
`focus-follows-pilot`; it should not be hidden inside unrelated flight tuning.

(世, p=0.99) The available inspection shortcut is real but has a different
meaning: clicking an existing entity sets `:follow-selection`, and
[`L`](../../src/infra/render/input.clj#L76) follows the nearest already-living
world. Non-manual mode [copies the camera target into attention](../../src/infra/dev/window/loop.clj#L156),
without moving the Spark. Thus it can help inspect existing bind/voxel/sculpt
code through ordinary UI. The accepted [flight design §5.2](../designs/spark-flight-and-camera.md#L200)
labels these views debug/cinematic, so this cannot close the explicit manual
flight acceptance. It also cannot create life or a fresh eligible candidate.
The superseded Commit-menu description is not authority to add a bypass button:
[The First Narrowing §3](../designs/the-first-narrowing-star-to-planet.md#L94)
makes commitment a sustained-attention horizon.

## Reopen the data-only time lock, then define its consumer precisely

(世, p=1.00) Existing UUID `narrowing-commitment-horizon` is DONE, 5, and its
acceptance says the local time lock engages. Its completion comment explicitly
records only a data hook. [`time-lock-record`](../../src/domain/narrowing.clj#L290)
also says cadence actuation is later work. A whole-source search found no
consumer of `c/time-lock` beyond its schema, registry, component, and writer.
Canonical title searches for time/Time, lock, cadence, and scheduler found no
separate implementation card. Record this gap on the existing UUID and reopen
the unmet acceptance through Rheos; do not create another “completed lock”.

(世, p=0.99) [Commitment & Resonance §5](../designs/commitment-and-resonance.md#L240)
already supplies the product policy: immediate neighborhood (including the
committed world and moons) gets 1 simulation second per wall second, optionally
slower; regional/global work uses the same ECS at lower update frequency. It
does **not** supply a complete executable clock contract:

- [`domain.pacing`](../../src/domain/pacing.clj#L31) assumes 60 ticks/s and has
  `pacing-dt-min = 1e7` seconds. The real [simulation loop](../../src/infra/dev/window/loop.clj#L120)
  targets 60 Hz but cannot guarantee it under load. `dt=1/60` would establish
  nominal rate, not measured 1 s/s when ticks run slower.
- [`advance-simulation-clock`](../../src/domain/genesis/tick.clj#L178) is the
  serial owner of next-step pacing and applies time-slip afterward. A lock must
  define precedence over adaptive pacing, disabled adaptive pacing, and slip;
  it must also account for the ordinary next-snapshot activation delay.
- [`lod-scheduler`](../../src/domain/lod.clj#L47) solely owns LOD cadence.
  The optional [due-entity filter](../../src/domain/integrator/base.clj#L75)
  skips bodies, while both integrator paths receive the same single global
  `dt`; there is no accumulated elapsed interval for skipped bodies. Merely
  enabling this option is not the specified coarser-time scheduling. The
  design's “10 s/s, 100 s/s” table must be reconciled with its next paragraph's
  “once every 100 simulated seconds”: update interval and independent time rate
  are different policies.
- [`player-thrust`](../../src/domain/player/flight.clj#L92) uses `max 1.0 dt`.
  Current Cruise/Fine controls were verified within cosmological `dt` values,
  not a sub-second locked neighborhood. A clock change needs a production
  pipeline input test and a phase-appropriate displacement contract, without
  resetting stored velocity or adding a second kinematic writer.
- The [master phase design](../designs/gates-of-truth-world-gen-phases.md#L113)
  still allows accelerated history in Phase 4 and a final synchronized lock at
  Gate discovery. The planetary local lock and later historical progression
  need a stated relationship. It is unsafe to infer either eternal global
  real-time or freely divergent clocks from these texts.

(己, p=0.97) Smallest lawful split: a **3-point design/derivation slice under
the existing lock UUID** first, reusing these source measurements and existing
multi-timescale research. Decide clock input, nominal versus elapsed-wall rate,
first locked tick, slip precedence, skipped-body elapsed time, and the meaning
of local versus historical time. Then propose separate **≤5-point consumer
slices** for (1) committed-clock policy through the current pacing owner, (2)
LOD elapsed-time/committed-neighborhood scheduling through its existing writer,
and (3) only the sub-second player-input correction proven necessary by tests.
Do not promise the complete lock until the composed pipeline satisfies it.
No external research is needed to prove the present data-hook defect; any
new asynchronous integration scheme would need primary numerical grounding.

## The actual Gate horizon is still missing producers

(世, p=0.99) Current [`ecology-system`](../../src/domain/ecology/system.clj)
adopts a scalar ecology from a physical planet and evolves it every 24 ticks.
[`check-phase-transition`](../../src/domain/ecology/state.clj#L42) ends at
`:complex`; [`detect-arc`](../../src/domain/arc.clj#L35) ends its forward ladder
at life emergence. Seed/Heat/Cool/Spark/Grow/Evolve helpers exist in
[`ecology.abilities`](../../src/domain/ecology/abilities.clj), but source searches
found only their definitions and public aliases, no native caller. Current
native H/J place physical heat interventions; they do not call the ecology
helpers. Do not claim the old browser hotbar design is already implemented.

(世, p=0.99) No society/avatar/Gate producer system was found in `src/domain`
or `src/law`. `c/civilization` is only a declared keyword. Gate-discovery names
appear in narrative, promotion, reward, and notification consumers, with no
domain transition that emits a Gate, constructs one, activates one, or supplies
an embodied character. A synthetic event would trigger text/rewards, not the
game the user requested.

(己, p=0.99) The intended shortcut is **early temporary control of an actual
individual once civilization is sophisticated enough**, explicitly allowed by
[master Phase 5](../designs/gates-of-truth-world-gen-phases.md#L131). It does not
permit fixture avatars or skipping the production of social actors/history.
The original [Gate vision](designs/2026.06.25.16.41.16-001-the-core-vision-truth-as-a-physics-first.md#L144)
describes discovery conditions, reachable nodes constrained by energy, and
destination resolution, but these remain design sketches. The existing
[research survey](../designs/simulation-methods-research.md#L201) names cultural
transmission/agent models and treats Gate travel as fiction. It is a starting
bibliography, not an implementation-level law for Phases 3–6.

## Prioritized board and implementation sequence

(己, p=0.98) Board facts below are canonical Rheos reads in the precision
checkout on 2026-10-06; other worktrees' unintegrated progress is not inferred.
The ready/TODO view contains 24 cards, but none is a ready Gate, avatar, society,
or ecology-input implementation. These are the smallest useful next dispatches:

1. **Finish the existing manual acceptance.** Keep native verification
   `f369c598-279c-498d-a64f-45d2ce16ad34` (IN_PROGRESS, 3) as evidence owner.
   Record fresh production eligibility while approaching; stored candidacy is
   insufficient evidence of a currently habitable target. Reopen
   `focus-follows-pilot` (REVIEW, 3) for the bounded press/release defect, with a
   red callback test proving one physical press gives one 0.1-AU nudge or one
   focus adjustment. Then repeat actual manual approach with the accepted
   precision controls; retain honest failure boundaries rather than teleport.
2. **Make relative approach controllable only from a defined law.** Existing
   `flight-assist-damping-and-toggle` is TODO, 5; its body says local-frame stop
   but its formula damps absolute `v`, with no reference-selection contract.
   It also depends on the unsplit 8-point force-channel work. First amend/split
   that existing scope to define the reference and observable relative motion;
   retain gravity and the single velocity writer, and conserve momentum with
   assist off. Actual relative-motion trajectory tests must precede code. The
   present report does not authorize target capture or an autopilot. Quiet
   guidance belongs under existing `flight-hud-and-cues` (TODO, 3), coordinated
   with the already-authored heading/HUD children, not another overlay system.
3. **Prove commit → resolved terrain → paid sculpt in the same natural world.**
   Reuse `narrowing-commitment-horizon` (DONE, 5),
   `voxel-band-render-path` (REVIEW, 5), and
   `voxel-sculpt-verb-palette-wiring` (REVIEW, 2). Reopen only reproduced unmet
   acceptance. Required evidence is one commitment event, armed palette,
   rendered band, real T/Shift+T/Y input, Resonance debit, causal field/edit
   diff, and visible terrain consequence. Apply the lock split above before
   claiming local human-time control.
4. **Activate the missing progression design under the existing plan owner.**
   Reopen/extend `a2b46a04-d541-4714-aed9-bf07d9a9c923` (REVIEW, 3) through Rheos
   to specify the first concrete life → represented actor boundary and its
   evidence. Existing `embodied-character-voxel-mode` is ICEBOX, 55 and says
   not to break down implementation until intervening phase specs exist.
   Produce those specs in ≤3-point research/design increments, then ≤5-point
   implementations: committed-world Biosphere input using existing helpers
   and the ecology writer; ecologically grounded actors and a persisted causal
   social/history model; civilization discovery capability; earned avatar
   selection/temporary embodiment; actual Gate object/activation/consequence.
   Each next slice needs an explicit producer, player action, failure outcome,
   event/provenance, render effect, and red production-boundary test. Research
   social/cultural laws from primary sources before choosing them; a scalar
   `:complex` flag must not instantly become a fabricated civilization.
5. **Use the existing embodied terrain scope when its prerequisite exists.**
   `character-scale-mining-construction` is BLOCKED, 5. Its
   [voxel design §§5/7.5](../designs/planetary-voxel-substrate.md) already owns
   remove/place/reshape, material yields, and support checks through the
   existing edit queue. Earlier voxel children being done does not remove its
   explicit embodiment deferral. Final Gate acceptance is real personal action
   activating a produced Gate and its visible consequence in the same world;
   network synchronization needs its own implemented contract before claiming
   the MMO threshold.

(己, p=0.98) Optional board work should not displace this route:
`rich-entity-inspection-ui-spec` is READY but has no estimate/design-research
chain and its accepted core slice excludes Orbit/Hierarchy/Events; its TODO
follow-up incorrectly assumes that core shipped. `debug-view-state-restore`
is TODO, 3, but depends on the chase-camera rebuild. Neither is a ready shortcut
to manual control or a Gate. Keep the current flight-plan decomposition and
visual work coordinated without making all polish a prerequisite to progression.

(己, p=1.00) Next action: attach this audit to the existing dependency-plan
card and record/reopen the unmet time-lock acceptance through canonical Rheos,
then dispatch its three-point executable-policy amendment.

## Canonical disposition after this audit

(世, p=1.00) Root appended the evidence to both existing owner cards. The
canonical Rheos attempt to move `narrowing-commitment-horizon` from `done` to
`in_progress` returned exit 3: `No transition from 'done' to 'in_progress'`.
That historical terminal state remains unchanged. The bounded policy/repair
work therefore needs a new incoming follow-up referencing the original UUID;
neither manual frontmatter editing nor an alternate transition implementation
is appropriate. Earlier recommendations to reopen describe the uncovered
acceptance gap, not a transition that actually succeeded.
