# Method: Deep Research Protocol

Follow the `deep-research` skill methodology exactly:

1. **Discovery** — Search arxiv for review papers, simulation methods, and benchmarks.
   Build a bibliography before reading anything deeply.
2. **Deep Reading** — Read abstracts first, then methods sections completely.
   Extract governing equations into LaTeX. Note parameter ranges and limiting cases.
3. **Synthesis** — Organize by subtopic, not by paper. Identify consensus models
   and open debates. Choose the model best suited to our ECS architecture.
4. **Implementation Sketch** — Write Clojure pseudocode mapping to our ECS patterns:
   `defrecord` for components, system functions taking/returning world.
5. **Toy Model** — Implement a minimal numerical experiment. Run against published
   benchmarks. Generate charts comparing results to literature.
6. **Documentation** — Write the full notebook with all sections, charts, citations,
   and a concrete promotion path.

## arxiv Search Strategy

- `<topic> review` for surveys
- `<topic> simulation method` for implementation
- `<topic> benchmark` for validation data
- `<topic> Clojure` or `<topic> Julia` for code references
- Check ADS citation counts to find foundational papers

## Chart Generation

Use Python (matplotlib/numpy) for charts. Save to `docs/research/cosmology/img/`.
Generate:
- Parameter space diagrams
- Comparison plots (our model vs published)
- Convergence studies
- Phase diagrams
