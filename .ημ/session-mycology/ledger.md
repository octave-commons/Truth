- ts: 2026-07-03T16:18:44.348065946Z
  session: ses_0d75f1e2cffeRur7XfdLIgCDQ9
  task: Fix Clojure game HUD format crash and add frame-level error recovery
  p-efficiency: 0.5
  p-friction: 0.5
  p-skill-candidate: 0.0
  spore: 20260703-frame-error-recovery
  receipt-refs: frame-error-handling
  note: Root cause was a %d format specifier receiving Double from math/floor. Large function edit introduced paren mismatch requiring structural repair. Pattern: when user reports 'game stops moving', check logs for uncaught exceptions in render loop and add try/catch + visible error state.
