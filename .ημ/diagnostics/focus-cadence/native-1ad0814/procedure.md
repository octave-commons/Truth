# Same-world focus-cadence reload — proposed diagnostic procedure

Prepared read-only on 2026-10-06 for root execution. **None of these live forms
has been executed or tested by this reviewer.** Source target is committed
`1ad081475364cc3b75db76142204cbf54b5bf60c` (GREEN implementation `021ef9d`). Root
must finish its exact-source strict gate before execution. The retained service
is Java 3083624, Xvfb :2, loopback nREPL 7895, expected world atom identity
1675959387. Recheck those facts immediately before execution; the numbers come
from the closed record, not a fresh live query.

## What the existing API can and cannot do

`lifecycle/start!` starts both workers; both share a stop atom. There is no public
simulation-only restart. Namespace reload changes Vars, but the running
`sim-loop` has already entered its old function body. `stop!` joins each worker
for 5 seconds and clears service-state even if a worker remains alive. Its
printed success is insufficient: retain references and assert both dead.

`start!` preserves its supplied world atom, but builds new camera/config/queue
objects, and its config whitelist drops current focus-offset, mode and UI
settings. `window-loop` has no finally cleanup. The earlier recovery script
explicitly left a window/context allocated and dropped some host config. Do
not replay it unchanged.

The bounded intervention below reuses the existing private thread factory and
launcher after normal stop. This is diagnostic lifecycle composition, **not a
new supported restart API**. It retains the same world, camera, config and
IntentAtom/queue, including pending functions in their existing order. It does
not call genesis/create-world, replace/reset the world, force an event, move a
body, change physics, or drain/replay an intent itself. New renderer-local key
and mouse gesture atoms cannot be carried through the current constructor:
require all keys/buttons released and no gesture/action in flight first.

## Execution envelope

Stop other native/offscreen/REPL operations in this JVM for this interval.
Do not touch the separate retained :1/7894 service. Capture command/output,
wall time, source revision/hash and every failed assertion as new evidence.
Do not overwrite the closed native-400ba3a bundle. Each numbered stage is a
separate eval; **any failed assertion stops the sequence, without automatic
retry or relaunch**. Use the actual client contract (one form argument, port
from environment), for example from a checkout with the demo-client alias:

```sh
TRUTH_DEMO_PORT=7895 clojure -M:demo-client '(load-file "/tmp/root-reviewed-stage.clj")'
```

No `--port`/`--file` arguments exist in the current client. A client timeout is
not evidence that a remote eval stopped: inspect the stored stage promise and
worker references before taking another action. Never duplicate a restart eval.

### 1. Save references, without changing service/world state

Release controls through normal native input beforehand. Confirm no independent
GLFW/offscreen work or third-party instrumentation owns this process. Then:

```clojure
(let [s @infra.dev.window.lifecycle/service-state
      old-recovery (some-> (ns-resolve 'user 'truth-native-recovery) deref)
      historical (:old old-recovery)
      observer (some-> (ns-resolve 'user 'truth-native-observer) deref)
      frame-var (ns-resolve 'infra.dev.window.loop 'render-frame-once)]
  (assert s "No retained service")
  (assert (= 1675959387 (System/identityHashCode (:world s))) "Wrong world")
  (assert (and (.isAlive (:thread s)) (.isAlive (:sim-thread s))))
  (assert (nil? (:error s)))
  (assert (nil? (:ui/error-state @(:config s))))
  (when observer
    (assert (identical? @(:target observer) (:original observer))
            "Prior frame observer is still installed"))
  (when historical
    (assert (and (not (.isAlive (:thread historical)))
                 (not (.isAlive (:sim-thread historical))))
            "Historical window still has a live worker")
    (assert (not= (:window historical) (:window s))))
  (assert (nil? (ns-resolve 'user 'truth-cadence-reload)) "Already staged")
  (intern 'user 'truth-cadence-reload
          (atom {:stage :prepared :old s :historical historical
                 ;; Capture before load-file re-evaluates the IntentAtom type.
                 :queue (.-queue ^infra.dev.window.loop.IntentAtom (:world-intents s))
                 :old-sim-loop @#'infra.dev.window.loop/sim-loop
                 :old-drain-intents @(ns-resolve 'infra.dev.window.loop 'drain-intents)
                 :frame-var frame-var :original-frame @frame-var
                 :teardown (promise) :claimed (atom false)}))
  {:staged true :tick (:tick @(:world s)) :window (:window s)
   :world-identity (System/identityHashCode (:world s))})
```

