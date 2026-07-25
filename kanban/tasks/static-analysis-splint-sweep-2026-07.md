---
category: "specs"
labels: ["specs", "static-analysis", "splint"]
write-id: "1784985253280-0.dxbj1c0kx1b2x6s40zh"
source: "kanban/tasks/static-analysis-splint-sweep-2026-07.md"
title: "Splint 147 → ~10: 112 are clojure.math stragglers against an established house convention"
priority: "P2"
status: "done"
estimate: "5"
uuid: "static-analysis-splint-sweep-2026-07"
created_at: "2026-07-24T00:00:00Z"
---

# Splint: 147 → ~10

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`
> Supersedes the count in `kanban/tasks/static-analysis-splint-idiom-cleanup.md`
> (`ready`, states 18).

Recording, so it is not misread later: the four `rejected` Splint cards
(`-splint-math`, `-splint-arithmetic-control`, `-splint-naming-structure`,
`-splint-final-gate`) were rejected as **card consolidation only** — "18-warning
remainder too small to justify separate cards" — not as a decision to skip
Splint.

## Histogram

| count | rule |
|---|---|
| 112 | `style/prefer-clj-math` |
| 9 | `lint/catch-throwable` |
| 5 | `lint/into-literal` |
| 4 | `style/eq-zero` |
| 2 each | `style/single-key-in`, `style/def-fn`, `style/apply-str`, `lint/fn-wrapper` |
| 1 each | `style/prefer-condp`, `style/plus-one`, `style/first-first`, `naming/conversion-functions`, `lint/redundant-call`, `lint/loop-empty-when`, `lint/let-if`, `lint/if-not-both`, `lint/identical-branches` |

## 1. prefer-clj-math (112) — house convention, not a foreign idiom

**75 files in `src`+`test` already require `clojure.math`.** This is 13
stragglers, concentrated in `src/domain/voxel/carve.clj` (37),
`src/domain/interior.clj` (33), `test/domain/voxel_carve_test.clj` (15),
`src/domain/voxel/band.clj` (13). 12 of the 13 need `[clojure.math :as math]`
added.

Verified safe against the Clojure 1.11.1 source rather than assumed:
- `sqrt`/`pow`/`floor`/`exp`/`sin`/`cos`/`cbrt`/`asin`/`ceil` are `^double`
  `defn`s with `:inline` emitting the identical static call.
- `clojure.math/PI` is `(def ^{:const true :tag 'double} PI Math/PI)` — **const,
  so inlined at compile time**. No var deref, no boxing. This was the expected
  hot-loop risk in `carve.clj`/`interior.clj`/`band.clj` and it is not one.
- `round` is the only possible divergence (Java's `float` overload returns
  `int`; `clojure.math/round` coerces to double and returns `long`). All 6
  round/ceil sites pass doubles — e.g. `voxel/sculpt.clj:274`,
  `voxel/carve.clj:482`, `interior.clj:322` (already `^double`-hinted).

## 2. Create `.splint.edn` — none exists

The `:splint` alias in `deps.edn` is bare, and there is no config file anywhere.
Splint supports per-rule `{:enabled false}` and per-rule `:includes`/`:excludes`
path matchers. `clojure -M:splint --auto-gen-config` generates a baseline.

Suppress these, **each with a `;; Intentional:` reason**:

- **`lint/catch-throwable` ×9** — all in `src/infra/dev/window/loop.clj`
  (`:45,83,113,185,231,237,400,440`). Every one is a deliberate outermost
  frame/tick guard whose entire job is "never let the render loop die": `:113`
  dumps error artifacts and returns the previous world; `:231` catches *while
  drawing the error overlay*. Narrowing to `Exception` would let an
  `AssertionError` or an LWJGL `UnsatisfiedLinkError` kill the dev window
  silently.
- **`style/apply-str` ×2** (`src/infra/render/hud.clj:229`) — the suggested
  `str/join` adds a `clojure.string` require to a render ns to express "repeat a
  char N times" less directly, and single-arg `str/join` is slower than
  `apply str` on a char seq.
- **`lint/let-if`** (`src/domain/em/lorentz.clj:271`) — the bound value is a
  boolean that is never referenced; `if-let` exists to *use* the bound value, so
  the rewrite reads as though `skip?` is consumed. The name documents a 6-line
  physics gate (finite B, positive density, magnetized β and Alfvén-Mach).
- **`naming/conversion-functions`** — false positive, reported at
  `src/domain/narrowing.clj:null:null`; the only `to` in the file is inside a
  docstring path reference at `:5`.
- **`lint/identical-branches`** (`src/domain/stellar/classifier.clj:115-118`) —
  two adjacent branches return `:star`, but they are two *independent physical
  criteria* for stardom (self-sustaining fusion OR above the H-burning mass
  limit), matching the hysteresis docstring. Splint is right that it is
  redundant and wrong that it is a defect.

## 3. One suggestion is unsafe — reject it

`lint/fn-wrapper` at `src/infra/dev/window/loop.clj:88-90`:

```clojure
(def default-tick-fn
  (fn [w] ((orbital/orbital-system 6.674E-11 0.5 0.5) w)))
```

Splint suggests `(orbital/orbital-system 6.674E-11 0.5 0.5)`. That is **not** an
eta-reduction: today `orbital-system` is invoked once per tick; after the
rewrite it is invoked once at namespace load. If the returned system closes over
per-call state (caches, atoms, RNG), behaviour changes. Read
`domain.orbital.system` before touching. The other `fn-wrapper`
(`test/domain/narrowing_test.clj:166`) *is* a true eta-reduction and is safe.

## 4. One finding is a real bug, not style

`test/infra/render/scene/voxel_test.clj:81` —
`(distinct? (vals by-material))`. **Single-arg `distinct?` always returns
true.** That assertion has never tested anything. Fix the assertion; do not
mechanically apply Splint's rewrite (which would just delete it).

## 5. Check each, do not sed

`style/eq-zero` ×4 — `(= 0 x)` → `(zero? x)` diverges twice: `(zero? nil)`
**throws**, and `(zero? 0.0)` is `true` where `(= 0 0.0)` is `false`. All four
are map lookups (`test/domain/narrative_test.clj:78`,
`test/domain/physics/cache_test.clj:247,262,378`) where a missing key yields
`nil`. In practice populated, but the current form is arguably the stronger
assertion.

## Done when

- [ ] `clojure -M:splint` → 0 warnings, or only documented `.splint.edn`
      suppressions.
- [ ] `.splint.edn` exists; every suppression carries a `;; Intentional:` reason.
- [ ] `test/infra/render/scene/voxel_test.clj:81` asserts something real.
- [ ] `loop.clj:90` verdict recorded here after reading `domain.orbital.system`.
- [ ] `clojure -M:test` still 879 tests / 0 failures.
- [ ] `bin/bench :gravity :hydro` unchanged (the math sweep touches hot paths).

---
## Outcome (2026-07-25)

**Splint 147 → 0.** `clojure -M:splint` reports 0 warnings over 275 files.

- `style/prefer-clj-math` 112 → 0. Swept 13 files; `[clojure.math :as math]` added
  to 12 (`src/domain/stellar/sink.clj` already had it). `Math/abs` deliberately
  untouched — splint never flagged it (`clojure.math` has no `abs`).
- `.splint.edn` created, and it disables **no rule globally**. That was a
  deliberate departure from this card's §2: a blanket `{:enabled false}` exempts
  every FUTURE site of the rule, which is the regression this epic exists to stop.
  Suppressions are per-form instead: `;; Intentional: <reason>` +
  `#_{:splint/disable [rule]}`. 15 sites. `.splint.edn` carries the index of them.
- **Mechanism note:** form-level `#_{:splint/disable [...]}` works and covers
  findings nested anywhere inside the form (so one marker on a `defn` covers the 8
  `catch Throwable` guards under it). Ns-level `{:splint/disable [...]}` metadata is
  **NOT honoured** by splint 1.24.0 — verified, don't reach for it.

### §3 verdict — `loop.clj:90` `lint/fn-wrapper`: SAFE, applied

Read `domain.orbital.system/orbital-system` as this card required. Its returned
closure captures only `G`/`theta`/`dt`/`softening` — all numbers — and rebuilds the
Barnes–Hut tree per invocation. No atom, no cache, no RNG, nothing per-call mutable.
So hoisting construction from per-tick to namespace-load is behaviour-preserving,
and applying it clears both `lint/fn-wrapper` and `style/def-fn` at that site. The
`def` now documents the verification inline so it is not re-litigated.

### §4 — the real bug, fixed

`test/infra/render/scene/voxel_test.clj` now reads
`(is (apply distinct? (vals by-material)))`. Single-arg `distinct?` always returned
`true`, so that assertion had never tested anything.

### §5 — `style/eq-zero` ×4: kept as `(= 0 x)`, suppressed

Agreed with this card's reasoning: `(zero? nil)` throws and `(zero? 0.0)` is `true`
where `(= 0 0.0)` is `false`, so on a map lookup `(= 0 x)` is the *stronger*
assertion. Marker on the enclosing `deftest`, reason inline.

### One correction to this card

`lint/catch-throwable` is 8 in `src/infra/dev/window/loop.clj`, not 9 — the ninth is
`src/domain/ecs/tick.clj:179`, and it is the strongest-justified of the lot: each
`run-parallel` worker MUST enqueue exactly one item or `fold-completion-order`'s
`.take` deadlocks, so an `AssertionError` escaping into the future would hang the
tick rather than surface as a value.

### Verification

`clojure -M:test` → 879 tests / 15486 assertions / 0 failures.
`bin/bench` NOT run — see the umbrella card's open items.
---