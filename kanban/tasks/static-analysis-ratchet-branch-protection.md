---
category: "specs"
labels: ["specs", "static-analysis", "ci", "process"]
write-id: "1784985264946-0.f9hmrb7ks7pt2tobg04"
source: "kanban/tasks/static-analysis-ratchet-branch-protection.md"
title: "The ratchet: CI has been red since 2026-07-11 and PRs merged anyway — that is the root cause"
priority: "P1"
status: "done"
estimate: "3"
uuid: "static-analysis-ratchet-branch-protection"
created_at: "2026-07-24T00:00:00Z"
---

# Make the gate real

> Parent: `kanban/tasks/static-analysis-regression-2026-07-24.md`

Every other card in this epic fixes findings. **This one fixes why the findings
came back.**

`gh run list --workflow=static-analysis.yml` shows **every** run failing since
2026-07-11 — eight consecutive failures across `main` pushes and PRs, including
PR #1 (`cae2668`, merged 2026-07-24). The workflow is correct
(`.github/workflows/static-analysis.yml:39` runs `./bin/analyze --strict`, and
`:43` runs the architecture invariants). It is simply not required to pass.

A gate that reports and does not block is a gate that trains everyone to ignore
it. Two weeks of red is how a broken momentum-conservation check
(`src/law/field/schema.clj:195` on main) sat unfixed while twelve cards said
`done`.

## 1. Promote advisory tools to blocking

`bin/analyze:55` documents the mechanism: "Advisory tools are intentionally
non-gating while the tree converges; flip one to blocking by adding its name to
`FAIL` below."

Owner decision (2026-07-24): promote **cljfmt** and **clj-kondo warnings**.

- clj-kondo currently only blocks on exit code 3 (errors). Make exit code 2
  (warnings) blocking too — `bin/analyze:50-52`.
- cljfmt currently prints "files need formatting" and exits 0 —
  `bin/analyze:88-93`. Make the check failure append to `FAIL`.
- Splint, jscpd and dead-code stay advisory **for now**; their cards
  (`-splint-sweep`, `-jscpd-*`, `-lsp-config-dead-vars`) each end with their own
  documented-suppression state, and promoting them is a follow-up once those
  land clean.

Ordering: this must land **after** the waves that make those tools clean, or CI
goes red on the cleanup PR itself.

## 2. Branch protection — the actual fix

Require the `static-analysis` check on `main` before merge.

**This is an outward-facing repo config change. Draft the `gh api` call and get
explicit owner approval before running it** — do not fire it as a side effect of
this card.

Roughly:

```bash
gh api -X PUT repos/:owner/:repo/branches/main/protection \
  -f 'required_status_checks[strict]=true' \
  -f 'required_status_checks[contexts][]=analyze' \
  ...
```

The exact context name must be read off a real run — the job id is `analyze`
(`static-analysis.yml:12`) and the workflow name is `static-analysis`; GitHub's
check context is the **job** name. Verify against
`gh api repos/:owner/:repo/commits/main/check-runs` rather than guessing.

## 3. Documentation

- `docs/STATIC-ANALYSIS.md:24-30` — the table marks Splint/lsp/jscpd/cljfmt
  "advisory" and says "Advisory tools are printed but don't fail the build while
  the tree converges." Update for the new contract.
- Document the two suppression conventions in one place:
  `;; Intentional:` (Splint/kondo suppressions, each with a reason) and
  `UNUSED-PENDING <card>` (see
  `static-analysis-unused-pending-convention.md`).
- `CLAUDE.md` / `AGENTS.md` — add the norm, plainly:

  > A red `bin/analyze --strict` is a blocker, not a backlog item. If you cannot
  > fix a finding, file a regression card and link it. Never leave a card marked
  > `done` whose finding has returned — the next agent reads `done` and believes
  > it.

## 4. Verify by making it refuse something

**The gate is not verified until it has blocked a merge.** Push a branch with a
deliberate breach (a 90-line function, or a shadowed var) and confirm:

- `static-analysis` reports failure, and
- the PR merge button is actually disabled.

