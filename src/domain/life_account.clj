(ns domain.life-account
  "Pure carbon-account API: explicit RED placeholders, not an installed system.

   No origin arithmetic, transition, ECS write or persistence is implemented in
   this RED checkpoint. See kanban/tasks/life-account-origin-kernel.md.")

(defn origin-budget
  "Return the exact integer origin partition and binary64 input witnesses.

   RED placeholder: reports the missing numerical contract without pretending
   a missing namespace or a fabricated origin is an implementation."
  [_material]
  {:accepted? false :reason ::not-implemented})

(defn apply-operation
  "Propose an account transition with immutable outcome and retention data.

   RED placeholder: it performs no arithmetic or state changes. The history is
   caller-supplied; this API never creates a store, an entity or an event."
  [account _history _request]
  {:disposition :rejected :reason ::not-implemented :account account
   :outcome nil :retain nil :effects []})
