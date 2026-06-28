# Skill: clojupyter — Clojure Notebooks & Research Loop

## What This Skill Is

This skill teaches agents how to:
1. Use **CloJupyter** (the Clojure Jupyter kernel) to author and run `.ipynb`
   notebooks in this repo.
2. Connect those notebooks to the **running nREPL** so research code can
   read and mutate live simulation world-state.
3. Run a **tight research loop** that feeds empirical physics/geology/biology
   findings directly into the Gates of Truth simulation domain layer.

---

## Setup (one-time, per machine)

```bash
# 1. Install Jupyter Lab (Python side)
pip install jupyterlab

# 2. Install the clojupyter kernel into Jupyter
clj -M:notebook
# This runs clojupyter.cmdline/install — you'll see the kernel listed by:
jupyter kernelspec list

# 3. Start the notebook server (PM2 handles this in dev)
pm2 start dev/ecosystem.config.js --only truth-notebook
# Or manually:
jupyter lab --port 8888 --no-browser
```

---

## Connecting Notebooks to the Running nREPL

The simulation (`gates-of-truth-dev`) starts an nREPL on **port 7888**.
Notebooks can connect to it using `nrepl.core`:

```clojure
;; In a notebook cell:
(require '[nrepl.core :as nrepl])
(def conn (nrepl/connect :port 7888))
(def client (nrepl/client conn 1000))

;; Evaluate a form in the live simulation context:
(nrepl/message client {:op "eval" :code "(keys @world/state)"})
```

This lets research notebooks **observe and probe** live planet state without
restarting the simulation.

---

## Research Loop Protocol

The goal is a closed feedback loop:

```
┌─────────────────────────────────────────────────────────────┐
│  Real-world model (paper / dataset / formula)               │
│        ↓  transcribe into notebook cell                     │
│  Clojure implementation of model                            │
│        ↓  run against synthetic domain data                 │
│  Validate outputs (compare to known values / ranges)        │
│        ↓  if valid                                          │
│  Promote to domain/ namespace (defrecord / tick fn)         │
│        ↓                                                    │
│  Write Malli schema in law/                                 │
│        ↓                                                    │
│  Write μ-test in test/domain/                               │
│        ↓                                                    │
│  PR to main                                                 │
└─────────────────────────────────────────────────────────────┘
```

### Notebook naming convention

```
notebooks/
  research/
    physics/      ← orbital mechanics, thermodynamics, fluid dynamics
    geology/      ← plate tectonics, volcanism, soil formation, erosion
    biology/      ← Lotka-Volterra, nutrient cycles, speciation
    atmosphere/   ← cellular automaton weather models, radiative transfer
    hydrology/    ← river routing, ocean salinity, precipitation
  scratch/        ← ephemeral exploration, never promoted
```

Each research notebook should:
- Cite its primary source (paper DOI or textbook section) at the top.
- Include a **"Promotion Checklist"** cell at the bottom:
  - [ ] Domain record defined
  - [ ] Malli schema written
  - [ ] μ-test passes (green)
  - [ ] Docstring on all public vars
  - [ ] PR opened

---

## Domain Areas & Key Models to Implement

### Physics
- Stefan-Boltzmann for surface temperature from stellar luminosity
- Blackbody radiation + albedo for day/night temperature delta
- Navier-Stokes simplified for atmospheric pressure gradients

### Geology
- Arrhenius viscosity model for mantle convection
- Isostasy (Airy model) for crust thickness ↔ elevation
- Denudation / erosion rates (Stream Power Law)

### Biology
- Lotka-Volterra predator-prey (already referenced in biome.clj)
- Liebig's Law of the Minimum for plant growth
- Species-Area Relationship (island biogeography) for biodiversity

### Atmosphere
- Clausius-Clapeyron for saturation vapor pressure → cloud formation
- Hadley cell approximation for large-scale wind patterns
- Radiative forcing from CO₂ / H₂O for greenhouse effect

### Hydrology
- Manning's equation for river flow velocity
- Penman-Monteith for evapotranspiration
- Saltwater intrusion model for coastal cells

---

## Agent Workflow

When asked to research a physical/biological model for the simulation:

1. **Search** for the governing equations (use `search_web` with the model
   name + "governing equations" or "Clojure implementation").
2. **Create** a notebook in `notebooks/research/<domain>/` with the
   transcribed equations and a Clojure implementation.
3. **Validate** by running the notebook against test inputs and comparing
   to known values from the literature.
4. **Promote** valid implementations to `domain/` following the Research
   Loop Protocol above.
5. **Open a PR** with the new domain function and its μ-test.

---

## GitHub Rendering

GitHub renders `.ipynb` files natively — output cells, charts, and markdown
are all visible in the GitHub UI. Commit notebooks with **cleared outputs**
except for final validated results, to keep diffs clean.

To clear outputs before committing:
```bash
jupyter nbconvert --ClearOutputPreprocessor.enabled=True \
  --to notebook --inplace notebooks/research/**/*.ipynb
```

---

## Key Files

| File | Purpose |
|------|---------|
| `dev/ecosystem.config.js` | PM2 process: `truth-notebook` app |
| `deps.edn` `:notebook` alias | Installs clojupyter kernel via `clj -M:notebook` |
| `notebooks/research/` | Research notebooks (versioned) |
| `notebooks/scratch/` | Ephemeral exploration (gitignored recommended) |
| `.agents/skills/clojupyter.md` | This skill file |
