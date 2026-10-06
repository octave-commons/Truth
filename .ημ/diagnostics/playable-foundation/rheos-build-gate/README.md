# Canonical Rheos Clojure gate capability evidence

(己, p=1.00) Observed 2026-10-06 using the existing canonical upstream
`/home/err/spaces/eta-mu/packages/rheos/dist/cli.cjs`; no upstream edits or global
package upgrade. CLI SHA256:
`c16255ab69e158d34536d9c8c72ad1234f3b0abc41e0408a1a270c167dca141d`.
The surrounding source checkout was `5dfa8e97099cc473584e451e3847fedcf7b0c7e6`;
the existing artifact's exact build revision was not established, so the hash
identifies the executable tested. The package reports `@eta-mu/rheos` `0.1.0`.
Node runtime: `v24.14.1`.

(己, p=1.00) The isolated fixture was
`/tmp/truth-rheos-runtime-PfcYM2`. Canonical `create` made its task, followed by
lawful incoming → accepted → breakdown → ready → todo → in_progress moves.
The preserved `pass.edn` and `fail.edn` configure upstream
`:extends :promethean`, `:build-gate-commands`, and config-relative `:cwd`.
Its `gate/gate-marker` contained `isolated runtime capability verification`.

(己, p=1.00) `refusal.log` is the actual failed move output, exit **3**. The
before/after SHA256 manifests are byte-identical, proving no card or ledger
write. The later `touch must-not-run` command did not create a file.
`refusal-state.json` is the canonical `read-task` result, still in_progress.
`admission.log` is the subsequent passing move output, exit **0**, to review.
These smoke commands test the upstream command mechanism, not Truth's suite.

(己, p=1.00) The existing compiled upstream suite, invoked as
`node dist/test.cjs` from `/home/err/spaces/eta-mu/packages/rheos`, completed
**138 tests / 476 assertions, zero failures and errors**. It warned that
`source-map-support` was unavailable. An earlier invocation from the isolated
fixture failed because the suite reads package-relative `docs/cli.md`; it was
not counted as a pass. Neither invocation changed the upstream checkout's dirt.

(己, p=1.00) Truth board projection comparison after creating/claiming the
hygiene card and before any further comments:

- Legacy JSON explicit configuration: SHA256
  `b88e967f455f6039d4fac522f0c80e1e13dc57a61993608a3f468b074b7b8ccb`.
- New EDN explicit configuration: the same SHA256.
- New EDN automatic discovery: the same SHA256.

The canonical `read-board` outputs were compared byte-for-byte with `cmp`;
there was no custom board parser or validator. Truth's actual review transition
must still execute `clojure -M:test` and `bin/analyze --strict` against the source
revision being reviewed. This evidence does not substitute for those gates.
