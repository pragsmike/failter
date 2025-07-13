That's an excellent question. It moves us from just *running* experiments to *understanding* them, which is a key part of the project's mission. The cost of LLM calls is a critical metric.

The good news is that the system is already designed to capture this data. The `failter` tool, when it runs an evaluation, records the cost of each prompt run and includes it as a `cost` column in the final `report.csv`. Our `reports` component already parses this into a numeric value.

Currently, we are simply not displaying this information to the user. We can expose it in several ways, ranging from simple enhancements to new features. Here are the options, from easiest to most powerful.

### Option 1: Log Costs at the End of Commands (Low-hanging Fruit)

We can enhance the existing commands to calculate and log total costs upon completion.

#### A. Enhance the `evaluate` command:

This is the most impactful place to add cost reporting, as `evaluate` is the most expensive operation. After the `failter` toolchain finishes and `report.csv` is generated, the `evaluate!` function can immediately parse that report, sum the `cost` column, and log the total.

*   **Implementation:**
    1.  In `pcrit.command.evaluate/evaluate!`, after the `failter/run-contest!` call succeeds, it receives the path to the report.
    2.  Add a call to `(reports/parse-report report-path)`.
    3.  Calculate `(reduce + (map :cost parsed-data))`.
    4.  Add a `log/info` statement like: `Contest 'web-cleanup-v1' completed. Total cost: $1.2345`.
*   **Pros:** Very easy to implement. Provides immediate, valuable feedback right where the user would expect it.
*   **Cons:** Only shows the total at the end, not a running total.

#### B. Enhance the `vary` command:

The `vary!` command also makes LLM calls. We can update it to track and log the cost of creating new prompt variations.

*   **Implementation:**
    1.  The `llm/call-model` function already returns a map containing the `:cost`.
    2.  In `pcrit.command.vary/vary!`, the `apply-meta-prompt` helper would need to return the full response map, not just the content.
    3.  The main loop would then collect these costs and sum them.
    4.  Add a `log/info` statement at the end: `Vary command finished. Created 10 new offspring. Total variation cost: $0.0789`.
*   **Pros:** Provides cost visibility for the "breeding" step of the evolution.
*   **Cons:** Slightly more complex than the `evaluate` change, as it requires modifying the data flow within the `vary!` function.

### Option 2: Create a New `stats` Command (Most User-Friendly)

This is a more powerful, dedicated solution. We could create a new command, `pcrit stats`, for analyzing completed runs.

*   **How it would work:**
    ```bash
    # Show stats for a specific contest
    pcrit stats my-experiment/ --from-contest "web-cleanup-v3"

    # Show stats for an entire generation (aggregating all its contests)
    pcrit stats my-experiment/ --generation 2
    ```
*   **Example Output:**
    ```
    Stats for contest: web-cleanup-v3
    -----------------------------------
    Prompts evaluated:   50
    Total Cost:          $2.4567
    Average Cost:        $0.0491
    Highest Score:       0.98 (P42)
    Lowest Score:        0.65 (P18)
    ```
*   **Implementation:**
    1.  Create a new `pcrit.command.stats` namespace.
    2.  The `stats!` function would take the context and options.
    3.  It would use `expdir` functions to find the correct `report.csv` file(s).
    4.  It would use `reports/parse-report` to read the data.
    5.  It would calculate various statistics (sum, average, min, max for both score and cost).
    6.  It would format and print a clean summary table.
*   **Pros:** Extremely flexible and powerful. Separates analysis from execution. Users can check costs of old experiments at any time.
*   **Cons:** It is a new feature and requires more implementation work than simply adding logging.

### Option 3: Integrate Cost into Selection (Advanced Feature)

We could make cost a first-class citizen in the evolutionary process itself by creating a new selection policy.

*   **How it would work:**
    ```bash
    pcrit select my-exp --from-contest "v3" --policy "efficiency-top-N=5"
    ```
*   **Implementation:**
    1.  Using the `multimethod` we just created, add a new `defmethod` for `:efficiency-top-n`.
    2.  This method would calculate a new metric for each prompt, like `efficiency = :score / :cost`.
    3.  It would then select the top N prompts based on this new efficiency metric.
*   **Pros:** Directly aligns with the project's goal of finding "high-performance" prompts, where performance can be defined as a balance of quality and cost.
*   **Cons:** This is a change to the core evolutionary strategy, not just a reporting feature.

### Recommendation

1.  **Start with Option 1A (Enhance `evaluate`).** It's a quick win that provides the most critical cost information with minimal effort.
2.  **Then, implement Option 2 (the `stats` command).** This is the best long-term solution for user-facing reporting and analysis.
3.  **Hold off on Option 3 for now.** It's a great idea for a future milestone (`v0.4` or later) once the basic cost reporting is in place.

I am ready to proceed with Option 1A. Shall I modify the `evaluate` command to log the total cost?
