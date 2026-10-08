# Finite Gate implementation source boundary

(己, p=1.0) The selected production source base is
`42cd8daafc73c2e5f3a85033a44eac4ca372d463`. PR12's planning gate passed at
that exact head: CodeRabbit request `6051682033`, completion `6051683279`,
summary `6024761980`; MiMo review `5451150481` and workflow `37723650031`.
Both reviewers covered all 15 changed paths. Independent transcript verification
reconstructed the complete 120,956-byte diff from all 15 delivered/assessed pages,
SHA256 `29db481ae4f992edce2b5d34748843f9e7bd536c0e3ad415a78b8e5bf6b7e9c2`.
This admits the bounded design; new implementation cards and refinements still
require planning review and lawful Rheos In Progress before RED or code.

(己, p=1.0) The original admitted design at that immutable commit has SHA256
`70d4888511c751c2da46e6c535930a496d6579efad1409aa8a830fe935aecac8`.
The current [design](../designs/finite-native-gate-energization.md) links the
proposed implementation cards and refines the input/display estimate to five.
The historical three-point estimate and all original review evidence remain
in Git and the append-only records; they do not approve this new candidate.

## Source comparison

(己, p=1.0) Compared planning `42cd8daa` with existing native source
`5ce5decae9ab756082d7e693834b4904a4c33e42`, at merge base
`6944df4ec266ec25059c89181fe3d07522d076b3`. There are 32 changed
src/dev/test/dependency paths and six adjacent native-test/config/script paths.
The relevant ECS, event ledger, bootstrap, physical flight, map/SoA integration,
picking and camera lifecycle files are shared. Neither head implements the new
local Gate operation or E binding. Using the planning base avoids inheriting
held trails, Kepler fixtures, focus, warp and demo work as implicit prerequisites.

(己, p=1.0) The source-only Foresight audit is
`implementation-base-compatibility-01/REPORT.md`, SHA256
`b62ca0b6ae1d52f10f5720cb660585c4e33fc1644e0166e316a4faff2a79e59b`;
its 61-row evidence is `8d4c067369d1c2b4a2e052d1be13e3f2ed62c2e2dcc53cbde3495846fe15d882`.
The complete scoped patch is `99c709c9e8642b9c5e38e3ae5c38c8ce955c8fae9a17b9ee721b02d9c87c2085`.
These are provenance references to retained root diagnostics, not files presumed
present in a fresh Truth checkout. The concrete source below is available here.

## Required existing-owner changes

(己, p=1.0) [The actual line pass](../../src/infra/render/scene/setup.clj)
uses width `1.5` and the existing particle position/RGB/size format. The launcher
requests a forward-compatible core context. OpenGL 3.3 core specification
Appendix E.2.1 removes wide lines in that context; values greater than one
produce `INVALID_VALUE`. [Khronos OpenGL 3.3 core specification](https://registry.khronos.org/OpenGL/specs/gl/glspec33.core.pdf#page=358).
Include width `1.0` and an actual error/visible-pixel RED/GREEN regression in the
input/projection card. Preserve legacy vertices' `:size`; copying the held
native opacity-path test verbatim would exercise a different format. This note
establishes the source/specification contract, not an observed run of the new test.

(己, p=1.0) [Cursor synchronization](../../src/infra/dev/window/loop.clj)
frees the pointer if camera mode is not manual, a UI domain is active, or
`:ui/cursor-free?` is true. E eligibility must cover desired capture and the
existing applied cursor state; checking only the two UI flags is insufficient.
[Selection](../../src/infra/menu/widgets.clj) currently stores a local EID and
enters follow-selection. Three ordinary C presses return to manual and retain
selection. The input card must retain run/history/device identity from the
actual pick, so an E press cannot silently rebind a recycled EID.

(己, p=1.0) The same render loop overwrites selected `:zoom-min` and removes it
on an unselected frame. The scene card must preserve validated scene defaults
through this owner as well as launch, reset and menu deselection. Following a
device or moving the camera does not change physical approach or range.

## Execution boundary

(己, p=1.0) This is source/design preparation. No production change, native
Gate result or implicit integration of another PR is recorded. New cards stay
Incoming until reviewed. Full repository gates, meaningful changed-path cost
evidence and a separately pinned finite native acceptance profile remain required.
The existing native owner `f369c598-279c-498d-a64f-45d2ce16ad34` must be explicitly
reconciled to the Gate scope before execution. One live run must demonstrate
ordinary physical approach, refusal, acceptance, matching state/event and visible
cold/energized capture with verified cleanup.