The historical object must still be the recorded leaked window from the earlier
recovery (recorded handle 132405185364976). If its ownership/allocation is no
longer known, stop and reconcile it rather than using an unverified raw handle.

### 2. Request one teardown at a completed render-frame boundary

This is outside a GLFW callback, on the current render/context owner. It checks
neutral keys and requests before stopping. The new local key-state map starts
neutral; gesture-local cursor deltas restart. No held input is synthesized.

```clojure
(let [d @user/truth-cadence-reload
      s (:old d) target (:frame-var d) original (:original-frame d)
      wrapper
      (fn [args]
        (let [result (original args)]
          (if-not (compare-and-set! (:claimed d) false true)
            result
            (try
              (assert (identical? (:world s)
                                  (:world @infra.dev.window.lifecycle/service-state)))
              (assert (= (:window args) (:window s)))
              (assert (not-any? true? (vals @(:keys-atom args))) "Held key")
              (assert (every? nil? (map @(:config s)
                                       [:screenshot-request :action-request :pick-request]))
                      "Outstanding host request")
              ;; Mouse buttons are separate from the key-state atom.
              (doseq [button [org.lwjgl.glfw.GLFW/GLFW_MOUSE_BUTTON_LEFT
                              org.lwjgl.glfw.GLFW/GLFW_MOUSE_BUTTON_RIGHT
                              org.lwjgl.glfw.GLFW/GLFW_MOUSE_BUTTON_MIDDLE]]
                (assert (= org.lwjgl.glfw.GLFW/GLFW_RELEASE
                           (org.lwjgl.glfw.GLFW/glfwGetMouseButton (:window s) button))
                        "Held mouse button"))
              (reset! (:stop s) true)
              (swap! user/truth-cadence-reload assoc
                     :owner-thread (.getName (Thread/currentThread))
                     :neutral-keys @(:keys-atom args)
                     :camera-at-teardown @(:camera s)
                     :config-at-teardown @(:config s))
              ;; The live context is unshared. Destruction retires its GL objects.
              (org.lwjgl.glfw.Callbacks/glfwFreeCallbacks (:window s))
              (org.lwjgl.glfw.GLFW/glfwMakeContextCurrent 0)
              (org.lwjgl.opengl.GL/setCapabilities nil)
              (org.lwjgl.glfw.GLFW/glfwDestroyWindow (:window s))
              (when-let [h (:historical d)]
                (org.lwjgl.glfw.Callbacks/glfwFreeCallbacks (:window h))
                (org.lwjgl.glfw.GLFW/glfwDestroyWindow (:window h)))
              ;; stop! must not receive a freed native window pointer.
              (swap! infra.dev.window.lifecycle/service-state dissoc :window)
              (deliver (:teardown d) {:ok true :wall-ms (System/currentTimeMillis)})
              false
              (catch Throwable t
                (deliver (:teardown d) {:ok false :error (str t)})
                ;; If cleanup has begun, stop rendering; root must investigate.
                (if @(:stop s) false result))
              (finally
                (alter-var-root target
                                #(if (identical? % (:wrapper @user/truth-cadence-reload))
                                   original %)))))))]
  (assert (identical? @target original) "Frame callable changed since staging")
  (swap! user/truth-cadence-reload assoc :wrapper wrapper)
  (alter-var-root target (constantly wrapper))
  {:teardown-requested true})
```

Then inspect the promise with a bounded wait, e.g. `(deref (:teardown
@user/truth-cadence-reload) 10000 :not-finished)`. `:not-finished` is not success.
If the wrapper sees held input, it restores the frame callable and leaves the
service running; do not blindly reinstall it. If any teardown step fails after
stop is set, do not start a second service. Preserve the failure and inspect
which resources were retired before manual recovery.

