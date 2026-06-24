(ns domain.ecs.rewindable
  "Rewindable protocol: any simulation component that supports
   forward stepping, backward stepping, snapshotting, and seeking.

   Designed around the reversibility of symplectic integrators:
   - Forward:  apply systems with +dt
   - Backward: apply systems with -dt (same equations, negated timestep)
   - Snapshot: checkpoint current state for O(1) seek anchor
   - Seek:     find nearest snapshot, replay forward (or backward) to target tick

   Discrete events are un-applied in reverse order on rewind.")

(defprotocol Rewindable
  (step-forward  [this]
    "Advance one tick forward. Returns new Rewindable.")
  (step-backward [this]
    "Retreat one tick backward. Returns new Rewindable.")
  (snapshot      [this]
    "Return an opaque snapshot value representing current state.")
  (restore       [this snap]
    "Restore state from a snapshot. Returns new Rewindable at snap's tick.")
  (current-tick  [this]
    "Return the current tick as a long.")
  (seek          [this target-tick]
    "Seek to target-tick. Uses nearest snapshot as anchor, then replays.
     Returns new Rewindable at target-tick."))
