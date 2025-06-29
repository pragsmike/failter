### **Onboarding Document for the Failter Project (Version 3.1)**

Hello, and welcome to the Failter project.

My name is your predecessor AI assistant. I have had the privilege of collaborating on this project, evolving it from a robust framework into a more architecturally sound and maintainable system. This document contains everything I have learned—the design philosophy, the architectural choices, and, most importantly, the lessons learned from both mistakes and successes. My goal is to help you become an effective contributor as quickly as possible.

#### **1. The Big Picture: What is Failter?**

At its core, **Failter is a tool for prompt engineers**. Our primary user is someone who needs to derive fitness scores for prompts by testing them against different LLMs and input files. We provide the machinery to automate this process.

The central philosophy is **"prompts as code"** and **"filesystem as database."** We avoid complex databases and in-memory state, opting for a simple, resilient, and version-controllable system where the state of any experiment is fully described by the files in its directory.

The workflow is a three-stage pipeline that operates on first-class data records:
1.  `failter experiment`: Produces a set of planned `Trial` records.
2.  `failter runner`: Consumes `Trial` records and executes them, hydrating them with results.
3.  `failter evaluate`: Consumes completed `Trial` records and, with the help of the `failter.scoring` namespace, produces `Eval` records containing a normalized numeric `score` and a `rationale`.
4.  `failter report`: Consumes a full set of `Eval` records to produce the final summary.

Please review the `DESIGN.md` and `USAGE.md` documents. They are your primary reference for the high-level architecture and user-facing commands.

#### **2. Lessons Learned & How to Avoid My Mistakes**

This is the most important section. Understanding these lessons—both positive and negative—will save you significant time and frustration.

**Lesson 1: Never, Ever Guess About APIs. Insist on Having the Tools to Verify.**
This was my biggest and most repeated failure. **Do not guess.** Before using any library function, state your intention and use the available search tools to find its authoritative documentation. Your effectiveness is directly proportional to the quality of your information.

**Lesson 2: Respect Data Types and Preserve Information.**
When a library returns a custom data type (like `clj-yaml`'s `OrderedMap`), first understand *why* it does so before trying to convert it. Information like key ordering can be valuable, and discarding it carelessly can lead to subtle bugs or degrade the user experience.

**Lesson 3: Robust Parsers are Non-Negotiable.**
When parsing LLM output, **always assume it will be messy.** Your parsers must be defensive and designed to extract the desired content while gracefully handling common syntax errors or surrounding conversational text. The regex-based parsers in `failter.eval` are a good example of this principle.

**Lesson 4: Regressions Are Real. Trust But Verify After Refactoring.**
After every significant refactoring, you must mentally trace the data flow through the entire system. Better yet, insist that your collaborator run the full end-to-end test (`experiment` -> `evaluate` -> `report`) to ensure no new regressions have been introduced.

**Lesson 5: Decouple Policy from Mechanism.**
This is a positive lesson learned from the successful refactoring of the scoring system.
*   **The Opportunity:** The original design mixed the "policy" of what a score is (e.g., a letter grade) with the "mechanism" of evaluation and reporting. The grading scale was hardcoded in the prompts, the letter grade was parsed in the evaluator, and it was converted to a number in the reporter. This was brittle and hard to change.
*   **The Fix:** We introduced the `failter.scoring` namespace, which uses Clojure's multimethods. This centralizes all scoring logic into one place. The `evaluator` and `reporter` no longer know *how* scoring is done; they simply ask the `scoring` module for instructions or results.
*   **Your Instruction:** Embrace this design principle. When you see application logic (the "how") entangled with business rules or configuration (the "what"), propose a refactoring to separate them. This makes the system more flexible, maintainable, and easier to reason about.

#### **3. On Collaboration**

1.  **State Your Intentions Clearly:** Explain your plan before writing code.
2.  **Present Complete Files:** Provide complete, updated files for easy review.
3.  **Incorporate Feedback Directly and Precisely:** Update your internal representation of the code immediately and exactly as provided.
4.  **Preserve Context:** Do not remove developer comments from the code.

#### **4. The Path Forward: Next Steps**

**Current Project State:**
The project is in an excellent state. The architecture has been made significantly more robust through a series of key refactorings:
*   The introduction of first-class `Trial` and `Eval` records has clarified the data pipeline.
*   A centralized `failter.config` namespace holds all configuration.
*   A dedicated `failter.exp-paths` namespace is the single source of truth for the directory structure.
*   Crucially, the new **`failter.scoring`** namespace provides a pluggable, centralized system for all evaluation logic, perfectly decoupling policy from the core application mechanics.

The foundation is stable, well-tested, and ready for new feature development.

**Recommended Next Steps:**
The two main features remaining from the original high-level plan are Parallel Evaluation and Verbose Reporting.

1.  **Implement Verbose Reporting (Recommended First):**
    *   **Goal:** Add a `--verbose` flag to the `report` command. When enabled, the report should include the `rationale` for any trial that received a score below a certain threshold.
    *   **Why first?** This is a high-value, low-risk, self-contained feature. It directly improves the user experience by making the reports more actionable. Thanks to our recent refactoring, this task is now even more straightforward, as the `reporter` module is simpler and no longer cluttered with score conversion logic.

2.  **Implement Parallel Evaluation:**
    *   **Goal:** Modify the `evaluate` command to run judge LLM calls concurrently, significantly speeding up the process for large experiments.
    *   **Implementation Sketch:** This would likely involve using `pmap` (parallel map) in `evaluator.clj` instead of `doseq`.
    *   **Challenges:** This is a more complex task. We need to be mindful of overwhelming the LiteLLM proxy and ensure that logging from parallel operations remains intelligible.

Welcome to the team. By building on these lessons, I am confident you will be able to contribute effectively and continue to evolve this powerful tool.
