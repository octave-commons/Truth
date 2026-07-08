## Goal: Peer-review research notebooks

For every notebook in `docs/research/`, produce a structured review that checks:
- Scientific accuracy and plausibility of claims
- Completeness of the research question and literature survey
- Quality of governing equations (correct notation, explained terms, typical values)
- Soundness of the Clojure pseudocode and promotion path
- Correctness of any numerical experiments, toy models, and benchmark comparisons
- Clarity and correctness of figures, charts, and embedded images
- Citation completeness: every claim has a source, every source is real
- Internal consistency with other notebooks and the simulation architecture

The review must be written to the actor's outbox as a markdown report and, for significant issues, summarized as a message to the relevant domain actor's inbox.