Then delete the branch. A protection rule that was configured but never
exercised is indistinguishable from one that was configured wrong.

## Done when

- [ ] cljfmt and clj-kondo warnings are in `FAIL` in `bin/analyze`.
- [ ] `bin/analyze --strict` exits 0 on this branch.
- [ ] Branch protection requires the static-analysis check, **approved by the
      owner before it was applied**.
- [ ] A deliberately-failing branch was pushed and the merge was refused;
      the run URL is recorded in this card.
- [ ] `docs/STATIC-ANALYSIS.md`, `CLAUDE.md` and `AGENTS.md` carry the new
      contract and the two suppression conventions.

---
## Outcome (2026-07-25)

### `bin/analyze` — all six tools now gate

Promoted, each having been driven to exactly zero first (a gate turned on while
findings remain is a gate that gets merged past — which is this epic's whole subject):

| tool | was | now |
|---|---|---|
| clj-kondo **warnings** | advisory | **blocking** (errors already blocked) |
| Splint | advisory | **blocking** |
| clojure-lsp unused-public-vars | advisory | **blocking** |
| cljfmt | advisory | **blocking** |
| jscpd | advisory, and `threshold`-less so it could never fail | **blocking** above `.jscpd.json`'s `threshold` |
| structural HARD | blocking (`--strict`) | unchanged |

The exit-gating comment block in `bin/analyze` now states the rule directly: a red
gate is a blocker, not a backlog item; if you cannot fix it, file a regression card;
never demote a tier, and never leave a card `done` whose finding has returned.

### The gate has refused something — four times, deliberately

This card's standard was "not verified until it has refused something". Each newly
blocking class was probed with an injected finding and `bin/analyze --strict`
confirmed to exit 1 and name it:

| injected | verdict |
|---|---|
| a shadowed-var **warning** (not error) | `clj-kondo: warnings present (tree was at 0)` |
| `(+ 1 n)` → `style/plus-one` | `Splint: warnings present (tree was at 0)` |
| a misindented `defn` | `cljfmt: files need formatting (tree was clean)` |
| an unreferenced public var | `clojure-lsp: unused public vars present (tree was at 0)` |

The last was not injected — it was a real speculative helper (`observer-eid`) left in
the new `test/support/worlds.clj`, and the gate caught it on its first run. All probes
reverted; clean tree exits 0.

### Docs

`docs/STATIC-ANALYSIS.md` — the gating contract, the regression history, and a
verified table of which suppression mechanisms actually work.
`CLAUDE.md` / `AGENTS.md` — the blocker rule and the two suppression markers.

### Branch protection — NOT applied, awaiting owner approval

The root cause is unfixed until the `static-analysis` check is *required* to merge:
PR #1 merged with it red. The `gh api` call is drafted and surfaced for approval, not
fired, because it is an outward-facing repo config change. See the umbrella card.
---

## The `gh api` call — DRAFTED, AWAITING YOUR APPROVAL (not run)

Verified state as of 2026-07-25:

- `gh api repos/octave-commons/Truth/branches/main/protection` → **404 "Branch not
  protected"**. `main` has no protection at all, which is why PR #1 could merge with
  `static-analysis` red.
- The required **check name is `analyze`** (the job name in
  `.github/workflows/static-analysis.yml`), not `static-analysis` (the workflow name).
  Requiring the wrong string produces a check that never satisfies and blocks all
  merges — worth getting right.
- Token has `admin: true` on the repo, so this will succeed if run.

```bash
gh api -X PUT repos/octave-commons/Truth/branches/main/protection \
  --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["analyze"]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": null,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```

Choices worth an explicit yes/no from the owner:

- `"strict": true` requires a branch to be up to date with `main` before merging.
  Safer, but means rebasing when `main` moves. Set `false` if that friction is not
  wanted.
- `"enforce_admins": false` leaves an admin override. Given the failure mode this
  card documents is *humans merging past a red check*, `true` is the stronger
  choice — but it also locks the owner out of an emergency merge. Deliberately left
  `false` so this is a conscious decision rather than a default.
- `"required_pull_request_reviews": null` — no review requirement added. This card is
  about the CI check only; adding a review gate is a separate policy call, and on a
  repo cohabited by agents a mandatory human review may be exactly what is wanted, or
  exactly what blocks everything.

### Verification after applying — the gate is not verified until it has refused something

```bash
# 1. confirm protection is live and names the right check
gh api repos/octave-commons/Truth/branches/main/protection \
  --jq '.required_status_checks.contexts'          # => ["analyze"]

# 2. push a branch that FAILS the gate on purpose and confirm the merge is refused
git switch -c ratchet-negative-test
printf '\n(defn ratchet-probe [count] (inc count))\n' >> src/law/plasma.clj
git commit -am 'TEMP: prove the static-analysis gate blocks a merge'
git push -u origin ratchet-negative-test
gh pr create --fill --base main
gh pr checks --watch                                # analyze must go RED
gh pr merge --merge                                 # must be REFUSED
# then
gh pr close --delete-branch
git switch - && git branch -D ratchet-negative-test
```

`bin/analyze --strict` has already been verified locally to refuse each newly-blocking
class (see the Outcome section above). What the steps above verify is the different,
and actually load-bearing, claim: that GitHub will not let a red run be merged.

---

## Correction to §2 and §7, and the root cause is now CLOSED (2026-07-25)

### The PR #1 framing in this card was wrong

This card (and the epic footer) said PR #1 (`cae2668`) "merged red into main". **It did
not merge into `main`.** Verified: PR #1 was `worktree-integration-seam-tests →
spark-gravity-bound-body`, merged 2026-07-24. There has only ever been ONE PR in this
repo and it targeted a feature branch.

The real history, from `gh run list --workflow=static-analysis.yml` (39 runs, the
complete record):

| when | event | branch | result |
|---|---|---|---|
| 2026-07-10 (early) | push | main | 7 × **success** |
| 2026-07-10 (later) → 2026-07-21 | push | main | **33 × failure, consecutive** |
| 2026-07-24 | pull_request | worktree-integration-seam-tests | failure |

So the gate was bypassed by **direct pushes to `main` by an admin**, 33 times — not by
merging PRs past a red check. That is a *stronger* case for protection, and it changes
what protection has to do: required status checks alone would not have stopped any of
those 33, because the pusher was an admin.

### Root cause CLOSED — protection applied and verified by refusal

Applied 2026-07-25 with owner approval:

```
required_status_checks: {strict: true, contexts: ["analyze"]}
enforce_admins:  true      <- the setting that actually binds, given the history above
allow_force_pushes: false
allow_deletions:    false
```

`enforce_admins: true` was a deliberate owner decision, not a default: with it `false`
the protection would have constrained only non-admins and PR merges, i.e. none of the
33 recorded failures. The cost is real — no emergency direct push to `main` — and was
accepted.

The required context is **`analyze`** (the job name), not `static-analysis` (the
workflow name).

**Verified by refusal, not by reading config.** A throwaway branch was cut from
`origin/main` carrying one deliberate `:shadowed-var` warning, pushed, and opened as
PR #2. `analyze` went red; `gh pr merge` was refused:

> `X Pull request octave-commons/Truth#2 is not mergeable: the base branch policy prohibits the merge.`

`mergeStateStatus` was `BLOCKED`. PR closed and branch deleted. (`git push --dry-run`
was tried first and is **useless** for this — it sends nothing, so no pre-receive hook
runs and it reports success against a protected branch.)

### A consequence worth knowing before you next touch `main`

That probe run also confirms **`main` is red today on its own merits** — independently
of the injected warning it reported `clj-kondo: errors present` (the two real
`src/law/field/schema.clj` conservation-check bugs this card documents at §1) and
`structural: HARD threshold breached`.

With protection now on and `enforce_admins: true`, **nothing can be pushed to `main`
until `main` is green** — and the thing that makes it green is merging this branch,
which carries the fixes. That is the intended one-move outcome, but it does mean `main`
is temporarily unpushable. Note also that `coverage` fails on `main` too; it is NOT a
required context, so it does not block.
