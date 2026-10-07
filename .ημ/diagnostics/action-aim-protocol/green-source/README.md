# Contextual sculpt GREEN candidate — offline source checkpoint

The observed RED is committed as `f0c4710a8306aa634af9f49859c11451515ed912`.
This is a source-ready candidate, **not a passing GREEN runtime result**.
The law and both tests retain their exact RED bytes; all 18 RED manifest hashes
still verify. Of the 304 pinned source/test/dev/dependency files, only the two
admitted host adapters differ.

The existing IntentAtom implementation is byte-identical. ContextualIntent adds
nominal dispatch on that same queue, with named payload/context validators inside
the existing Throwable/non-map guard. A single mode/offset projection is read
from each iteration's existing config snapshot. The after-drain manual attention
preparation is byte-identical. Window input explicitly supplies the new capability;
legacy callables and absent-capability callbacks retain their original routes.

Manual sculpt consumption checks observer presence, then offset, then position;
only then does existing focus-follow prepare attention before unchanged domain
request-op. Tracking delegates directly. A valid domain denial retains prepared
attention; an exception retains the exact prior serial world through the guard.
No physical writer, queue, world model, domain sculpt rule or renderer was added.

## Static evidence and limitations

`kondo-final.json` records zero errors/warnings across all five source/test files.
`reader-final.json` records source parsing only. `format-check-final-02.json`
records passing cljfmt checks of the two touched adapters through Babashka and
the installed cljfmt 0.16.4/rewrite-clj 1.2.50 jars; no project namespace ran.
`git diff --check` also passes.

Initial checks are retained: the nominal record's `update` field shadowed the
unused clojure.core/update refer, resolved by excluding that refer without
changing :update representation or suppressing a lint rule; the first style
check found indentation fixed only in the touched files. A later check-command
parenthesis error is retained as failed harness evidence; corrected final-02
passed without another source edit. None is a functional test outcome.

No JVM, native operation, full suite, strict gate, host cost measurement or push
was executed during this GREEN source turn. Those checks and independent source
review remain pending under root's resource coordination. In particular, the
13 previously absent-API test bodies have not yet executed on this implementation.
Do not infer restored gameplay or measured host cost from static qualification.

`SOURCE-FREEZE.json` binds the exact source hashes, RED provenance and preserved
boundaries. `source.patch` is the complete two-adapter diff against RED.
