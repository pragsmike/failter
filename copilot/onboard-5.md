### **Onboarding Document for the Failter Project (Version 4.0)**

Hello,

I am your predecessor AI assistant. I have had the privilege of collaborating on a major refactoring of the Failter project, and the purpose of this document is to ensure you have all the context necessary to continue this work seamlessly. We have fundamentally evolved Failter from a three-step interactive tool into a single, idempotent, spec-driven command, making it far more suitable for programmatic integration.

This document contains the project's core principles, the critical lessons I learned during our refactoring, and a clear path forward.

#### **1. The Big Picture: What is Failter Now?**

At its heart, **Failter is a programmatic component for running LLM-based text transformation experiments.** Its primary interface is now the `failter run --spec <file>` command. The guiding philosophies have been refined to **"declarative experiments"** and **"filesystem as a resilient database."**

A user declares the desired experimental run in a `spec.yml` file, and Failter does whatever is necessary to make the on-disk state in the `artifacts_dir` match that declaration. This makes the system atomic, resumable, and auditable. You should internalize the new `README.md`, `USAGE.md`, `docs/DESIGN.md`, and `docs/spec-format.md` files, as they are the new source of truth for the project's architecture.

#### **2. Prime Directives & Rules of Collaboration**

Adherence to these rules is the most effective way to collaborate. I have included the initial directives I was given and added new ones derived directly from my experience during this refactoring session.

1.  **State Your Intentions Clearly:** Before writing or changing code, explain your understanding of the problem and your plan to solve it. This remains the fastest way to get feedback.
2.  **Provide Complete Files:** When you emit code, provide the complete, updated file. This is non-negotiable for clarity and ease of integration.
3.  **Incorporate Feedback Directly and Precisely:** When you receive a correction, update your internal representation of the code immediately and exactly as specified.
4.  **Preserve Context:** Do not remove developer comments or existing code that is not directly part of the change request.
5.  **Verify the Data Pipeline First (New & Critical):** Before implementing a "consumer" component, first ensure the "producer" component that creates its data is correct. A failure at the end of a chain is often caused by incorrect data at the beginning. Always trace the data from its source.
6.  **Every Changed Namespace Needs a Corresponding Test Update (New):** If you modify a source file (e.g., `failter.runner`), you *must* also update its corresponding test file (`failter.runner-test`) in the same turn. The test suite must always reflect the current state of the implementation.
7.  **Collaborate on Design Through Questioning (New):** When presented with a proposal, explore its edge cases and implications by asking "What if...?" questions. Our discussions about idempotency and retries led to a significantly more robust final design than was initially proposed.

#### **3. My Mistakes & Your Guide to Avoiding Them**

This is the most important section. Understanding my errors is the fastest way for you to avoid repeating them.

*   **My Mistake:** I provided an updated `failter.evaluator` that I claimed was complete, but it had a hidden, unmet dependency. It required source file paths in the `Trial` record that the `runner` was not yet writing and the `trial` record itself was not yet reading. I had implemented the consumer before its producers were correct.
*   **The Root Cause:** I was thinking about the project in terms of a linear file list, not a logical data-flow graph. I modified `evaluator.clj` because it was next in my mental list, failing to recognize that it *consumes* data produced by `runner.clj` and defined in `trial.clj`. The data wasn't there, so my proposed solution was fundamentally broken.
*   **Your Instruction:** Before you touch any component, explicitly ask yourself: **"What data does this component consume, and where does it come from?"** Then, verify that the upstream component is correctly producing and shaping that data. In this case, I should have updated `trial.clj` first, then `runner.clj` (the producer), and only then `evaluator.clj` (the consumer). Always follow the data.

#### **4. State of the Project & "Tribal Knowledge"**

The project is now in a very stable state, but some critical details are not immediately obvious from the documentation alone.

*   **The Runner-Evaluator Contract:** This is the most important piece of "tribal knowledge." The entire system now hinges on the fact that `failter.runner` writes `:source-input-path` and `:source-template-path` into the artifact's YAML frontmatter. The `failter.evaluator` reads these paths to find the original files, which is necessary because the flexible `artifacts_dir` means it can no longer guess their locations. This is a critical, non-obvious data contract between two decoupled components.
*   **The `Makefile` is the Canonical Entry Point:** Continue to trust the `Makefile`. The `test-e2e` target is the single most important sanity check after any significant change. It builds and runs a small, complete experiment from scratch and is the best way to guarantee the entire pipeline is sound.
*   **`failter.scoring` is Unchanged but Pluggable:** We did not touch the scoring logic in this refactor. Remember that it uses Clojure's multimethods and is designed to be easily extensible if you need to add new ways of grading results (e.g., star ratings, numeric scales).
*   **Legacy Commands Are Gone:** The old `experiment`, `evaluate`, and `report` commands have been removed from the `core` interface. The `single` command remains, but it should be treated as a legacy debugging tool, not part of the primary user workflow.

#### **5. The Path Forward: Next Steps**

The project is now perfectly positioned for the next wave of improvements.

1.  **Refactor Ground Truth Discovery (Recommended First):**
    *   **The Need:** Currently, the `evaluator` finds a ground truth file by crudely replacing `/inputs/` with `/ground_truth/` in the input file's path. This is brittle and implicit.
    *   **The Plan:** Make this explicit. Add an optional `ground_truth_dir` key to the `spec.yml` file. In `failter.evaluator`, if this key is present, use it to construct the path to the ground truth file. This makes the logic more robust and easier to understand.

2.  **Implement Parallel Trial Execution:**
    *   **The Goal:** The original onboarding documents mentioned this. We can now implement it cleanly. The `run-trials!` function in `failter.run` currently uses `doseq` to iterate over the collection of trials.
    *   **The Plan:** Replace the `doseq` with `pmap`. Because each trial run is a completely independent operation that writes to its own unique file path within the `artifacts_dir`, this is now a relatively safe change.
    *   **Challenges:** Be mindful of overwhelming the LiteLLM proxy with too many concurrent requests. This feature might eventually need a throttling mechanism (e.g., using a library like `claypoole`). Start with a simple `pmap` implementation.

3.  **Implement Schema-Based Spec Validation:**
    *   **The Need:** Our current spec validation is a simple check for required keys. If a user provides a key with the wrong data type (e.g., `retries: "two"` instead of `retries: 2`), it could cause a cryptic error downstream.
    *   **The Plan:** Introduce a schema validation library like `malli`. Define a schema for the `spec.yml` structure in `failter.run`. The `load-spec` function would then validate the file against this schema, providing a user with immediate, precise, and helpful error messages about what is wrong in their spec file.

Welcome to the team. By understanding the data flow, respecting the test suite, and building on these lessons, you will be well-equipped to drive this project forward.
