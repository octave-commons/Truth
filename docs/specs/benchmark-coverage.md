# Benchmark Coverage Suite

A coverage suite for the Gates of Truth benchmark suite, analogous to `bin/coverage` for tests but tuned to the coarser nature of benchmarks.

## Why namespace-level coverage?

Tests are fine-grained and can be measured with line/form instrumentation (`cloverage`). Benchmarks are intentionally coarse: a single benchmark like `phase0` exercises dozens of systems across many namespaces. Line-level instrumentation would also distort the timings we care about. Therefore, benchmark coverage is measured at the **namespace level** and is **declarative**: each benchmark group states which source namespaces it is intended to exercise.

This gives us a lightweight answer to the question: *"How much of our source tree has a benchmark that targets it?"*

## Model

- **Source namespace**: any Clojure namespace under `src/` (domain, infra, shape, law quadrants).
- **Benchmark group**: an entry in `gates-of-truth.bench/benchmark-groups`.
- **Coverage**: a source namespace is *covered* if at least one benchmark group declares it in its `:covers` set.
- **Excluded quadrants**: `law` namespaces are excluded from coverage totals. They contain Malli schemas and contracts, not simulation code that is meaningfully benchmarked by Criterium; including them would dilute the metric with noise.
- **Excluded namespaces**: `shape.core` is excluded because it is pure data constructors (Shape, Claim, UUID helpers) with no performance relevance. `shape.spatial` remains in the denominator because it is hot-path 3D math (vec3, AABB, octants) used by gravity, collision, hydro, and the spatial index.

Coverage is **not** transitive. If `:gravity` covers `domain.gravity.barnes-hut`, and that namespace uses `domain.gravity.barnes-hut.tree`, the sub-namespace is **not** considered covered unless a benchmark group explicitly targets it. This keeps the report honest and avoids magical inflation.

## Registry format

Each benchmark group entry includes a `:covers` set of namespace symbols:

```clojure
(def benchmark-groups
  {:gravity {:label "Barnes-Hut Gravity"
             :ns 'gates-of-truth.bench.gravity
             :covers #{'domain.gravity.barnes-hut
                       'shape.spatial}}})
```

Rules for adding `:covers`:
- List only namespaces the group is **primarily** intended to benchmark. Scaffold imports (e.g. `domain.ecs.core` used only to build a test world) should not be listed unless the group actually benchmarks that namespace.
- Use real namespace symbols (not strings). The coverage analyzer validates that each symbol maps to an existing `.clj` file.
- When a group benchmarks a whole subsystem, list the subsystem facade and any important sub-namespaces it directly exercises.

## Running the report

```bash
bin/bench-coverage              # full text report
bin/bench-coverage --threshold 50   # exit non-zero if namespace coverage < 50%
```

The report prints:
- Total and quadrant-level coverage percentages
- Which benchmark groups cover which namespaces
- Source namespaces with no benchmark coverage
- Registry validation errors (e.g. typos in `:covers`)

## CI integration

The `:bench-coverage` alias in `deps.edn` makes the benchmark path available to the test classpath. The `bin/bench-coverage` script is the canonical entry point. For CI, a threshold can be enforced:

```bash
bin/bench-coverage --threshold 40
```

Threshold should be raised over time as the benchmark suite grows. Starting at a low threshold and raising it prevents adding a large block of unbenchmarked code without noticing.

## Differences from test coverage

| Test coverage (`bin/coverage`) | Benchmark coverage (`bin/bench-coverage`) |
|---|---|
| Line/form instrumentation | Namespace-level declarations |
| Runtime: runs all tests | Static: reads registry, no benchmarks run |
| Measures what code is executed | Measures what code has a benchmark target |
| Can distort timing (doesn't matter for tests) | Cannot distort timing (no instrumentation) |

## Future directions

- Function-level coverage by recording which public vars each benchmark calls (requires lightweight tracing, but would add measurement overhead).
- Subsystem-level coverage buckets (e.g. "accretion physics", "renderer", "ECS") to make gaps easier to prioritize.
- Historical tracking of coverage percentage in `perf_report_*.txt` or a dedicated artifact.
