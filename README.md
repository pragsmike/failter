# **Failter: LLM-Powered Text Filtering and Experimentation**

Failter is a command-line framework for systematically filtering text using Large Language Models (LLMs). It serves as a powerful experimentation harness to compare the performance of different models and prompt engineering strategies for specific text transformation tasks.

Instead of hard-coding filtering logic, Failter defines transformations in natural language via **prompt templates**. It then automates the process of running these transformations across various models and input files, evaluating the results with a "judge" LLM, and generating comparative reports.

See the [USAGE.md](USAGE.md) document for a detailed user guide.

See the [DESIGN.md](./docs/DESIGN.md) document for a more detailed architectural overview.

## Features

-   **Prompt-Driven Logic:** Define complex text transformations in natural language, not code.
-   **Systematic Experimentation:** Run trials across all combinations of inputs, prompts, and models automatically.
-   **Automated Evaluation:** Use a powerful "judge" LLM (e.g., GPT-4o) to score the quality of each trial's output.
-   **Flexible Scoring:** The evaluation system is pluggable. Define custom scoring strategies (e.g., letter grades, 0-100 numeric scores) that can be changed without altering core logic.
-   **Comprehensive Reporting:** Generate summary reports that rank model-prompt combinations by average score, time, and cost, saved as both Markdown and CSV.
-   **Resilient & Idempotent:** Automatically skips completed work and isolates failures, allowing you to resume long-running experiments.
-   **Data-Rich Artifacts:** Output files are self-contained with YAML frontmatter detailing how they were created, including performance metrics and any errors.
-   **Insightful Diagnostics:** Captures the model's "internal monologue" (`<think>...</think>`) into separate `.thoughts` files for deeper analysis.

## Workflow

The Failter workflow is a three-step pipeline executed from the command line:

1.  **`experiment`**: Kicks off the process. Failter reads your experiment configuration, runs each trial (input + prompt + model), and generates filtered output files in a structured `results/` directory.
2.  **`evaluate`**: Assesses the quality of the generated outputs. It uses a powerful "judge" LLM to compare the original text with the filtered output based on the prompt's instructions, saving a score and rationale for each.
3.  **`report`**: Synthesizes the results. It scans all trial outputs and their evaluations, automatically assigning a score of 0 to any failed trials, and generates a summary report as both `report.md` and `report.csv`.

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

### 1. Defining an Experiment

Create an experiment directory with `inputs/`, `templates/`, and a `model-names.txt` file.

### 2. Running the Pipeline

All commands are run from the project root.

**Step 1: Run the Experiment**```bash
# Perform a dry run first to verify the setup
clj -M:run experiment my-experiment --dry-run

# Execute the live run
clj -M:run experiment my-experiment
```

**Step 2: Evaluate the Results**
```bash
clj -M:run evaluate my-experiment
```

**Step 3: Generate the Report**
```bash
clj -M:run report my-experiment
```

### 3. Interpreting the Results

After running the full pipeline, your experiment directory will contain the generated `results/` directory and the final reports.

**Example Report Output (`report.md`):**
(This example uses the default `:letter-grade` scoring strategy)
```
Model                               | Template             | Avg Score  | Avg Time(s) | Avg Cost   | Trials  | Errors | Score Distribution
--------------------------------------------------------------------------------------------------------------------------------------------
openai/gpt-4o-mini                  | cleanup-aggressive.md| 90.00      | 8.12        | $0.000152  | 2       | 0      | {"A" 1, "B" 1}
ollama/qwen3:8b                     | cleanup-aggressive.md| 84.00      | 35.80       | $0.000000  | 2       | 0      | {"A" 1, "B" 1}
ollama/qwen3:8b                     | cleanup-basic.md     | 50.00      | 42.88       | $0.000000  | 2       | 0      | {"B" 1, "D" 1}
openai/gpt-4o-mini                  | cleanup-basic.md     | 20.00      | 60.01       | $0.000000  | 2       | 2      | {"F" 2}
```

-   **Avg Score:** The primary quality metric, normalized to a **0-100** scale. Higher is better.
-   **Score Distribution:** Shows the consistency of the results. Its format depends on the active scoring strategy (e.g., letter grades, numeric buckets). A tight grouping of high scores is better than a wide spread.
-   For detailed analysis, inspect the individual `.eval` and `.thoughts` files in the `results/` subdirectories.

## How It Works

Failter's architecture is built on a few key design principles:

-   **Filesystem as Database:** The entire state of an experiment is stored in its directory, making it portable and easy to version control.
-   **Decoupled Logic:** The code is organized into modular namespaces. The "policy" of how to score an evaluation is defined in the `failter.scoring` namespace, decoupled from the "mechanism" of running the evaluation in `failter.evaluator`. This makes the system easy to understand and extend.
-   **Self-Describing Artifacts:** Each output file's YAML frontmatter contains a complete record of its creation, including the model, template, execution time, token usage, and cost.

See the [DESIGN.md](./docs/DESIGN.md) document for a more detailed architectural overview.
