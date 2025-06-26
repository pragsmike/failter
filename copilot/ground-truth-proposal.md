# **Technical Design Proposal: Ground Truth Evaluation**

## 1. Overview

This document proposes the integration of a "ground truth" evaluation mechanism into the Failter framework. The goal is to enhance the existing rules-based evaluation with a more objective, example-based assessment, providing a more nuanced picture of model and prompt performance.

This proposal outlines two potential implementation paths:
*   **Path A: Single-Judge with Context Switching** (Simpler, lower cost)
*   **Path B: Hybrid Dual-Judge Evaluation** (More complex, higher cost, more nuanced results)

## 2. Shared Concepts & Prerequisites

Both proposed paths share a common set of foundational changes.

### 2.1. Experiment Directory Structure

A new, optional directory named `ground-truth/` will be recognized at the root of an experiment directory.

*   **Convention:** For any input file `inputs/doc.md`, its corresponding ground truth file must be located at `ground-truth/doc.md`.
*   **Optionality:** The system must function correctly if this directory is absent or contains ground truth files for only a subset of inputs.

### 2.2. New Prompt Templates

New evaluation prompt templates must be created to instruct the judge model on how to use the ground truth data.

*   `prompts/evaluation-prompt-gt.md`: For the single-judge approach (Path A).
*   `prompts/evaluation-prompt-gt-hybrid.md`: For the ground-truth-specific judge in the dual-judge approach (Path B).

These prompts will include a new placeholder, `{{GROUND_TRUTH_OUTPUT}}`, and will instruct the judge to prioritize semantic similarity to the ground truth over strict adherence to the filtering rules.

## 3. Path A: Single-Judge with Context Switching

This path implements ground truth evaluation by changing the *context* given to our single judge, depending on the availability of a ground truth file.

### 3.1. Workflow

1.  **In `failter.evaluator/run-evaluation`:**
    *   For each trial output to be evaluated, the function will first check for the existence of a corresponding ground truth file.
2.  **Logic Fork:**
    *   **If `ground-truth/doc.md` exists:**
        *   Load the new `evaluation-prompt-gt.md` template.
        *   Construct the judge prompt using `(Original, Prompt, Generated, GroundTruth)`.
        *   Call the judge model.
        *   When writing the `.eval` file, ensure the YAML includes `evaluation-method: ground-truth`.
    *   **If no ground truth file exists:**
        *   Execute the existing logic: load `evaluation-prompt.md`, use `(Original, Prompt, Generated)`, call the judge, and write `evaluation-method: rules-based` to the `.eval` file.

### 3.2. Affected Modules & Changes

*   **`failter.evaluator.clj`:**
    *   `run-evaluation`: Will contain the main logic fork. It will need to construct the path to the potential ground truth file and check for its existence.
    *   `evaluate-one-file`: Will need to be modified to accept an optional ground truth content string and use the appropriate prompt template.
*   **`failter.reporter.clj`:**
    *   `get-trial-result`: Must be updated to parse the new `evaluation-method` key from the `.eval` file.
    *   `format-as-table` / `format-as-csv`: Should be updated to include a new `Eval_Method` column in the report. This is critical for transparency, as scores derived from different methods are not directly comparable.

### 3.3. Trade-offs for Path A

*   **Pros:**
    *   **Lower Implementation Complexity:** Changes are mostly contained within the `evaluator`.
    *   **No Cost Increase:** The number of judge model calls remains one per trial.
    *   **Delivers Core Value Quickly:** Provides the essential functionality of comparing against a perfect example.
*   **Cons:**
    *   **Mixed-Signal Reporting:** The `Avg_Score` in the report becomes an aggregation of scores derived from two different methodologies, which can be confusing. The `Eval_Method` column mitigates this but doesn't solve it.
    *   **Loss of Rules-Based Signal:** For trials with ground truth, we lose the signal of whether the model strictly followed the prompt's negative constraints.

## 4. Path B: Hybrid Dual-Judge Evaluation

This path implements a more sophisticated system where trials with ground truth are judged by **two** separate models or prompts, and their scores are combined.

### 4.1. Workflow

1.  **In `failter.evaluator/run-evaluation`:**
    *   For each trial output, check for a ground truth file.
2.  **Logic Fork:**
    *   **If `ground-truth/doc.md` exists:**
        1.  **Run Judge A (Rules-Based):** Execute the existing evaluation logic exactly as it is today.
        2.  **Run Judge B (Ground-Truth-Based):** Load the new `evaluation-prompt-gt-hybrid.md` and run a second evaluation.
        3.  **Combine Results:** Write a single `.eval` file containing a nested structure for both results.
            ```yaml
            rules_based_eval:
              grade: B
              # ...
            ground_truth_eval:
              grade: A
              # ...
            ```
    *   **If no ground truth file exists:**
        *   Execute the existing single-judge evaluation logic. The `.eval` file will only contain the `rules_based_eval` key.

### 4.2. Affected Modules & Changes

*   **`failter.evaluator.clj`:**
    *   Significant refactoring is required. `evaluate-one-file` would need to be split into sub-functions (e.g., `run-rules-judge`, `run-gt-judge`). The main function would orchestrate calling one or both of these and then writing the combined YAML structure.
    *   Robust error handling is needed for cases where one judge call succeeds and the other fails.
*   **`failter.reporter.clj`:**
    *   `get-trial-result`: Must be rewritten to parse the new nested YAML structure. It should gracefully handle files that contain one or both evaluation types.
    *   `calculate-summary`: Must be updated to compute a weighted final score.
    *   `format-as-table` / `format-as-csv`: The report format must be extended to display the individual scores (`Rules_Score`, `GT_Score`) in addition to the final weighted `Avg_Score`.
*   **`failter.core.clj`:**
    *   The `report` command should be updated to accept optional weighting parameters (e.g., `--weights 0.4:0.6`).

### 4.3. Trade-offs for Path B

*   **Pros:**
    *   **Maximum Nuance:** Provides the most detailed possible assessment of a trial's performance.
    *   **Clearer Signal:** Separates the concepts of "following instructions" from "producing a quality result."
    *   **Powerful Analysis:** The weighted scoring allows users to tune the evaluation criteria to their specific needs.
*   **Cons:**
    *   **High Implementation Complexity:** Touches nearly every part of the post-experiment pipeline.
    *   **Doubled Evaluation Cost & Time:** A significant and unavoidable drawback.
    *   **Report Complexity:** The final report becomes much wider and more data-dense, which may be overwhelming.

## 5. Recommended Path Forward

The recommended approach is to implement this feature in two distinct phases:

1.  **Phase 1: Implement Path A.**
    *   **Rationale:** This is the Minimum Viable Product (MVP) for ground truth evaluation. It delivers ~80% of the value with ~20% of the complexity. It establishes the core mechanics of handling the `ground-truth/` directory and the new prompt type.
2.  **Phase 2: Evolve to Path B.**
    *   **Rationale:** Once Path A is stable and in use, we can build upon it to implement the dual-judge system. Path A's `evaluator` logic can be refactored into the `run-gt-judge` function of Path B. This iterative approach is lower risk and allows for user feedback after Phase 1 to inform the final design of Phase 2.
