Of course. Here is a plan for implementing the `failter` command-line tool in a series of iterative and incremental steps. Each step results in a runnable, testable piece of the program.

### **Step 1: Create the Application Skeleton and Argument Parsing**

The goal of this step is to create the main entry point for the application and ensure it can correctly parse the required command-line arguments. It won't do any file I/O or LLM calls yet.

1.  **Create a new file:** `src/failter/core.clj`.
2.  **Define the namespace:** `(ns failter.core ...)` and a main function `(-main ...)`.
3.  **Implement argument parsing:** The `-main` function will take a variable number of arguments (`& args`). It should check if it receives exactly two arguments: `input-file` and `output-file`.
4.  **Add basic validation:** If the argument count is wrong, print a usage message to the console (e.g., "Usage: failter <input-file> <output-file>") and exit with a non-zero status code.
5.  **Add pre-flight check:** Call the `llm-interface/pre-flight-checks` function at the beginning of `-main` to ensure the environment is configured correctly.
6.  **Add a success stub:** If arguments are valid, just print them to the console to confirm they were parsed correctly.

**Testable Outcome:** We can run `clojure -M:run` with zero, one, or two arguments and verify that it either prints the usage message or the parsed file paths.

### **Step 2: Implement File I/O (Passthrough)**

This step will add the logic to read the input file and write to the output file, but without involving the LLM. This confirms our file handling logic is sound.

1.  **Modify `failter.core.clj`:**
2.  In the `-main` function, after parsing the arguments, use `slurp` to read the entire contents of the `input-file` into a variable.
3.  Use `spit` to write the *same content* directly to the `output-file`.
4.  Add `try...catch` blocks around the `slurp` and `spit` calls to handle potential `FileNotFoundException` or other I/O errors gracefully.
5.  Add `println` statements to indicate progress (e.g., "Reading from...", "Writing to...").

**Testable Outcome:** Running `clojure -M:run input.txt output.txt` will create `output.txt` with content identical to `input.txt`. This verifies that file reading and writing work correctly.

### **Step 3: Introduce and Test Prompt Templating**

Now, we'll integrate the prompt logic, but we will *not* call the LLM yet. Instead, we'll write the *final, combined prompt* to the output file to ensure our templating works as expected.

1.  **Modify `cleanup-prompt.md`:** Change `[Paste the markdown text here]` to `{{INPUT_TEXT}}`.
2.  **Modify `failter.core.clj`:**
3.  Hardcode the path to the prompt file for now (e.g., `"prompts/cleanup-prompt.md"`).
4.  In `-main`, read the prompt template file content using `slurp`.
5.  Use `clojure.string/replace` to substitute the `{{INPUT_TEXT}}` token in the prompt template with the content of the input file read in Step 2.
6.  Instead of writing the original input to the output file, now `spit` the *combined prompt string* to the `output-file`.

**Testable Outcome:** Running `clojure -M:run input.txt output.txt` will result in `output.txt` containing the full text of `cleanup-prompt.md`, with the contents of `input.txt` appended at the end. This verifies that our prompt assembly logic is correct.

### **Step 4: Integrate the LLM Call**

This is the central step where we connect all the pieces. We'll replace the debug output from Step 3 with an actual call to the LLM.

1.  **Modify `failter.core.clj`:**
2.  Add a `:require` for `failter.llm-interface`.
3.  Hardcode the model name for now (e.g., `"gpt-4o"` or whatever model you have configured in LiteLLM).
4.  In `-main`, take the combined prompt string from Step 3 and pass it to `(llm-interface/call-model model-name prompt-string)`.
5.  Take the string *returned* by `call-model`.
6.  `spit` this LLM response to the `output-file`.
7.  **Add error handling:** The `call-model` function returns a JSON string with an `:error` key on failure. Check if the returned string contains `:error` and, if so, print an error message and exit instead of writing to the output file.

**Testable Outcome:** Running `clojure -M:run input.txt output.txt` will now execute the full workflow. It will send the templated prompt to the LLM and save the cleaned-up markdown response to `output.txt`.

### **Step 5: Make Model and Prompt Configurable**

The hardcoded model and prompt path limit the tool's flexibility. Let's make them command-line arguments.

1.  **Modify `failter.core.clj`:**
2.  Update the `-main` function's argument parsing logic to accept four arguments: `model-name`, `prompt-file`, `input-file`, `output-file`.
3.  Update the usage message to reflect the new arguments.
4.  Replace the hardcoded model name and prompt file path with the values from the parsed arguments.

**Testable Outcome:** We can now run the tool with different models and prompt files, for example:
`clojure -M:run "gpt-4o" "prompts/cleanup-prompt.md" "input.md" "output.md"`
`clojure -M:run "claude-3-haiku" "prompts/summarize-prompt.md" "long-article.txt" "summary.txt"`

This plan moves from a simple skeleton to a fully functional and configurable utility, with verification possible at each stage.

I am ready to start implementing Step 1.
