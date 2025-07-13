# **Failter Spec File Format Reference**

**Version:** 2.0

The Failter `spec.yml` file is a declarative YAML document that defines all aspects of an experiment run. The `failter run` command consumes this file to orchestrate the entire testing and evaluation pipeline.

## Top-Level Structure

A valid spec file is a YAML document with a single root object.

```yaml
version: 2
# --- Path directives ---
inputs_dir: ...
templates_dir: ...
artifacts_dir: ...
# --- Run configuration ---
templates: [...]
models: [...]
judge_model: ...
# --- Optional settings ---
retries: ...
output_file: ...
```

---

## **Required Keys**

These keys must be present in the spec file for a run to be considered valid.

### `version`
The version of the spec file format. This ensures that Failter can provide backward compatibility or clear error messages for older formats in the future.
-   **Type:** `Integer`
-   **Current Value:** `2`

### `inputs_dir`
The absolute or relative path to the directory containing your source input files (e.g., `.txt`, `.md` files to be transformed).
-   **Type:** `String`
-   **Example:** `/home/user/my-project/prompts/inputs`

### `templates_dir`
The absolute or relative path to the directory containing your prompt template files. Each file represents a different prompt engineering strategy.
-   **Type:** `String`
-   **Example:** `./prompt_library`

### `artifacts_dir`
The absolute or relative path to the directory where Failter will write all of its output. This includes the intermediate trial results, evaluation files, and diagnostic `.thoughts` files. This directory is the key to Failter's idempotency. If it doesn't exist, Failter will create it.
-   **Type:** `String`
-   **Example:** `./experiment-results/run-001/artifacts`

### `templates`
A list of the specific template **filenames** from within your `templates_dir` that you want to include in this particular run. This allows you to have a large library of prompts but only test a small subset at a time.
-   **Type:** `List of Strings`
-   **Example:**
    ```yaml
    templates:
      - cleanup-v1.md
      - cleanup-v2-with-cot.md
      - summarize-for-email.prompt
    ```

### `models`
A list of the model identifier strings to test against. These names must correspond exactly to the model names configured in your LiteLLM proxy.
-   **Type:** `List of Strings`
--   **Example:**
    ```yaml
    models:
      - "openai/gpt-4o-mini"
      - "ollama/qwen3:8b"
      - "anthropic/claude-3-sonnet-20240229"
    ```

### `judge_model`
The model identifier string for the LLM that will be used to evaluate the results of the trials. It is recommended to use a powerful, reliable model for this task (e.g., `openai/gpt-4o`).
-   **Type:** `String`
-   **Example:** `openai/gpt-4o`

---

## **Optional Keys**

These keys can be added to the spec file to enable additional features.

### `retries`
The number of times to retry an individual trial if it fails due to a potentially transient error (e.g., network timeout, API 503 error). A value of `2` means Failter will make one initial attempt and up to two subsequent retries, for a total of three possible attempts. The results of these retries are recorded for analysis.
-   **Type:** `Integer`
-   **Default:** `0` (no retries)
-   **Example:** `retries: 2`

### `output_file`
The absolute or relative path to a file where the final JSON report will be saved. The report is **always** printed to `stdout`, but this key provides a convenient way to also archive the result to a file.
-   **Type:** `String`
-   **Example:** `./experiment-results/run-001/final-report.json`
