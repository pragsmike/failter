### **Technical Analysis: Parallel Evaluation**

## 1. The Opportunity

The `evaluate` command is the most significant performance bottleneck in the Failter workflow after the initial `experiment` run. Its execution time scales linearly with the number of trials to be evaluated.

*   **Nature of the Bottleneck:** The process is overwhelmingly **I/O-bound and latency-bound**. For each trial, the system spends the vast majority of its time waiting for a response from the LLM API. The local CPU work (reading files, formatting prompts) is trivial by comparison.
*   **Parallelization Potential:** This makes the `evaluate` step an "embarrassingly parallel" problem. Each evaluation is a completely independent task. By running these tasks concurrently, we can change the total execution time from **(Time per eval * Number of evals)** to approximately **(Time per eval * (Number of evals / Degree of parallelism))**. This would provide a dramatic speedup for large experiments.

## 2. Proposed Implementation Strategy

The idiomatic way to handle this in Clojure is to use tools from its core concurrency libraries. The `pmap` (parallel map) function is a strong candidate, but we must control the degree of parallelism to avoid overwhelming the API. A simple and robust strategy is to process the work in controlled, parallel batches.

**Core Logic Change in `failter.evaluator/run-evaluation`:**

1.  **Partition the Work:** Instead of a single `doseq` over all output files, we will first partition the list of files into smaller chunks using `(partition-all N ...)`, where `N` is our desired concurrency level (e.g., 8).
2.  **Process Chunks Sequentially:** We will iterate through these chunks one by one.
3.  **Process Items Within a Chunk in Parallel:** For each chunk, we will use `pmap` to apply the evaluation function to all items in that chunk concurrently. We will wrap the `pmap` call in `doall` to force the realization of the parallel tasks before moving to the next chunk.

### Code Transformation Example

**Current Sequential Logic (in `run-evaluation`):**
```clojure
;; ...
(doseq [output-file output-files]
  ;; ... logic to check and call evaluate-one-file ...
  )
```

**Proposed Parallel Logic:**
```clojure
(def PARALLELISM_LEVEL 8) ; Configurable concurrency level

;; ...
(let [work-chunks (partition-all PARALLELISM_LEVEL output-files)]
  (doseq [chunk work-chunks]
    (println (str "Processing a batch of " (count chunk) " files in parallel..."))
    (doall
      (pmap
        (fn [output-file]
          ;; ... logic to check and call evaluate-one-file for a single file ...
          )
        chunk))))
```

## 3. Problems, Missing Implications, and Mitigations

Implementing parallelism introduces new challenges that must be addressed.

#### **Problem 1: API Rate Limiting (High Risk)**
*   **Issue:** The biggest risk is overwhelming the upstream LLM API (e.g., OpenAI) or our LiteLLM proxy with too many simultaneous requests. This can lead to `429 Too Many Requests` errors, causing trials to fail.
*   **Mitigation:** The proposed batching strategy (`partition-all`) is the primary mitigation. By setting `PARALLELISM_LEVEL` to a sensible number (e.g., 4, 8, or 16), we can precisely control the maximum number of concurrent requests, keeping us safely within the API rate limits. This value should be a constant at the top of the namespace so it can be easily tuned.

#### **Problem 2: Interleaved Logging Output**
*   **Issue:** When multiple tasks run concurrently, their `println` outputs will be interleaved in the console. The output will no longer be a clean, sequential log for each file.
*   **Implication:** This is an expected and acceptable consequence of parallelism. It does not affect the program's correctness.
*   **Mitigation:** We must ensure all log messages are **self-contained**. Each line should include the path of the file it is referring to (e.g., `Skipping (runner failed): path/to/file.md`). Our current logging already follows this pattern, so it is well-suited for parallel execution. The user will see a stream of messages, but each one will be understandable on its own.

#### **Problem 3: Exception Handling in Parallel Tasks**
*   **Issue:** If an uncaught exception is thrown inside a task wrapped by `pmap` (or any `future`), the exception is captured. When the main thread attempts to realize the result of that task (which `doall` forces), the exception is re-thrown on the main thread, potentially halting the entire `evaluate` command.
*   **Mitigation:** The worker function (`evaluate-one-file`) must be designed to **never throw an exception**. It must have a top-level `try...catch (Exception e)` block. Our current implementation already does this, returning an error message instead of throwing. This design is robust and suitable for parallel execution.

#### **Problem 4: Resource Management**
*   **Issue:** While the tasks are I/O-bound, creating a very large number of threads can still consume significant system resources (memory for thread stacks).
*   **Mitigation:** The `partition-all` strategy also solves this. By limiting the number of active threads at any given time to `PARALLELISM_LEVEL`, we ensure that resource consumption remains predictable and constrained.

## 4. Conclusion

Running evaluators in parallel is a high-value performance optimization that is well-supported by Clojure's concurrency model. The primary risks (API rate limiting, error handling) are well-understood and can be effectively mitigated by implementing a controlled-concurrency strategy using `partition-all` and `pmap`.

This feature would significantly improve the user experience for any non-trivial experiment and should be considered a priority enhancement.
