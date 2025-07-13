## Proposal from developers of PromptCritical, a tool that makes heavy use of Failter

To the Failter Development Team,

First, thank you for building a powerful and flexible tool for prompt evaluation. The core design of Failter as a "black box" experiment runner that PromptCritical can orchestrate has been fundamental to our architecture. As we refine this integration, we've identified several opportunities that could make Failter even easier to use from a programmatic partner's perspective.

Here is our advice, focused on creating a more robust and streamlined machine-to-machine interface.

### 1. The Core Recommendation: A Single "Run" Command with a Spec File

The current `experiment -> evaluate -> report` three-step process is effective for interactive use but creates complexity for an automated caller like PromptCritical. It requires us to manage state and handle potential failures at each intermediate step.

**Our single most impactful suggestion is to adopt a single, idempotent `run` command that takes a self-contained specification file.**

```bash
# Proposed Invocation
failter run --spec /path/to/my-contest-spec.yml
```

This `spec.yml` file would define the entire contest:
```yaml
# Example my-contest-spec.yml
version: 2
# Instead of `pcrit` building a directory, it just provides paths.
inputs_dir: /path/to/user/inputs
templates_dir: /path/to/pcrit/pdb
# Allow specifying individual templates to run
templates: [P1.prompt, P5.prompt, P12.prompt]
# Configuration is now part of the spec
models:
  - "openai/gpt-4o"
  - "anthropic/claude-3-sonnet"
judge_model: "openai/gpt-4o-mini"
# Where to write the final report
output_file: /path/to/pcrit/generations/gen-001/contests/my-contest/report.csv
```

**Benefits for PromptCritical:**
*   **Simplification:** This would eliminate the need for PromptCritical to meticulously create a `failter-spec/` directory with symlinks. We could simply generate this spec file and invoke Failter.
*   **Atomicity:** The `failter run` command would be a single transactional operation. It either succeeds and produces a report, or it fails. This is much easier to manage than the current multi-step sequence.
*   **Reproducibility:** The spec file itself becomes a perfect, auditable artifact describing exactly what was run.

### 2. Standardize on Structured Output (JSON) to `stdout`

While `report.csv` is human-readable, machine-to-machine communication is far more robust with structured data like JSON.

**Our recommendation is to have `failter run` print its results as a JSON array to `stdout` by default.** A `--output-file` flag could still be used to write a CSV for interactive use.

The JSON output for each prompt should contain:
*   `prompt_id`: The identifier for the prompt (e.g., "P1").
*   `score`: The final fitness score.
*   `usage`: A nested object with the **immutable usage facts**: `model_used`, `tokens_in`, and `tokens_out`. This aligns perfectly with PromptCritical's internal cost-accounting philosophy.
*   `error`: A string field that is null or absent on success, but contains a detailed error message if that specific prompt failed during evaluation.

```json
// Example stdout from `failter run`
[
  {
    "prompt_id": "P1",
    "score": 0.92,
    "usage": {
      "model_used": "openai/gpt-4o",
      "tokens_in": 1250,
      "tokens_out": 430
    },
    "error": null
  },
  {
    "prompt_id": "P5",
    "score": null,
    "usage": null,
    "error": "API call failed with status 429: Rate limit exceeded"
  }
]
```

**Benefits for PromptCritical:**
*   **No File I/O:** We could parse the results directly from the command's output stream, avoiding the need to find and read a file from the filesystem.
*   **Rich Error Handling:** We could immediately identify which specific prompts failed and why, allowing for more granular retries or analysis.

### 3. Separate Logs from Data

Following from the previous point, it's a best practice for CLI tools to use `stdout` for machine-readable data and `stderr` for human-readable logs, progress indicators, and warnings. This allows a programmatic caller to capture the data stream cleanly while still being able to log the progress stream for debugging purposes.

### Summary of Recommendations

In essence, our advice centers on making Failter behave more like a standard Unix-style command-line utility, optimized for programmatic composition.

1.  **Embrace a Declarative Spec File:** Move from a complex directory structure to a single YAML or EDN file that defines the entire job.
2.  **Provide a Single, Atomic `run` Command:** Consolidate the `experiment`, `evaluate`, and `report` steps into one idempotent command.
3.  **Use `stdout` for Structured JSON Output:** This makes parsing results simpler and more robust than file-based CSV.
4.  **Use `stderr` for Human-Readable Logging:** This separates data from diagnostic information.

Adopting these patterns would significantly simplify the integration logic within PromptCritical, making the entire `evaluate` step more robust, transparent, and easier to maintain. Thank you for your consideration.