GLFW documents destroy-window/termination as main-thread-only and forbids
destroying a context current on another thread. Truth already initializes,
creates and polls on its named daemon render thread. This owner-thread cleanup
is a host-specific continuation of that existing Linux/Xvfb lifecycle; it is
**not a portable GLFW lifecycle fix**. We do not call glfwTerminate or claim all
historical process-global allocations are reclaimed. Recoverable window
callbacks/contexts are retired; the existing global GLFW error callback is
outside this window-scoped operation. References:
[window lifecycle](https://www.glfw.org/docs/latest/group__window.html),
[GLFW initialization/termination](https://www.glfw.org/docs/latest/group__init.html).

### 3. Prove stop, save the exact immutable world, reload only the new loop

```clojure
(let [d @user/truth-cadence-reload s (:old d)]
  (assert (true? (:ok (deref (:teardown d) 10000 {:ok false}))) "Teardown incomplete")
  (infra.dev.window.lifecycle/stop!)
  (assert (not (.isAlive (:thread s))) "Old renderer still alive")
  (assert (not (.isAlive (:sim-thread s))) "Old sim still alive; refuse second writer")
  (assert (nil? @infra.dev.window.lifecycle/service-state))
  (swap! user/truth-cadence-reload assoc
         :stopped-world @(:world s) :stopped-camera @(:camera s)
         :stopped-config @(:config s) :pending-intents (.size (:queue d)))
  ;; Cache IDs belong to the destroyed contexts; never delete them in a new one.
  (reset! infra.render.shader/program-cache {})
  (infra.render.volume/reset-volume-cache!)
  (when-let [asset-ns (find-ns 'infra.render.asset)]
    (doseq [name '[mesh-cache texture-cache]]
      (when-let [v (ns-resolve asset-ns name)] (reset! @v {}))))
  ;; GPU slots and applied-native-cursor bookkeeping are not user settings.
  (swap! (:config s) #(-> %
                         (assoc :body-program nil :line-program nil :sprite-program nil
                                :hud-program nil :volume-program nil :particle-program nil
                                :mesh nil :cube-mesh nil)
                         (dissoc :ui/applied-cursor)))
  (load-file "/home/err/spaces/foresight/.worktrees/truth-focus-cadence/src/infra/dev/window/loop.clj")
  (swap! user/truth-cadence-reload assoc
         :stage :stopped-loaded
         :new-sim-loop @#'infra.dev.window.loop/sim-loop
         :new-drain-intents @(ns-resolve 'infra.dev.window.loop 'drain-intents))
  {:stopped true :tick (:tick @(:world s))
   :world-identity (System/identityHashCode (:world s))
   :pending-intents (.size (:queue d))})
```

Root must verify the load-file bytes/hash match committed 1ad0814 just before
this stage. Do not use `require :reload`: this JVM's classpath points to
truth-focus-input, where the loop source is older. No player/focus reload is
needed; its change is documentation. Do not reload shader namespaces, call
reload-shaders!, or reload the whole service graph.

**Pause here for runtime_scout's detached host-loop comparison.** Both native
workers must remain dead throughout any process-wide with-redefs. It can use
`:old-sim-loop`, `:old-drain-intents`, `:new-sim-loop` and `:stopped-world` without
EDN-round-tripping functions/records. It must restore all Vars and leave the
production world atom's value identical to `:stopped-world`. This is a detached
microcost experiment, not live native FPS or simulation speed evidence.

### 4. Restart both workers with retained state, without replaying inputs

The root-reviewed observation wrappers can be installed after this restart;
their samples need not include the very first consumed snapshot. First:

```clojure
(let [d @user/truth-cadence-reload s (:old d)
      stop (atom false)
      make-threads @(ns-resolve 'infra.dev.window.lifecycle 'make-service-threads)
      launch @(ns-resolve 'infra.dev.window.lifecycle 'launch-service!)
      resource-keys [:body-program :line-program :sprite-program :hud-program
                     :volume-program :particle-program :mesh :cube-mesh
                     :ui/applied-cursor]]
  (assert (= :stopped-loaded (:stage d)))
  (assert (nil? @infra.dev.window.lifecycle/service-state))
  (assert (and (not (.isAlive (:thread s))) (not (.isAlive (:sim-thread s)))))
  (assert (identical? (:stopped-world d) @(:world s)) "World changed while stopped")
  (assert (= (:stopped-camera d) @(:camera s)))
  (assert (= (apply dissoc (:stopped-config d) resource-keys)
             (apply dissoc @(:config s) resource-keys)) "Host config drift")
  (assert (= (:pending-intents d) (.size (:queue d))) "Pending queue drift")
  (let [threads (make-threads (:world-intents s) (:camera s) (:config s)
                             stop (:world s) (:queue d))]
    (launch infra.dev.window.lifecycle/service-state threads (:world s)
            (:world-intents s) (:camera s) (:config s) stop)
    (let [new @infra.dev.window.lifecycle/service-state]
      (doseq [k [:world :camera :config :world-intents]]
        (assert (identical? (k s) (k new)) (str "Replaced " k)))
      (swap! user/truth-cadence-reload assoc :stage :restarted :new new)
      {:restarted true :world-identity (System/identityHashCode (:world new))
       :tick (:tick @(:world new))
       :old-workers-alive? [(.isAlive (:thread s)) (.isAlive (:sim-thread s))]
       :new-workers-alive? [(.isAlive (:thread new)) (.isAlive (:sim-thread new))]})))
```

This resumes the exact queue, not a copied list. The last old render-frame
intent can remain queued; the new sim drains it normally and then applies the
new manual focus preparation. The loop's local pacing iteration restarts at
zero, and renderer frame/time counters, key state and mouse-gesture state reset.
World simulation time/tick/history do not reset. Window handle changes. Camera
atom/value is preserved at the stopped boundary, then normal camera tracking
resumes as the same world advances. Do not claim visually continuous video or
unchanged post-restart camera values across advancing frames.

## Minimal native proof, distinct from lifecycle repair

Use a bounded diagnostic wrapper around the existing config tick-fn and the
`domain.player/focus-follow` Var. Save exact original functions and original
tick-fn presence/value. Call each original exactly once with unchanged args,
return the identical result, and allow original exceptions to propagate. Catch
only failures in the diagnostic recording so they cannot alter normal flow.
Store at most 128 metadata rows plus counters outside the world; no full-world
history. Restore exact presence/value and Var root, confirming wrapper identity
before restoration. Installation itself changes diagnostic host callables and
adds timing overhead; it is not an uninstrumented benchmark.

The focus wrapper records its actual offset argument and last returned world
reference (one reference only), input tick/physical Spark position, returned
focus, and calling thread. At tick-fn entry, record whether its world is
`identical?` to that returned reference, the actual focus, physical Spark
position, focus radius/intensity, dt, frame offset and a bounded render-frame
counter. This is the exact consumed pre-fold world, after drained inputs and
manual preparation. Record tick-fn output position/focus separately. A renderer
wrapper can increment a counter after each original completed frame; it should
reuse the existing closed observer's guarded recording convention.

The essential input observation is this form inside a tick-fn wrapper (with
`last-follow`, `frames` and `rows` diagnostic atoms and saved `original-tick`):

```clojure
(fn [w]
  (let [sample (try
                 (let [{follow-world :world offset :offset} @last-follow]
                   {:tick (:tick w)
                    :same-prepared-object? (identical? w follow-world)
                    :offset offset :position (domain.player/observer-position w)
                    :focus (:focus-position (domain.player/get-observer w))
                    :dt (:sim/dt w) :frame-offset (:genesis/frame-offset w)
                    :render-frames @frames :thread (.getName (Thread/currentThread))})
                 (catch Throwable t {:probe-error (str t)}))
        ;; Original evaluation is outside the diagnostic catch.
        out (original-tick w)]
    (try
      (swap! rows #(vec (take-last 128
                                  (conj % (assoc sample
                                                 :output-tick (:tick out)
                                                 :output-position (domain.player/observer-position out)
                                                 :output-focus (:focus-position (domain.player/get-observer out)))))))
      (catch Throwable t (swap! probe-errors #(vec (take-last 8 (conj % (str t)))))))
    out))
```

This is an observer body for root to compose/review, not a standalone install
form. The focus wrapper must set `last-follow` to the actual original function
result and offset, and return that identical result; never call focus-follow
from the tick wrapper to make a failing old loop appear aligned. On restoration,
use `assoc` with the saved value if `contains?` originally held for `:tick-fn`,
otherwise `dissoc`; preserve unrelated concurrent config keys.

Pass criterion: many actual sim inputs on the sim thread satisfy exact
`focus = input Spark position + actual passed offset`, including pairs of sim
inputs with the same completed-render-frame counter. This establishes cadence
independent of render completion. Tick-fn output/published focus may differ
from post-fold Spark position because physical integration/recentering follows
the preparation. Do not call that expected difference a failure or imply the
fix predicts the post-fold position. For held/error-paused loops no tick-fn is
called; these are covered by focused tests, not by claiming native tick samples
prove those branches.

Root can then use ordinary native mode/arrow/comma/period/movement controls and
real window capture, retaining source and input provenance. Sample existing
production `narrowing/focus-overlap?`/binding state only on the same consumed
world if a real target is being evaluated; no surrogate eligibility predicate,
forced commitment, target injection or following-camera-as-flight claim.
Actual approach, commitment and sculpt remain separate acceptance.

Do not use `take-screenshot!`/`render-to-file` as a read-only native image helper:
that existing offscreen path ticks its supplied world and switches contexts.
Capture the real Xvfb window externally. Read native GL errors only on its
render owner thread and retain initial residual/error samples without clearing
them to manufacture a pass. Finish with observer restoration evidence, worker
and world identity, errors, config controls, source hashes and a closed inventory.
