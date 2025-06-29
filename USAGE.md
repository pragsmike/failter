# **User Guide: Optimizing Prompts with Failter**

This guide explains how to use the **Failter** command-line tool to systematically test, evaluate, and optimize your prompts. It is written for developers and AI assistants who need to derive fitness scores for prompt evolution.

## 1. Core Purpose

Failter is an experimentation framework designed to help you answer questions like:

*   "Which of my prompt variations performs best for this text transformation task?"
*   "Which LLM model is the most cost-effective and performant for my specific use case?"
*   "How consistently does my prompt perform across a wide range of input documents?"
*   "How close is my prompt's output to a 'perfect,' human-verified example?"

It automates the tedious process of running numerous trials, evaluating the results using a flexible scoring system, and summarizing them so you can focus on making data-driven decisions to improve your prompts.

## 2. The Experiment Workflow

The Failter workflow is a three-step pipeline executed from the command line:

1.  **`experiment`**: You provide a set of inputs, prompt templates, and models. Failter runs every possible combination and generates the filtered output files.
2.  **`evaluate`**: Failter uses a powerful "judge" LLM (e.g., GPT-4o) to automatically review and score the quality of each output file from the previous step.
3.  **`report`**: Failter gathers all metadata, performance metrics, and evaluation scores into a comprehensive summary report, delivered as both a markdown table and a CSV file.

## 3. Setting Up Your Experiment

First, create a single directory for your experiment. Inside this directory, you must create the following structure:

```
my-prompt-test/
├── inputs/
│   ├── article_to_clean.md
│   └── another_document.txt
├── templates/
│   ├── prompt_v1.md
│   └── prompt_v2_with_cot.md
├── ground_truth/         <-- Optional but recommended
│   ├── article_to_clean.md
│   └── another_document.txt
└── model-names.txt
```

#### `inputs/` directory
Contains all the source files you want to test your prompts against. Having multiple, diverse inputs is crucial for testing the consistency and reliability of your prompts.

#### `templates/` directory
Contains your different prompt variations. Each file is a complete prompt template.
*   **Crucially**, each template file **must** contain the placeholder token `{{INPUT_TEXT}}`. Failter will replace this token with the content of an input file during a trial.

#### `model-names.txt` file
A simple text file listing the LLM models you want to test, one per line. These names must match those configured in your [LiteLLM proxy](https://github.com/BerriAI/litellm) (e.g., `ollama/qwen3:8b`, `openai/gpt-4o-mini`).

#### `ground_truth/` directory (Optional)
This directory enables a more objective, powerful evaluation method.
*   It contains human-verified, "perfect" versions of the output files.
*   **Naming Convention:** For every file in `inputs/`, there should be a corresponding file in `ground_truth/` with the **exact same name**.
*   If a ground truth file is present for an input, the `evaluate` step will use it as a gold standard for comparison, resulting in a more objective `ground-truth` evaluation method. If it's absent, Failter seamlessly falls back to `rules-based` evaluation for that trial.

## 4. Running the Pipeline

All commands are run from the project root.

#### Step 1: Execute the Experiment
This command creates a `results/` directory inside your experiment folder and populates it with the filtered output from each trial.

```bash
# We strongly recommend a "dry run" first to verify your setup
clj -M:run experiment path/to/my-prompt-test --dry-run

# Once verified, run the live experiment
clj -M:run experiment path/to/my-prompt-test
```

#### Step 2: Evaluate the Results
This command creates `.eval` files containing a score and rationale for each successful trial.

```bash
# Use a powerful, cost-effective model as the judge
clj -M:run evaluate path/to/my-prompt-test --judge-model openai/gpt-4o-mini
```

#### Step 3: Generate the Final Report
This is the final step. It prints a summary table to your screen and also saves `report.md` and `report.csv` inside your experiment directory.

```bash
clj -M:run report path/to/my-prompt-test
```

## 5. Interpreting the Results for Prompt Evolution

After running the full pipeline, analyze the generated `report.md` to determine the "fitness" of your prompt variations.

**Example Report (`report.md`):**
(This example assumes the default `:letter-grade` scoring strategy is active.)
```
Model              | Template               | Avg Score  | Avg Time(s) | Avg Cost   | Trials  | Errors | Eval Methods               | Score Distribution
----------------------------------------------------------------------------------------------------------------------------------------------------------
openai/gpt-4o-mini | prompt_v2_with_cot.md  | 92.00      | 5.21        | $0.00015   | 10      | 0      | 10 ground-truth            | {"A" 8, "B" 2}
openai/gpt-4o-mini | prompt_v1.md           | 88.00      | 4.95        | $0.00014   | 10      | 0      | 4 ground-truth, 6 rules-based | {"A" 5, "B" 5}
ollama/qwen3:8b    | prompt_v2_with_cot.md  | 66.00      | 35.80       | $0.00000   | 10      | 1      | 1 failed, 9 ground-truth   | {"B" 2, "C" 7, "F" 1}
```

### How to Make Decisions:

1.  **`Avg Score`:** The primary metric for quality, **normalized to a 0-100 scale**. Higher is better. Here, `prompt_v2_with_cot.md` has the highest average score.

2.  **`Score Distribution`:** The most important column for understanding **consistency**. Its format depends on the active `:scoring-strategy` in Failter's configuration.
    *   **With `:letter-grade` (default):** The distribution is shown as `{"A" 8, "B" 2}`. A prompt that scores `{"A" 8, "B" 2}` is far more reliable than one that scores `{"A" 5, "F" 5}`, even if their average scores are similar. Look for tight groupings in the higher grades.
    *   **With `:numeric-100`:** The distribution might be shown as `{"90s" 8, "80s" 2}`. This shows the count of scores falling into different numeric buckets. The principle is the same: look for consistent, high scores.

3.  **`Eval Methods`:** This tells you how the scores were derived. A score from a `ground-truth` evaluation is generally more reliable and objective than one from `rules-based`. This column helps you trust your results.

4.  **`Avg Time(s)` / `Avg Cost`:** Critical metrics for production viability. Is the higher score from `prompt_v2` worth the slightly increased time and cost? Failter gives you the data to make that trade-off.

5.  **`Errors`:** A high error count indicates that a specific model may be unstable or incompatible with your prompt, a key factor in selecting a production model.

### Deeper Analysis:

For any surprising or low-scoring trials, you can drill down into the artifacts in the `results/` directory:
*   **The Output File (`.md`):** Inspect the generated text directly. The YAML frontmatter contains detailed performance metrics for that specific run.
*   **The Thoughts File (`.thoughts`):** If the model produced an "internal monologue" (e.g., inside `<think>...</think>` tags), it is saved here. This is extremely useful for debugging *why* a model is misinterpreting your prompt.
*   **The Evaluation File (`.eval`):** This contains the structured output from the judge model, including the raw `score` and a specific `rationale` explaining what the model did right or wrong.

By analyzing the report and drilling down into the artifacts, you can form a new hypothesis, create a `prompt_v3.md`, and repeat the process, iteratively evolving your prompts based on quantitative evidence.
