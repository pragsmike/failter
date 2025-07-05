Of course. I have analyzed the request and the provided specification for the new prompt file format.

### Problem to be Solved

My understanding of the problem is that we need to update Failter to correctly handle a new, extended format for its `template` files. Currently, Failter assumes these template files are plain text, where the entire content is the prompt.

The new requirement is that these template files may now optionally contain a YAML frontmatter block at the beginning, delimited by `---` lines. Failter's task is to intelligently parse these files:

1.  If a YAML frontmatter block exists, it must be identified and its content **ignored**. The "prompt" is now only the body text that comes *after* the closing `---` delimiter.
2.  If the file does not contain a frontmatter block (the old format), it must be treated as it is today, with the entire file content considered the prompt.

The core of the task is to make Failter's file reading logic for prompt templates robust enough to handle both formats seamlessly, ensuring that only the executable prompt text is used in the `experiment` and `evaluate` stages. The metadata within the frontmatter is irrelevant to Failter's logic at this time.

### Code to be Changed

The change is localized to the specific points in the code where prompt template files are read from the disk and their content is used. Based on my analysis of the codebase, two key locations must be modified:

1.  **`failter.runner/run-single-trial`**: This is the function that executes a single trial. It currently reads the template file with a simple `(slurp template-path)`. This line must be updated to first slurp the file, then parse the content to separate the frontmatter from the body, and finally use only the body as the `prompt-template`.

2.  **`failter.evaluator/build-evaluation-context`**: This function prepares the data needed for the judge LLM during the `evaluate` step. It also reads the template file via `(slurp full-template-path)` to include its content in the context for the judge. This logic must also be changed to parse the file and use only the body text as the `template-content`.

Fortunately, the project already contains a `failter.frontmatter/parse-file-content` function that does exactly what is required. It can parse a string, identify YAML frontmatter, and return a map containing both `:frontmatter` and `:body`. Crucially, if no frontmatter is found, it correctly returns the entire original string as the `:body`. This existing utility is perfect for the job and can be reused directly in both locations.

### Plan for Implementation and Testing

Here is my proposed plan to implement and validate this change:

**Phase 1: Code Implementation**

1.  **Modify `failter.runner`**:
    *   In the `run-single-trial` function, replace the line `prompt-template (slurp template-path)` with logic that uses `failter.frontmatter/parse-file-content` to extract the `:body` of the template file.

2.  **Modify `failter.evaluator`**:
    *   In the `build-evaluation-context` function, replace the line `template-content (slurp full-template-path)` with the same logic to parse the file content and use only the `:body`.

**Phase 2: Testing and Validation**

To ensure the change is working correctly and has not introduced any regressions, I will create a dedicated test experiment.

1.  **Create Test Artifacts:**
    *   **New Format Test Case:** Create a new template file, `test-experiment/templates/template-with-fm.md`, that includes a YAML frontmatter block. The body will contain a simple instruction and the `{{INPUT_TEXT}}` placeholder.
        ```
        ---
        id: "P123"
        description: "A test prompt with frontmatter."
        ---
        This is the body. The input is: {{INPUT_TEXT}}
        ```
    *   **Backward Compatibility Test Case:** Create a second template, `test-experiment/templates/template-no-fm.md`, in the old format (plain text with no `---` delimiters).
        ```
        This is the old format. The input is: {{INPUT_TEXT}}
        ```
    *   **Input File:** Create a simple input file, `test-experiment/inputs/sample.txt`.
    *   **Configuration:** Create `test-experiment/model-names.txt` listing a single, fast model to speed up testing.

2.  **Execute the Test Plan:**
    *   Run the full Failter pipeline on the `test-experiment` directory:
        1.  `clj -M:run experiment test-experiment`
        2.  `clj -M:run evaluate test-experiment`
        3.  `clj -M:run report test-experiment`

3.  **Verify the Results:**
    *   **Check Experiment Output:** Examine the generated files in the `test-experiment/results/` directory.
        *   The output file generated using `template-with-fm.md` must contain the body "This is the body. The input is: [content of sample.txt]" and its own frontmatter written by the runner. The frontmatter from the template file should be absent from the body.
        *   The output file generated using `template-no-fm.md` must be processed correctly, confirming that backward compatibility is maintained.
    *   **Check Evaluation Output:** Inspect the `.eval` and `.thoughts` files. The `PROMPT_TEMPLATE` context provided to the judge model must only contain the body of the prompt template, not its frontmatter. This confirms the second code change in the `evaluator` is working as intended.
    *   The final report should show successful runs for both template types, with no errors.

This plan will allow us to implement the feature cleanly using existing project utilities and thoroughly verify its correctness and resilience.
