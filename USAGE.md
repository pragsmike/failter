# **User Guide: Driving Experiments with Failter**

This guide explains how to use the **Failter** command-line tool to systematically test, evaluate, and optimize your prompts using the modern, spec-driven workflow.

## 1. Core Purpose

Failter is an experimentation framework designed to help you answer questions like:

*   "Which of my prompt variations performs best for this text transformation task?"
*   "Which LLM model is the most cost-effective and performant for my specific use case?"
*   "How consistently does a given model-prompt combination perform?"

It automates the tedious process of running numerous trials, evaluating the results using a judge LLM, and summarizing them so you can focus on making data-driven decisions.

## 2. The Modern Workflow: The `run` Command

The Failter workflow is a single, idempotent pipeline executed from the command line. You define the entire experiment in a declarative **`spec.yml`** file, and then execute it with the `run` command.

```bash
# Define your entire experiment in a single file
# then execute it with one command.
clj -M:run run --spec /path/to/my-experiment/spec.yml
```

This single command handles the entire `Experiment -> Evaluate -> Report` lifecycle:
1.  **Orchestration:** Failter reads your `spec.yml` to determine the full matrix of trials to run (all combinations of inputs, templates, and models).
2.  **Execution (Idempotent):** It creates an `artifacts_dir` where all intermediate and final results are stored. It intelligently skips any trial that has already been completed, allowing you to safely re-run the command to resume a failed or interrupted job.
3.  **Evaluation:** It uses a powerful "judge" LLM to automatically review and score the quality of each output file. This step is also idempotent and skips work that has already been evaluated.
4.  **Reporting:** It gathers all metadata, performance metrics, and evaluation scores into a structured JSON report that is printed directly to `stdout` for easy programmatic consumption.

## 3. Setting Up Your Experiment: The `spec.yml` File

To create an experiment, you create a single YAML file that defines all aspects of the run. This is the new source of truth for Failter.

#### **Example `spec.yml`**
```yaml
# The version of the spec format. Currently '2'.
version: 2

# --- Required Paths ---
# A directory containing the source files to test your prompts against.
inputs_dir: /path/to/my-project/inputs
# A directory containing your different prompt template files.
templates_dir: /path/to/my-project/templates
# The directory where Failter will write all output artifacts.
# This directory is the key to idempotency and makes the run portable.
artifacts_dir: ./run-results/gen-01-artifacts

# --- Required Run Configuration ---
# A list of specific template filenames from templates_dir to include in this run.
templates:
  - cleanup-aggressive.md
  - cleanup-v2-with-cot.md

# A list of LLM models to test. Must match the names in your LiteLLM proxy.
models:
  - "openai/gpt-4o-mini"
  - "ollama/qwen3:8b"

# The model to use for evaluating the results.
judge_model: "openai/gpt-4o"

# --- Optional Configuration ---

# The number of times to retry a failed LLM call.
# A value of 2 means 1 initial attempt + 2 retries = 3 total attempts.
# Defaults to 0 (no retries).
retries: 2

# A path to a file where the final JSON report will also be saved.
# The report is always printed to stdout regardless of this setting.
output_file: ./run-results/final-report.json
```

## 4. Running the Pipeline

All you need is the single `run` command, pointed at your spec file.

```bash
# It's recommended to run from your project root.
clj -M:run run --spec path/to/my/spec.yml
```
Failter will print logs and progress to `stderr`, while the final, machine-readable JSON report will be printed to `stdout`.

## 5. Interpreting the Results

The primary output of a successful run is a JSON array printed to your console. Each object in the array contains the aggregated results for a specific prompt-model combination.

#### **Example JSON Output from `stdout`**
```json
[
  {
    "prompt_id": "cleanup-v2-with-cot.md",
    "score": 95,
    "usage": {
      "model_used": "openai/gpt-4o-mini",
      "tokens_in": 1834,
      "tokens_out": 1790
    },
    "performance": {
      "execution_time_ms": 8120,
      "total_trial_time_ms": 15300,
      "retry_attempts": 1
    },
    "error": null
  },
  {
    "prompt_id": "cleanup-aggressive.md",
    "score": null,
    "usage": {
      "model_used": "ollama/qwen3:8b",
      "tokens_in": null,
      "tokens_out": null
    },
    "performance": { ... },
    "error": "Trial failed: Final attempt failed: API call timed out"
  }
]
```

### How to Make Decisions:

1.  **`score`**: The primary metric for quality, typically on a 0-100 scale. Higher is better. It will be `null` if the trial failed.
2.  **`usage`**: Provides the key facts about the LLM interaction. `tokens_in` and `tokens_out` are critical for understanding the cost and scale of a given prompt.
3.  **`performance`**: Gives insight into the reliability and speed of the model.
    *   `execution_time_ms`: The wall-clock time for the final, successful LLM call.
    *   `retry_attempts`: A non-zero value here is a strong signal that the model endpoint may be unreliable.
4.  **`error`**: If a trial ultimately failed after all retries, this field will contain the reason, providing crucial data for debugging or disqualifying an unstable model.

### Deeper Analysis: The `artifacts_dir`

For any surprising results, you can drill down into the artifacts that Failter created in the directory you specified for `artifacts_dir`.

*   **The Output File:** Inside `artifacts_dir/MODEL_TEMPLATE/`, you will find the generated text file. Its YAML frontmatter contains the full, detailed metrics for that specific run, including all retry errors.
*   **The Thoughts File (`.thoughts`):** If the model produced an "internal monologue" (e.g., inside `<think>...</think>` tags), it is saved here. This is invaluable for debugging *why* a model is misinterpreting your prompt.
*   **The Evaluation File (`.eval`):** This contains the structured output from the judge model, including the raw `score` and a specific `rationale` explaining its judgment.
