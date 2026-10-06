# Canonical Rheos comment formatting repair

Truth PR18 source at operation start: `e0a4e175d3f6c41e8ee1899220bd7ba1046bc30b`.
Affected native finding: <https://github.com/octave-commons/Truth/pull/18#discussion_r4200839294>, thread `PRRT_kwDOTDahac6prGcs`.

Root authorized one canonical comment operation on `body-trails-ringbuffer`.
The command used Node 22.20.0 and the existing upstream artifact:

```text
/home/err/spaces/review-repair/rheos-comment-rendering/dist/cli.cjs
SHA256 83c6b397278d418d69ce6509b8c3d9fe87e88cca9141b28143d9efb5d78a75a7
```

The artifact is the already merged [Rheos PR2](https://github.com/open-hax/rheos/pull/2)
repair. Merge `ef3c4abf1ea75199486f693e9470df3fec88dd49` and retained
head `ab6b227becd8585742b5130df9ae624d8744928e` have identical Git tree
`e62be10e7a39b8266021511ca4bd83578d595e2f`. No upstream source/build/global
package change was made. Its FSM source matches the previously consumed
EDN-capable engine, and its existing config tests cover the Promethean overlay,
custom command list and config-relative cwd. No transition or Truth build gate
was run for this formatting-only operation.

`card-before.md` and `ledger-before.edn` preserve the exact original bytes.
`before-provenance.json` records hashes and lengths, including the pre-append
receipt and reflection prefixes. `canonical-before.json`, `canonical-after.json`
and `canonical-latest-event.json` are direct Rheos CLI outputs. The only board
mutation was `comment body-trails-ringbuffer --text <comment-text.txt content>`
with `--config openhax.kanban.edn`; it exited 0 once. The saved text is not a
second event or a replay instruction.

Verification compares those canonical JSON reads; it does not parse Markdown
frontmatter or implement board semantics. Every prior section/content and
frontmatter field is preserved, except the expected appended comment and
engine-generated write ID. UUID, source path and `in_progress` status match.
The entire **595,690-byte** ledger prefix is identical; precisely one **902-byte**
line was appended. The canonical event has type `kanban.comment`, the expected
text/task and write ID `1791325421315-0.i4cgynxic5pzxnnm86u`.
`ledger-appended.edn` is an exact evidence copy of that one suffix, not a new
operational ledger. The operational ledger remains `kanban/tasks/.events/ledger.edn`.

The upstream installed `marked` lexer confirms the previous final planning
paragraph was a level-two heading, and is now a paragraph. The new formatter
receipt also renders as a paragraph. `rendered-before.html` and
`rendered-after.html` preserve full actual renderer output; `verification.json`
records the token observations, canonical event and hashes. Raw card formatting
changed at delimiter boundaries; parsed card content was preserved.

All stderr files are empty. `git diff --check` passed. No new tests or source
implementation were added, and historical upstream tests/builds were not
rerun or represented as fresh results. Receipt River and session-mycology each
received an append; their previous prefixes remain intact. No spore was created.
Root retains commit/push/review-settlement ownership; no completion or gameplay
acceptance is implied.

`CLOSED-FILES.txt` is the complete closed diagnostic inventory. `SHA256SUMS`
checks every member except itself, relative to this directory. Source card,
operational ledger, `.ημ/receipts.edn` and `.ημ/session-mycology/ledger.md` are
separate changed files for the owning checkpoint.
