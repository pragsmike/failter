Of course. The next logical step is to update the architectural documentation to reflect our new, streamlined design. The `DESIGN.md` file needs to be updated to show the new data flow centered on the `run` command and the `spec.yml` file.

Here is the updated `docs/DESIGN.md`.

```markdown
# **Failter: Design Document**

**Version:** 4.0
**Audience:** Developers joining the project who need to understand its architecture, design principles, and how to contribute.

## 1. Overview & Philosophy

**Failter** is a command-line framework for systematically testing text transformation tasks using Large Language Models (LLMs). Its primary purpose is to serve as an idempotent experimentation harness to compare the performance of different models and prompt engineering strategies.

The core philosophy remains **"prompts as code"** and **"filesystem as database."** Instead of implementing logic in a traditional programming language, Failter defines transformations in natural language via prompt templates. The framework then provides the machinery to rigorously test and evaluate these prompts. The filesystem provides a resilient, portable, and auditable record of the entire run.

## 2. Core Concepts

*   **Specification File (`spec.yml`):** The single source of truth for an experiment. This declarative YAML file defines all parameters for a run: paths to inputs and templates, models to use, the judge model, and the directory for storing all generated files.

*   **Artifacts Directory:** A user-specified directory that holds the complete state of a run. It contains all trial outputs, evaluation files, and diagnostic information. This directory's existence and contents are what make the `run` command idempotent.

*   **`Trial` Record:** The atomic unit of work, defined as a Clojure record. A single `Trial` represents the plan to process one input file with one prompt template using one LLM model. After execution, this record is hydrated with performance metrics (`execution-time-ms`, `retry-attempts`, `tokens-in`, `tokens-out`) or an `error`.

*   **`Eval` Record:** The result of a qualitative assessment. It contains the `Trial` record it is assessing, along with a normalized numeric `score` (0-100) and a `rationale` from the judge LLM.

## 3. System Architecture & Data Flow

Failter now operates as a single-stage pipeline, orchestrated by the `failter run` command, which is driven by the `spec.yml` file. The key design principle is the flow of data from the spec file to the orchestrator, which then manages state on disk in the `artifacts_dir`.

```
                  +--------------------------+
                  | <spec.yml>               |
                  | (Defines the entire run) |
                  +--------------------------+
                              |
                              v
+-----------------+     +----------------------+     +-----------------+
| failter run     |---->| failter.run          |---->| failter.runner  |
| (CLI Entrypoint)|     | (Orchestrator)       |     | (Executor)      |
+-----------------+     +----------------------+     +-----------------+
                              ^                            | (writes)
                              |                            |
                              |                            v
                              |              +--------------------------+
                              +--------------| <artifacts_dir>/         |
                                             | (State on disk)          |
                                             +--------------------------+
                                                           ^
                                                           | (reads)
                                                           |
+-------------------+     +--------------------+     +-----------------+
| failter.reporter  |<----| failter.evaluator  |<----| failter.trial   |
| (Generates JSON)  |     | (Assessor)         |     | (Hydrates Record)|
+-------------------+     +--------------------+     +-----------------+
        |
        v
+--------------------------+
| stdout (JSON Report)     |
+--------------------------+
```

1.  The `failter.run` namespace reads the `spec.yml`.
2.  It creates a sequence of `Trial` plans.
3.  For each `Trial`, it checks for an existing artifact in the `artifacts_dir`.
4.  If the artifact is missing, it calls `failter.runner` to execute the trial and write the artifact to disk. The runner implements a retry loop.
5.  After all trials are complete, `failter.evaluator` is called. It scans the `artifacts_dir` and evaluates any trials that are missing a corresponding `.eval` file, writing the result to disk.
6.  Finally, `failter.reporter` scans the `artifacts_dir` one last time, reads all final `Trial` and `Eval` data from the artifacts, and generates the final JSON report which is printed to `stdout`.

## 4. Key Design Principles

*   **Filesystem as Database:** This remains the core principle for resilience and idempotency. The entire state of an experiment—its results and evaluations—is contained within the `artifacts_dir`.
*   **Declarative & Atomic Runs:** The user declares the desired state in a `spec.yml` file, and the `failter run` command performs the single, atomic operation to make the `artifacts_dir` match that state.
*   **First-Class Data Records:** The system continues to use the `Trial` and `Eval` records to provide a clear, self-documenting structure for the data that flows between the major components.
*   **Pluggable Scoring Strategies:** The "policy" for how to score a trial (e.g., letter grades, numeric scales) remains decoupled from the "mechanism" of running the evaluation, using Clojure's multimethods in the `failter.scoring` namespace.

## 5. Codebase Tour (Module Breakdown)

*   `failter.core`: Main application entry point. Parses CLI arguments and dispatches to the `run` command.
*   **`failter.run` (New):** The primary orchestrator. Responsible for parsing the spec file and managing the `run -> evaluate -> report` data flow.
*   `failter.runner`: **Consumes** a `Trial` record and executes it with retries. Writes the final output artifact to the `artifacts_dir`, including detailed performance metadata and source paths.
*   `failter.evaluator`: Scans the `artifacts_dir` for completed trials and **produces** `.eval` artifacts by calling a judge LLM.
*   `failter.reporter`: A pure **consumer** of the on-disk artifacts in `artifacts_dir`. Generates the final JSON report.
*   `failter.trial`: Defines the `Trial` and `Eval` records, and the logic to construct them by reading artifact files from disk.
*   `failter.exp-paths`: A utility for calculating artifact paths within the `artifacts_dir`.
*   `failter.scoring`: The central hub for all scoring-related logic (unchanged in this refactor).
