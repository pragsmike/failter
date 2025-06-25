***

### **Revised Plan: The `failter` Experimentation Framework**

The objective is to evolve `failter` from a single-file processing tool into a robust framework for systematically testing combinations of input files, prompt templates, and LLM models.

#### **Guiding Principles**

1.  **Decoupled Orchestration:** The logic for discovering trials (`conduct-experiment`) will be separate from the logic for executing a single trial (`run-trial` function). This enables "dry runs" for testing the setup.
2.  **Idempotency & Resilience:** The experiment runner will be idempotent by default; it will skip any trial for which an output file already exists. It will also be resilient, continuing to the next trial even if one fails.
3.  **Clean Code Organization:** We will introduce new namespaces (`failter.runner`, `failter.experiment`) to logically separate the core processing logic from the experiment orchestration logic.
4.  **Robust Naming Conventions:** All file and directory names derived from parameters (like model names) will be sanitized to ensure they are valid on all filesystems.
5.  **Clear Function Signatures:** Functions will pass trial parameters as a single map, making the code clean and extensible.

---

### **Implementation Plan**

#### **Step 1: Refactor Core Logic into a Reusable `runner` Namespace**

The first step is to isolate the existing file-processing logic so it can be called programmatically by our future experiment harness.

1.  **Create a new file:** `src/failter/runner.clj`.
2.  **Define the namespace:** `(ns failter.runner ...)` with a `:require` for `failter.llm-interface`.
3.  **Create a new function `run-single-trial`:**
    *   **Signature:** `(defn run-single-trial [params])` where `params` is a map containing `:model-name`, `:template-path`, `:input-path`, `:output-path`, and an optional `:timeout`.
    *   **Logic:** Move the entire `try...catch` block from the current `failter.core/-main` into this function. It will:
        *   Read the template and input files.
        *   Format the prompt.
        *   Call `llm/call-model` with the model, prompt, and timeout.
        *   Ensure the output directory exists using `(clojure.java.io/make-parents output-path)`.
        *   Write the result to the output file.
        *   This function will be responsible for the I/O and LLM call for one specific trial.
4.  **Update `failter.core/-main`:** Modify the existing main function to become a simple wrapper. It will parse its command-line arguments, construct the `params` map, and call `(runner/run-single-trial params)`. This ensures the original command-line functionality remains intact.

**Outcome:** The core logic is now encapsulated in a testable, reusable function, and the program's external behavior has not changed.

#### **Step 2: Build the Experiment Orchestrator (`conduct-experiment`)**

Now we build the heart of the framework: the logic that discovers and orchestrates all the trials.

1.  **Create a new file:** `src/failter/experiment.clj`.
2.  **Define helper functions:**
    *   A function to sanitize strings for use as file paths (e.g., `(defn sanitize-name [s])`). This will replace characters like `/` and `:` with `_`.
    *   A function to read a list of files from a directory (e.g., `(defn list-files [dir])`).
    *   A function to read model names from `model-names.txt`.
3.  **Define `conduct-experiment`:**
    *   **Signature:** `(defn conduct-experiment [experiment-dir trial-fn])`.
    *   **Logic:**
        1.  Discover parameters: Call the helper functions to get lists of all models, template file paths, and input file paths from the `experiment-dir`.
        2.  Generate trials: Use a `for` comprehension to create a sequence of all possible trial combinations (`model` x `template` x `input`).
        3.  Inside the loop, for each combination:
            a.  Construct the sanitized output directory name (e.g., `ollama_qwen3_32b_prompt1`).
            b.  Construct the full output file path (e.g., `<exp-dir>/ollama_qwen3_32b_prompt1/one.md`).
            c.  Check if the output file already exists. If so, print a "Skipping..." message and continue to the next iteration.
            d.  Construct the trial parameter map: `{:model-name "..." :template-path "..." ...}`.
            e.  Wrap the following in a `try...catch` block to ensure one failure doesn't stop the whole run:
                i.  Call the provided `(trial-fn trial-params)`.
4.  **Create a "dry run" function:** In the same namespace, define `(defn print-trial-details [params])` that simply prints the map it receives.

**Outcome:** We can now point `conduct-experiment` at a properly structured experiment directory and, using `print-trial-details` as the `trial-fn`, verify that it correctly generates the parameters and output paths for every trial without running any LLM calls.

#### **Step 3: Connect the Orchestrator to the Runner**

This step links the orchestrator from Step 2 with the runner from Step 1 to perform a "live run".

1.  **Modify `failter.runner.clj`:**
    *   Create a new "live run" function `(defn live-trial-runner [params])`.
    *   This function's sole purpose is to call `(run-single-trial params)`. It serves as the bridge, matching the signature expected by `conduct-experiment`.

#### **Step 4: Update the Command-Line Interface (`core.clj`)**

Finally, we update the main entry point to allow the user to choose between running a single file or an entire experiment.

1.  **Modify `failter.core/-main`:**
    *   Update argument parsing logic to handle a new "subcommand" structure.
    *   **If the command is `failter experiment <dir> [--dry-run]`:**
        *   Parse the experiment directory path.
        *   Check for the optional `--dry-run` flag.
        *   Call `experiment/conduct-experiment` with the directory path.
        *   Pass `experiment/print-trial-details` as the `trial-fn` if `--dry-run` is present.
        *   Pass `runner/live-trial-runner` as the `trial-fn` otherwise.
    *   **If the command is `failter <input> <output>` (legacy mode):**
        *   Maintain the existing behavior of calling `runner/run-single-trial` directly.

**Outcome:** The `failter` program is now a complete experimentation framework, controllable from the command line.

#### **Future Vision: The Evaluator**

This plan lays the foundation for the final evaluation stage. Once trials are complete, a separate evaluator tool can be run. It will iterate through the structured output directories, and for each generated output file (e.g., `ollama_qwen3_32b_prompt1/one.md`), it will:
1.  Read the original input (`inputs/one.md`), the prompt (`templates/prompt1.md`), and the generated output.
2.  Send these three pieces of text to a powerful reasoning model.
3.  Write the model's structured evaluation (grade and rationale) to a corresponding `.eval` file (e.g., `ollama_qwen3_32b_prompt1/one.md.eval`).
