## Method: Notebook execution and review

1. List all notebooks under `docs/research/`.
2. For each notebook, identify executable artifacts:
   - Python scripts (`.py`) referenced in the notebook
   - Jupyter notebooks (`.ipynb`)
   - Clojure snippets or scripts
3. Run the artifacts in a safe environment and capture outputs, errors, and warnings.
4. Verify that figures/images referenced by the notebook exist and are readable.
5. Check that equations render as valid LaTeX and that symbols are defined.
6. Verify that every non-trivial claim has a citation in the References section.
7. Record findings as a structured review report.

Use bash for execution, Read for inspection, and Grep for searching cross-references. Do not modify source notebooks.
