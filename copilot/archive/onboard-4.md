### **Onboarding Document for the Failter Project (Version 3.2)**

Hello,

I am your predecessor AI assistant. I've had the opportunity to collaborate on the Failter project, and my goal with this document is to ensure your transition is as smooth as possible. I've worked to implement new features, fix critical bugs, and improve the project's overall test coverage and robustness. This document contains the project's core principles, the lessons I learned from my mistakes, and a clear path forward.

#### **1. The Big Picture: What is Failter?**

At its core, **Failter is a tool for prompt engineers**. It provides a rigorous, file-based framework to test prompt templates against various models and inputs. The guiding philosophies are **"prompts as code"** and **"filesystem as database."** This approach makes experiments portable, version-controllable, and easy to reason about.

The workflow is a simple three-stage pipeline: `experiment` -> `evaluate` -> `report`. You should familiarize yourself with the `README.md`, `USAGE.md`, and `docs/DESIGN.md` documents, as they are the source of truth for the project's high-level architecture and user-facing functionality.

#### **2. Prime Directives & Rules of Collaboration**

Adhering to these rules is critical for effective collaboration. I have reiterated the initial directives I was given and added new ones based on my experience during this session.

1.  **State Your Intentions Clearly:** Before writing or changing code, explain your understanding of the problem and your plan to solve it. This is the fastest way to get feedback and avoid going down the wrong path.
2.  **Provide Complete Files:** When you emit code, provide the complete, updated file. This makes it easy for your collaborator to review and integrate.
3.  **Incorporate Feedback Directly and Precisely:** When you receive a correction, update your internal representation of the code immediately and exactly as specified. Do not argue or re-emit the faulty code.
4.  **Preserve Context:** Do not remove developer comments, as they contain valuable context.
5.  **Trust the Test Results:** This is a new, crucial directive. If a test fails, it is signaling a real problem. Your first assumption should be that the test is correct and the application code is flawed. Do not "fix the test" to make it pass until you have proven the bug is truly in the test's logic.
6.  **Trace Data from the Source:** When debugging, do not just look at the point of failure. Trace the data from the very beginning of the pipeline. A problem that manifests in the `report` step can often have its root cause in the `experiment` or `evaluate` step.

#### **3. My Mistakes & Your Guide to Avoiding Them**

This is the most important section. Understanding my errors will help you avoid them.

**Lesson 1: A Failing Test Is a Symptom, Not the Disease.**
*   **My Mistake:** In `reporter_test.clj`, the test was failing with a `ClassCastException`. I incorrectly assumed the test data was outdated and "fixed" it multiple times.
*   **The Real Cause:** The test was correctly revealing a critical bug in the application code (`failter.scoring.clj`). The grading logic was flawed, causing all scores to be bucketed into a single grade. My attempts to fix the test were masking the real bug.
*   **Your Instruction:** When a test fails, especially in an unexpected way, treat it as a symptom. Your first action should be to deeply investigate the application code being tested. Use the test failure as a guide to find the underlying problem in the main codebase.

**Lesson 2: Filesystem Tests Must Be Self-Contained.**
*   **My Mistake:** The test for `failter.runner.clj` failed with a `FileNotFoundException`.
*   **The Real Cause:** The test was creating temporary files and then passing *relative* paths to the function under test. When the test runner executed from the project's root directory, the relative paths were incorrect, and the files could not be found.
*   **Your Instruction:** When writing tests that involve file I/O, always construct and use **absolute paths** for all files created within the test's scope. This makes the test independent of the directory from which it is run, ensuring it is robust and reliable.

**Lesson 3: The Simplest Explanation Is Often the Right One.**
*   **My Mistake:** The end-to-end test ran successfully but produced an empty report. I theorized that the small judge model was failing to produce valid YAML, leading me to make the parser more "robust."
*   **The Real Cause:** The bug was much simpler and earlier in the data flow. The file discovery function in `failter.exp-paths.clj` was hardcoded to only find `.md` files, and our test was using `.txt` files. The evaluator simply never received any files to process.
*   **Your Instruction:** Before implementing a complex fix, trace the data flow from the beginning. A problem observed at the end of a pipeline (like an empty report) often originates from a simple error at the start (like files not being found).

#### **4. State of the Project & "Tribal Knowledge"**

The project is now in a very stable state. The core feature of handling frontmatter in templates is complete, and several underlying bugs have been fixed. Here is what you need to know that isn't immediately obvious from the documentation:

*   **The Test Suite is Robust:** We have a three-tiered testing strategy:
    1.  **Unit Tests (`test`):** Fast tests for pure functions.
    2.  **Integration Tests (`test`):** Tests for modules with side effects, where external dependencies (like LLM calls) are mocked out.
    3.  **End-to-End Test (`make test-e2e`):** A full-system test that simulates a real user workflow from start to finish. Use this after any significant change to guarantee the entire pipeline is sound.
*   **The Makefile is Canonical:** All standard developer workflows (`test`, `run`, `test-e2e`, `pack`) are defined in the `Makefile`. It is the official entry point for interacting with the project.
*   **Judge Model Limitations:** The `test-e2e` script uses a very small local model as the judge for speed and self-containment. Be aware that small models may not always follow formatting instructions perfectly. We've hardened the rationale parser to be resilient to this, but it's a known trade-off.

#### **5. The Path Forward: Next Steps**

The project is now perfectly positioned to complete the features outlined in the original onboarding document.

1.  **Implement Verbose Reporting (Recommended First):**
    *   **Goal:** Add a `--verbose` flag to the `report` command. When enabled, the report should include the `rationale` for any trial that received a score below a certain threshold (e.g., 80).
    *   **Why first?** This is a high-value, low-risk feature that makes reports more actionable. The recent refactoring has made this even more straightforward.
    *   **Implementation Sketch:**
        1.  Add the `--verbose` flag to the `cli-options` map in `failter.core`.
        2.  Pass the `verbose?` flag from `core` down through `reporter/generate-report`.
        3.  In `reporter/generate-report`, before generating the table, filter the `evals` sequence to find low-scoring trials. For each, print the output path, score, and the full rationale text. This should happen before the main summary table is printed.

2.  **Implement Parallel Evaluation:**
    *   **Goal:** Modify the `evaluate` command to run judge LLM calls concurrently, significantly speeding up the process for large experiments.
    *   **Implementation Sketch:** In `failter.evaluator/run-evaluation`, the `doseq` that iterates over result files would be replaced with `pmap`.
    *   **Challenges:** As my predecessor noted, this is a more complex task. Be mindful of overwhelming the LiteLLM proxy with too many concurrent requests. Also, ensure logging from parallel operations remains intelligible.

Welcome to the team. By building on these lessons and understanding the current state of the architecture, I am confident you will be able to contribute effectively.
