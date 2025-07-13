# **Failter: LLM-Powered Text Filtering and Experimentation**

Failter is a command-line framework for systematically testing text transformation and filtering tasks using Large Language Models (LLMs). It serves as a powerful, idempotent experimentation harness to compare the performance of different models and prompt engineering strategies.

Instead of hard-coding filtering logic, Failter defines transformations in natural language via **prompt templates**. The entire experiment—including inputs, templates, models, and run configuration—is defined in a single **YAML specification file**. Failter handles the orchestration, execution, and reporting in a single, atomic command.

See the [USAGE.md](USAGE.md) document for a detailed user guide and the [spec format reference](./docs/spec-format.md).

See the [DESIGN.md](./docs/DESIGN.md) document for a more detailed architectural overview.

## Features

-   **Declarative Experiments:** Define your entire testing matrix (inputs, prompts, models) in a single, version-controllable YAML file.
-   **Prompt-Driven Logic:** Define complex text transformations in natural language, not code.
-   **Systematic & Idempotent:** A single `run` command orchestrates all trials, automatically skipping completed work and allowing you to safely resume failed runs.
-   **Automated Evaluation:** Use a powerful "judge" LLM (e.g., GPT-4o) to score the quality of each trial's output.
-   **Transparent Retries:** Automatically retries failed LLM calls due to transient errors, while recording the attempts as valuable reliability data.
-   **Structured JSON Output:** The primary output is a stream of clean JSON sent to `stdout`, perfect for programmatic integration with other tools.
-   **Rich, Portable Artifacts:** All intermediate results are stored in a self-contained `artifacts_dir`, making runs portable and fully auditable. Output files contain rich YAML frontmatter detailing how they were created.
-   **Insightful Diagnostics:** Captures the model's "internal monologue" (`<think>...</think>`) into separate `.thoughts` files for deeper analysis.

## Workflow

The Failter workflow is now a single, idempotent pipeline executed from the command line.

1.  **Define a `spec.yml` file:** You create a YAML file that specifies the paths to your inputs and templates, the models to test, the judge model, and an `artifacts_dir` for storing results.
2.  **Execute `failter run`:** You run a single command that points to your spec file. Failter handles the rest:
    *   It reads the spec and determines the full set of trials to run.
    *   It executes only the trials that do not already have a corresponding result in the `artifacts_dir`.
    *   It runs the judge LLM to evaluate any newly generated results.
    *   It prints a final JSON report to `stdout` summarizing the performance of all prompt-model combinations.

## Installation & Setup

1.  **Clone the Repository:**
    ```bash
    git clone https://github.com/your-username/failter.git
    cd failter
    ```

2.  **Install Dependencies:**
    You will need a working Clojure development environment. See the [official guide](https://clojure.org/guides/getting_started) for instructions.

3.  **Set up LiteLLM:**
    Failter communicates with LLMs via the [LiteLLM proxy](https://github.com/BerriAI/litellm). Install and configure it to connect to your desired LLM providers (OpenAI, Anthropic, Ollama, etc.).

4.  **Configure Environment:**
    Set the environment variable for your LiteLLM proxy API key.
    ```bash
    export LITELLM_API_KEY="your-litellm-proxy-key"
    ```

## Usage

For a detailed walkthrough, see the [USAGE.md](USAGE.md) guide.

### 1. Defining an Experiment Spec

Create a `spec.yml` file to define your run.

```yaml
# my-experiment/spec.yml
version: 2
inputs_dir: /path/to/my/inputs
templates_dir: /path/to/my/templates
artifacts_dir: ./my-experiment/artifacts # Failter will store all results here

templates: [cleanup-aggressive.md, cleanup-basic.md]
models: ["openai/gpt-4o-mini", "ollama/qwen3:8b"]
judge_model: "openai/gpt-4o"
retries: 2 # Optional: Attempt each LLM call up to 3 times
```

### 2. Running the Pipeline

All that's needed is a single command run from the project root.

```bash
clj -M:run run --spec my-experiment/spec.yml
```

### 3. Interpreting the Results

The command will print a JSON array to `stdout`. Each object in the array represents the aggregated results for a specific prompt.

**Example JSON Output:**
```json
[
  {
    "prompt_id": "cleanup-aggressive.md",
    "score": 90,
    "usage": {
      "model_used": "openai/gpt-4o-mini",
      "tokens_in": 1834,
      "tokens_out": 1790
    },
    "performance": {
      "execution_time_ms": 8120,
      "total_trial_time_ms": 8120,
      "retry_attempts": 0
    },
    "error": null
  },
  {
    "prompt_id": "cleanup-basic.md",
    "score": null,
    "usage": { ... },
    "performance": { ... },
    "error": "Trial failed: Final attempt failed: API call timed out"
  }
]
```
For deeper analysis, you can inspect the individual output files, `.eval`, and `.thoughts` files inside the specified `artifacts_dir`.
