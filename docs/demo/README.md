# Gates of Truth — watch the nebula become stars

**Start here: one continuous live run, from the starting cloud through
contraction, core formation, ignition, and the surrounding gas afterward.**
Everything is embedded on this page: **11 looping GIFs and 24 screenshots**.
Tap a still to inspect the native readouts on your phone.

[Continuous formation](#continuous-formation) · [Slower transitions](#slower-transitions) ·
[Frames from the same run](#frames-from-the-same-run) · [Earlier fixture tour](#animated-scenes) ·
[All eight panels](#all-eight-panels)

## Continuous formation

**Six minutes of the actual default seeded simulation, shown in 45 seconds
at 8× playback.** The large cloud contracts in the original wide frame; a
resolved core appears, a protostar heats up, and stellar ignition happens.
The volume renderer stays on throughout. This is a single native recording,
with no fixture switches or inserted formation states.

![Continuous live cloud contraction, core formation, stellar ignition, and gas afterward; 8 times playback](2026-10-06/formation-wide/continuous-formation.gif)

The camera holds its initial position and distance through the formation
transitions. About four minutes into the source recording (around 30 seconds
into this GIF), it eases inward over eight seconds to frame the gas around
the formed system, then holds again. This remains a view of the surrounding
material; the camera never approaches a stellar surface. Cyan loops are the
existing field overlays, while the cloudy purple/bright regions are the gas
volume. Stars are small at this physical scale and partly obscured by gas.

## Slower transitions

### The first core and heating protostar — half speed

Source seconds **24–44**, shown over **40 seconds at 0.5× playback**. The
first core is brief: the actual event ledger records condensation at tick
354, then the body climbs the mass ladder. Watch the new resolved-body
count, the core notification, and the brightening around the protostar.
The temporary “Accretion” arc label before ignition comes from a substellar
body; it does not mean a star has already formed.

![First resolved core followed by a brightening protostar in the same cloud; half-speed excerpt](2026-10-06/formation-wide/core-transition.gif)

### Stellar ignition — original speed

Source seconds **70–92**, shown at **1× playback**. This includes the actual
`:protostar → :star` event at tick 952. The native “A star ignites” message,
star count, peak temperature, and surrounding cloud remain visible together.

![Actual stellar ignition with the surrounding cloud retained; original-speed excerpt](2026-10-06/formation-wide/stellar-ignition.gif)

### Gas around the formed system — 4× playback

Source seconds **260–340**, shown over **20 seconds**. The camera is now
fixed in the wider system view after the earlier reframe. Two formed stars,
remaining gas, field structure, and the live disk count stay in the same
frame. This shows continuing gas motion and accretion accounting; the
renderer does not yet provide a clearly resolved spiral disk at this scale.

![Live gas and field structure around two formed stars; fixed system view at 4 times playback](2026-10-06/formation-wide/gas-and-accretion.gif)

### What the live run actually formed

| Evidence | Observed transition or state |
| --- | --- |
| Tick 0 | 1,000 nebula particles; no cores or stars; initial peak 12 K. |
| Tick 354, threshold event | Entity 491 changes from nebula to condensed core. |
| Tick 424, sampled snapshot | A protostar and 999 nebula particles; peak temperature about 3.85 million K. |
| Tick 952, threshold event | The same entity 491 changes from protostar to star. |
| Tick 955, sampled snapshot | One star, another protostar, a brown dwarf, and 958 nebula particles; peak about 10.18 million K. |
| Tick 3,868, final snapshot | Two stars and 904 nebula particles; about 1.74 million simulated years elapsed; disk mass about 1.11 × 10²⁹ kg. |

A separate live stellar readout after ignition measured the first star at
about **0.269 solar masses**, **0.473 solar radii**, and **13.17 million K**
in its simulation temperature component. That temperature describes the
simulation's hot stellar interior component, not a measured photosphere.
The HUD's peak temperature is the highest body temperature in the current
world. Neither should be confused with the 5,778 K staged surface shown later.

## Frames from the same run

### Starting cloud — tick 0

![Untouched cloud at tick zero, before physics resumes](2026-10-06/formation-wide/cloud-start.png)

### Contracting cloud — source second 10

![Cloud contracting within the original fixed wide frame](2026-10-06/formation-wide/contraction.png)

### First resolved core — source second 28, tick 355

The notification follows the condensation event at tick 354; subsequent
classification is rapid.

![First core notification and first resolved body while the cloud remains visible](2026-10-06/formation-wide/first-core.png)

### Heating protostar — source second 35

![Brightening protostar and surrounding gas, retaining the original wide camera](2026-10-06/formation-wide/protostar.png)

### Ignition — source second 80, tick 967

![Actual star ignition notification, star count, and cloud in the same frame](2026-10-06/formation-wide/ignition.png)

### Surrounding gas — source second 300

![Gas and field structure around the formed system in the later fixed frame](2026-10-06/formation-wide/gas-system.png)

### End of the six-minute run

![Two stars and remaining cloud at the end of the live native run](2026-10-06/formation-wide/formation-end.png)

The formation recording uses normal `domain.arc/tick-genesis` and its existing
physics parameters. New capture observations and threshold-event evidence are
under [`.ημ/diagnostics/formation-20261006T164710Z`](../../.ημ/diagnostics/formation-20261006T164710Z/).

The older tour below remains available in full. Its later scenes are
**staged ECS fixtures** with frozen physics and moving cameras. They show
surfaces and interface states; the continuous run above supplies the actual
formation evidence. The blue/green fixture has prokaryotic toy ecology.

## Animated scenes

### 1. Earlier short live nebula clip

The original 12-second nebula clip, retained for comparison. It shows early motion; use the continuous run above to see formation.

![1. Live nebula formation; native application recording](2026-10-06/live-formation.gif)

### 2. Living world

10 seconds orbiting the staged living globe. The camera and renderer animate.

![2. Living world; native application recording](2026-10-06/living-world-orbit.gif)

### 3. Molten protostar

10 seconds orbiting the staged protostar surface.

![3. Molten protostar; native application recording](2026-10-06/protostar-orbit.gif)

### 4. Stellar ignition

New recording: 10 seconds orbiting a staged star at 5,778 K.

![4. Stellar ignition; native application recording](2026-10-06/ignition-orbit.gif)

### 5. Accretion scene

New recording: 10 seconds orbiting the central star in the staged accretion world, which also contains debris and disk components. This closeup shows the star, not a claim that an animated disk is rendered.

![5. Accretion scene; native application recording](2026-10-06/accretion-orbit.gif)

### 6. Terrestrial planet

New recording: 10 seconds orbiting the staged planet before the toy ecology component is added.

![6. Terrestrial planet; native application recording](2026-10-06/planets-orbit.gif)

### 7. Click through every panel

New continuous 24-second recording of real mouse clicks through World, View, Entities, Spark, Phase, Journal, Narrator, and Multiverse. Each active panel is checked during capture; this uses the life fixture.

![7. Click through every panel; native application recording](2026-10-06/navigation-tour.gif)

The seven earlier GIFs keep their complete recording duration at 960×540,
10 fps, and loop continuously. The continuous formation overview is 6 fps;
its slower excerpts are 10 fps. All playback compression is labeled above. The 1280×720 MP4 sources remain in the repository for reproduction;
every recording has its own GIF above. The earlier compact living-world preview
is retained for existing links.

## Story screenshots

The seven arcs come from the existing `domain.arc/detect-arc`; there is no
Storybook catalog or second simulation model. All fixture inputs pass the
existing seed contracts, and the toy ecology passes its schema.

### Live nebula — starting cloud

Live physics; `:arc/genesis-nebula-collapse` at construction.

![Live nebula — starting cloud](2026-10-06/01-live-nebula.png)

### Live nebula — after the recording

Actual progression after the original 12-second clip; no arc is forced.

![Live nebula — after the recording](2026-10-06/02-live-formation.png)

### Protostar

Staged fixture · `:arc/genesis-protostar`.

![Protostar](2026-10-06/story-protostar.png)

### Ignition

Staged fixture · `:arc/genesis-ignition`.

![Ignition](2026-10-06/story-ignition.png)

### Accretion

Staged fixture · `:arc/genesis-accretion`. Star, debris, and disk accounting.

![Accretion](2026-10-06/story-accretion.png)

### Planets formed

Staged fixture · `:arc/genesis-planets-formed`.

![Planets formed](2026-10-06/story-planets.png)

### Life emergence

Staged fixture · `:arc/life-emergence`. Inspector exposes the toy ecology.

![Life emergence](2026-10-06/story-life.png)

### Living-world closeup

The same life fixture seen through the normal manual camera.

![Living-world closeup](2026-10-06/hero-living-world.png)

### Dispersed cloud

Staged fixture · `:arc/genesis-dispersed`. Empty-cloud ending.

![Dispersed cloud](2026-10-06/story-dispersed.png)

## All eight panels

Every panel is embedded below. These full-resolution stills use the life fixture
and were captured after actual mouse-hit dispatch and active-panel assertions.

### World

Phase and identity readout; some copy remains placeholder.

![World panel in the native application](2026-10-06/panel-world.png)

### View

Camera mode and sensitivity settings.

![View panel in the native application](2026-10-06/panel-view.png)

### Entities

Resolved-body list, selection, and follow behavior.

![Entities panel in the native application](2026-10-06/panel-entities.png)

### Spark

Observer state and parameter controls.

![Spark panel in the native application](2026-10-06/panel-spark.png)

### Phase

Current narrative arc; some next-phase copy is static.

![Phase panel in the native application](2026-10-06/panel-phase.png)

### Journal

Observation note; astronomy and mythology sections remain placeholders.

![Journal panel in the native application](2026-10-06/panel-journal.png)

### Narrator

Ambient, read-only presence. An addressable narrator is unavailable.

![Narrator panel in the native application](2026-10-06/panel-narrator.png)

### Multiverse

Visibly locked placeholder; gate travel is not demonstrated.

![Multiverse panel in the native application](2026-10-06/panel-multiverse.png)

## Run it yourself

<details>
<summary>Launch commands and controls</summary>


Start in the Truth repository:

```bash
clojure -M:demo serve
```

1. Watch the purple nebula. It runs `domain.arc/tick-genesis`, including the
   actual ECS physics pipeline. The cloud can naturally progress to later arcs.
2. Press **Tab** to free/lock the cursor. Open **View** to adjust the camera,
   **Spark** for observer controls, and **Phase** or **Journal** for the story.
3. From another terminal, jump to a clearly staged later arc:

   ```bash
   clojure -M:demo-client '(demo/select! :life)'
   ```

4. The selected body's inspector shows mass, radius, temperature, composition,
   and ecology. Open **Entities** to select a body through the list. The camera
   follows the selection. For the gallery macro view:

   ```bash
   clojure -M:demo-client '(demo/closeup!)'
   clojure -M:demo-client '(demo/orbit!)'
   ```

5. Open **Narrator** and **Multiverse** to see the currently available panels.
   Multiverse is visibly locked; its ghost-node text is a placeholder surface.

**Controls:** mouse/drag looks around; scroll zooms; **C** cycles camera modes;
**WASD** flies the observer; arrows move focus in manual mode; **, / .** changes
focus width; **G**, **Shift-G**, **H**, **J** are the gravity/heat palette;
**L** jumps toward life. Palette availability and currency requirements remain
visible. **Escape closes the window** in this revision. Stop the terminal
process with Ctrl-C when finished. This demo uses loopback nREPL port **7890**;
the ordinary `:dev` service uses **7888**.


</details>

<details>
<summary>Reproduce the captures, validation, and scope</summary>


On Linux, install a JDK and Clojure CLI, **Xvfb**, **FFmpeg/ffprobe**,
**xdotool**, **ripgrep**, `ss` (iproute2), and Mesa or another OpenGL 3.3
implementation. Maven dependencies resolve through `deps.edn` on first use.
No browser, model service, notebook server, or generated imagery is required.

```bash
clojure -M:demo list
clojure -M:demo check
bin/demo-capture docs/demo/my-new-capture
bin/formation-capture docs/demo/my-new-formation 360
```

The destination must not exist. The script allocates a private Xvfb display,
starts only its own demo process, waits for readiness, captures the tour, checks
panel state and runtime errors, probes video dimensions/duration, and stops the
processes it started. It refuses to compete with an existing server on the chosen port
(default 7890). For a separate capture alongside your demo, use
`TRUTH_DEMO_PORT=7891 bin/demo-capture docs/demo/my-new-capture`; both launcher
and client honor that optional environment variable. Diagnostic output is under `.ημ/diagnostics/demo-<UTC timestamp>/`.

The formation capture starts a fresh paused tick-zero cloud, verifies a visible
frame, records 360 seconds, and samples live matter states and temperature.
It eases the camera to the system view after 240 seconds and keeps the volume
renderer enabled. Its default port is **7891**. It produces the full source
MP4, the 8× GIF, and the slower excerpts above. A shorter duration omits
excerpts outside its source range. Runtime throughput can change when
transitions occur; consult the recorded ticks and observations.

To explore this fixed-frame start interactively, run
`clojure -M:demo serve formation`, then resume with:

```bash
clojure -M:demo-client '(swap! (:config @infra.dev.window/service-state) assoc :tick-fn domain.arc/tick-genesis)'
```

The RNG seed and fixture inputs are stable. Live screenshots and video timing
depend on machine speed; OpenGL pixels can differ between drivers. This is a
reproducible tour, not a byte-identical video or proof of long-term planetary
formation. The fixture worlds are frozen by an identity tick function; orbit
clips animate the camera and renderer only. Fixture frames display **STAGED ECS
FIXTURE** in the quest line.

## Verification and scope

Original screenshots and three recordings captured October 6, 2026, from base revision
`8ade66a3553bdd89696aebf4fc4628a6fe66e5ae` plus the demo/launch/HUD changes in
the original demo PR. The additional ignition, accretion, planet, and navigation
recordings were captured from PR head `51e9d24` plus the capture/port changes
in this update. Original MP4s are converted at their full duration. The native
scene is unchanged by GIF encoding. The previous README's `clojure -M:run` route targeted the
absent `infra.main`; it remains unsupported and is no longer advertised. PM2
now resolves its checkout relative to `dev/ecosystem.config.js` and inherits the
requested display.

The original demo implementation passed the full suite: **879 tests / 15,486
assertions**, zero failures/errors. The mobile-gallery update passes the render
group again: **51 tests / 8,185 assertions**, zero failures/errors. The eight
panel stills and navigation GIF are refreshed with the camera status hidden
while a menu is open, so it cannot cover the panel text. The navigation clip
ends visibly on Multiverse. An intentional remote failure verifies that cleanup
terminates and reaps its recorder, app, and display; probe output is a valid JSON
array with one entry per recording. Original raw probe output is retained. Architecture guards and the render group pass; the six-tool
`bin/analyze --strict` gate passes. The new demo clients are separately linted,
all seven scenarios are constructed/validated, and the actual capture pass
checks native UI state. Gallery images and representative video frames are
reviewed visually before handoff.

This tour does not establish natural life emergence, complete geology or
civilization, interactive mythology, gate traversal, research-actor health, or
any particular count of generated papers. The repository contains a larger
research index; those systems require separate verification.

Software/process documentation is GPL-3.0-or-later. Creative screenshot/video
assets are CC-BY-SA-4.0 where applicable, following the project license policy.

</details>
