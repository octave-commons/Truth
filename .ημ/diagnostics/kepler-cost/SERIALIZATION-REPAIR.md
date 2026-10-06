# Metrics representation failure and bounded repair

(世, p=1.0) Oracle capture completed successfully on the unchanged solver,
followed by 21 tests / 596 assertions with zero failures or errors. The first
cost action then ran all five workloads but exited 1 at the final strict EDN
equality check. It did not write `before-propagation.edn`; the subsequent phase0
stage did not run. The failed action's numerical cost values are unavailable,
and no values are reconstructed or claimed for that attempt.

(世, p=1.0) The tiny actual JVM probe in `serializer-probe.clj` confirms the
representation mismatch: Criterium 0.4.6 returns an
`criterium.core.OutlierCount` record; `clojure.pprint` prints its four fields as
an ordinary map; EDN reads that map, which is unequal to the original record.
The probe retains all four distinct counts (0,1,2,3) and demonstrates that
explicit `{:record-type "criterium.core.OutlierCount" :fields {...}}` round-trips
with equal data. See `serializer-probe.log` and its command/exit record.

(己, p=1.0) Root approved the adapter-only repair, independently reviewed by
`intent_research`: only metrics `:outliers` receives this explicit encoding;
all original entries are retained with `into {}` and the class is checked.
There is no generic record coercion. Solver source and the frozen fixture are
unchanged. Strict EDN equality and refusal to overwrite outputs remain.
Future rejected output is saved to a unique failure path before throwing,
including when EDN parsing itself throws.

(己, p=1.0) `attempt1/kepler.clj` preserves the exact initial adapter, SHA256
`e63574e742ce75147601e19f81237b57c18f0ebd5bcb1e3641b6f6df39ab69bc`.
`attempt1/serialization-error.edn` preserves the original error report.
`baseline-source.json` binds the first attempt; `rerun-source.json` binds the
corrected adapter and unchanged fixture. The numerical capture/tests were not
rerun merely for this metrics representation repair. Cost actions are labeled
`03b-propagation-cost` and `04-phase0-cost`; their eventual exit records govern
whether the retry passed.
