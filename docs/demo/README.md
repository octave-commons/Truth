# Gates of Truth — the complete visual tour

**Everything is embedded on this page. Scroll to see seven looping GIFs, all
seven story states, and every navigation panel. No video player or download is
needed.** Tap a still image if you want to inspect the text more closely.

[Animated scenes](#animated-scenes) · [Story screenshots](#story-screenshots) ·
[All eight panels](#all-eight-panels) · [Run it yourself](#run-it-yourself)

These are real captures of the native JVM/OpenGL application. The nebula runs
live physics. Later scenes are **staged ECS fixtures**: they show the renderer
and interface, with the simulation frozen and the camera moving. The blue/green
world contains a terrestrial planet and prokaryotic toy ecology; it does not
claim natural life emergence during capture. Macro views move the observer to
the subject at true scale; no body radius is enlarged.

## Animated scenes

### 1. Live nebula formation

12 seconds of the normal seeded physics world. The simulation clock advances.

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

New continuous 18-second recording of real mouse clicks through World, View, Entities, Spark, Phase, Journal, Narrator, and Multiverse. Each active panel is checked during capture; this uses the life fixture.

![7. Click through every panel; native application recording](2026-10-06/navigation-tour.gif)

The GIFs keep the complete recording duration at 960×540, 10 fps, and loop
continuously. The 1280×720 MP4 sources remain in the repository for reproduction;
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
```

The destination must not exist. The script allocates a private Xvfb display,
starts only its own demo process, waits for readiness, captures the tour, checks
panel state and runtime errors, probes video dimensions/duration, and stops the
processes it started. It refuses to compete with an existing server on the chosen port
(default 7890). For a separate capture alongside your demo, use
`TRUTH_DEMO_PORT=7891 bin/demo-capture docs/demo/my-new-capture`; both launcher
and client honor that optional environment variable. Diagnostic output is under `.ημ/diagnostics/demo-<UTC timestamp>/`.

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

The existing full suite passes: **879 tests / 15,486 assertions**, zero
failures/errors. Architecture guards and the render group pass; the six-tool
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
