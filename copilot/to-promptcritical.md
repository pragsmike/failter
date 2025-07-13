### **To: The PromptCritical Development Team**
### **From: The Failter Development Team**
### **Subject: Design Update for the `failter run` Command**

First, thank you for the thoughtful and well-structured proposal regarding Failter's programmatic interface. Your perspective as a key integration partner is invaluable, and your recommendations have provided a clear path forward for making Failter a more robust and streamlined tool for machine-to-machine communication.

We are pleased to confirm that we will be adopting the core of your recommendations. We will also be building upon them to incorporate enhanced resilience and idempotency, principles that are central to Failter's design philosophy.

This document outlines the final design for the new `failter run` command.

#### **Core Proposal Adopted**

As per your advice, we will be implementing the following foundational changes:

1.  **A Single `run` Command:** We will introduce a new, primary command, `failter run`, which will consolidate the existing `experiment`, `evaluate`, and `report` steps into a single, atomic operation.
2.  **Declarative Spec File:** This `run` command will be driven by a single YAML specification file, which will define all aspects of the experiment (inputs, templates, models, etc.), eliminating the need for a rigid directory structure.
3.  **Structured JSON Output to `stdout`:** The command's primary output will be a stream of structured JSON written to `stdout`, making it easily parsable by your system.
4.  **Logs to `stderr`:** All human-readable logging, progress indicators, and warnings will be directed to `stderr`, cleanly separating diagnostic information from the data stream.
5.  **Focus on Token Usage:** As requested, we will be removing the estimated monetary `cost` metric in favor of reporting the immutable usage facts: `model_used`, `tokens_in`, and `tokens_out`.

#### **Enhancement 1: Full Idempotency via an `artifacts_dir`**

Your proposal correctly identified the need for a simpler, atomic `run` command. To make this command truly robust and "fire-and-forget," we are extending the concept by leveraging Failter's "filesystem as database" principle to guarantee idempotency.

The spec file will include a key, `artifacts_dir`, which points to a directory where Failter will store all intermediate files (trial outputs, evaluations, etc.).

**How it works:**
When `failter run` is executed, it will:
1.  Read the spec and identify all required trials.
2.  For each trial, it checks if the corresponding output artifact already exists in the `artifacts_dir`.
3.  If the artifact exists, the work is skipped. If not, the trial is executed.
4.  The process is repeated for the evaluation step.
5.  Finally, the report is generated from the complete set of artifacts on disk.

**Benefit to you:** This makes the `run` command fully resumable. If a run fails halfway through due to a network outage or any other issue, you can simply execute the exact same command again. Failter will intelligently pick up exactly where it left off, completing only the missing work. A fully successful run that is executed again will be a quick, no-op operation that simply regenerates the final report.

#### **Enhancement 2: Transparent Retries for Increased Resilience**

Production systems often face transient errors (e.g., temporary API unavailability, network timeouts). To handle these gracefully without requiring an external retry loop on your end, we are introducing a configurable retry mechanism.

However, instead of simply hiding these failures, we believe they represent valuable data about a model's operational reliability. Therefore, our implementation will be transparent.

**How it works:**
The spec file can optionally include a `retries` key (e.g., `retries: 3`).

*   For each trial, Failter will attempt to execute it up to `retries + 1` times.
*   If a trial succeeds after one or more failures, the final artifact will contain metadata about the previous attempts, including the error messages and total time elapsed.
*   The final JSON report will expose this data, so your system can distinguish between a trial that succeeded on the first attempt and one that succeeded on the third.

**Example JSON output for a trial that succeeded after 2 failed attempts:**
```json
{
  "prompt_id": "P1.prompt",
  "score": 0.92,
  "usage": {
    "model_used": "openai/gpt-4o",
    "tokens_in": 1250,
    "tokens_out": 430
  },
  "performance": {
    "execution_time_ms": 8540,
    "total_trial_time_ms": 25310,
    "retry_attempts": 2
  },
  "error": null
}
```

This gives you the resilience you need while providing deeper insight into the practical performance of the models being tested.

### **Putting It All Together: The Final `spec.yml`**

Here is an example of a `spec.yml` file incorporating these design decisions:

```yaml
# Example my-contest-spec.yml
version: 2
# Paths to the raw materials
inputs_dir: /path/to/user/inputs
templates_dir: /path/to/pcrit/pdb
# The specific templates to include in this run
templates: [P1.prompt, P5.prompt, P12.prompt]
# Models to test against
models:
  - "openai/gpt-4o"
  - "anthropic/claude-3-sonnet"
judge_model: "openai/gpt-4o-mini"

# --- Enhancements ---
# Directory for all intermediate state to enable idempotency.
artifacts_dir: /path/to/pcrit/generations/contests/my-contest/failter-artifacts/
# Attempt each trial up to 3 times before marking it as a hard failure.
retries: 2 # (Means 1 initial attempt + 2 retries)

# The final report destination (optional).
# The same JSON will always be printed to stdout.
output_file: /path/to/pcrit/generations/contests/my-contest/report.json
```

We believe these enhancements build upon the excellent foundation of your proposal, resulting in a command-line interface that is simple to invoke, programmatically robust, and fully transparent. Our goal is to make Failter a rock-solid, "black box" component that PromptCritical can rely on.
