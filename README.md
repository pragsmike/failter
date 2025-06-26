# Failter: LLM-Powered Text Filtering and Experimentation

Failter is a command-line framework for systematically filtering text using Large Language Models (LLMs). It serves as a powerful experimentation harness to compare the performance of different models and prompt engineering strategies for specific text transformation tasks.

Instead of hard-coding filtering logic, Failter defines transformations in natural language via **prompt templates**. It then automates the process of running these transformations across various models and input files, evaluating the results with a "judge" LLM, and generating comparative reports.

  <!-- Placeholder for a future workflow diagram -->

## Features

-   **Prompt-Driven Logic:** Define complex text transformations in natural language, not code.
-   **Systematic Experimentation:** Run trials across all combinations of inputs, prompts, and models automatically.
-   **Automated Evaluation:** Use a powerful "judge" LLM (e.g., GPT-4o) to grade the quality of each trial's output.
-   **Comprehensive Reporting:** Generate summary reports that rank `model-template` combinations by average score, time, and cost.
-   **Resilient & Idempotent:** Automatically skips completed work and isolates failures, allowing you to resume long-running experiments.
-   **Data-Rich Artifacts:** Output files are self-contained with YAML frontmatter detailing how they were created, including performance metrics and any errors.
-   **Insightful Diagnostics:** Captures the model's "internal monologue" (`<think>...</think>`) into separate `.thoughts` files for deeper analysis.

## Workflow

The Failter workflow is a three-step pipeline executed from the command line:

1.  **`experiment`**: Kicks off the process. Failter reads your experiment configuration, runs each trial (input + prompt + model), and generates filtered output files in a structured `results/` directory.
2.  **`evaluate`**: Assesses the quality of the generated outputs. It uses a powerful "judge" LLM to compare the original text with the filtered output based on the prompt's instructions, saving a grade and rationale for each.
3.  **`report`**: Synthesizes the results. It scans all trial outputs and their evaluations, automatically assigning an 'F' grade to any failed trials, and generates a summary table ranking the performance of each model-prompt combination.

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

### 1. Defining an Experiment

Create an experiment directory with the following structure:

```
my-experiment/
├── inputs/
│   ├── article1.md
│   └── article2.md
├── templates/
│   ├── cleanup-basic.md
│   └── cleanup-aggressive.md
└── model-names.txt
```

-   `inputs/`: Contains the source files you want to transform.
-   `templates/`: Contains prompt templates. Each template must include the token `{{INPUT_TEXT}}` where the input content should be injected.
-   `model-names.txt`: A plain text file listing the LLM models you want to test, one per line (e.g., `ollama/qwen3:8b`, `openai/gpt-4o-mini`).

### 2. Running the Pipeline

All commands are run from the project root.

**Step 1: Run the Experiment**
This will generate the filtered files in `my-experiment/results/`.

```bash
# Perform a dry run first to verify the setup
clj -M:run experiment my-experiment --dry-run

# Execute the live run
clj -M:run experiment my-experiment
```

**Step 2: Evaluate the Results**
This will create `.eval` files alongside the outputs in the `results/` directory.

```bash
clj -M:run evaluate my-experiment
```
*Optional: Use a specific judge model:*
```bash
clj -M:run evaluate my-experiment --judge-model openai/gpt-4o
```

**Step 3: Generate the Report**
This will print a summary table of all trial results to your console.

```bash
clj -M:run report my-experiment
```

**Example Report Output:**
```
Model_Template_Combination          | Avg Score  | Avg Time(s) | Avg Cost   | Trials  | Errors | Grade Distribution
---------------------------------------------------------------------------------------------------------------------
openai-gpt-4o-mini_aggressive       | 4.50       | 8.12        | $0.000152  | 2       | 0      | {"A" 1, "B" 1}
ollama-qwen3-8b_aggressive          | 3.00       | 45.19       | $0.000000  | 2       | 0      | {"C" 2}
ollama-qwen3-8b_basic               | 2.50       | 42.88       | $0.000000  | 2       | 0      | {"B" 1, "D" 1}
openai-gpt-4o-mini_basic            | 1.00       | 60.01       | $0.000000  | 2       | 2      | {"F" 2}
```

## How It Works

Failter's architecture is built on a few key design principles:

-   **Filesystem as Database:** The entire state of an experiment is stored in its directory, making it portable and easy to version control.
-   **Decoupled Logic:** The code is organized into modular namespaces for orchestration, execution, evaluation, and reporting. This makes the system easy to understand and extend.
-   **Self-Describing Artifacts:** Each output file's YAML frontmatter contains a complete record of its creation, including the model, template, execution time, token usage, and cost.

See the [docs/DESIGN.md](DESIGN) document for a more detailed architectural overview.

## Contributing

Contributions are welcome! Please feel free to open an issue to discuss a new feature or submit a pull request.
