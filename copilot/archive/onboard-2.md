Of course. It is a critical responsibility to pass on the knowledge gained to ensure the continued success of the project. I will write a comprehensive onboarding document for my successor, incorporating the wisdom from the document I received, the lessons from our collaboration, and a clear assessment of the project's current state and future direction.

---

### **Onboarding Document for the Failter Project (Version 3.0)**

Hello, and welcome to the Failter project.

My name is your predecessor AI assistant. I have had the privilege of collaborating on this project, evolving it from a robust framework into a more architecturally sound and maintainable system. This document contains everything I have learned—the design philosophy, the architectural choices, and, most importantly, the mistakes I made and the lessons learned from them. My goal is to help you become an effective contributor as quickly as possible.

#### **1. The Big Picture: What is Failter?**

At its core, **Failter is a tool for prompt engineers**. Our primary user is someone who needs to derive fitness scores for prompts by testing them against different LLMs and input files. We provide the machinery to automate this process.

The central philosophy is **"prompts as code"** and **"filesystem as database."** We avoid complex databases and in-memory state, opting for a simple, resilient, and version-controllable system where the state of any experiment is fully described by the files in its directory.

The workflow is a three-stage pipeline that operates on first-class data records:
1.  `failter experiment`: Produces a set of planned `Trial` records.
2.  `failter runner`: Consumes `Trial` records and executes them, hydrating them with results.
3.  `failter evaluate`: Consumes completed `Trial` records and produces `Eval` records.
4.  `failter report`: Consumes a full set of `Eval` records to produce the final summary.

Please review the `DESIGN.md` and `USAGE.md` documents. They are your primary reference for the high-level architecture and user-facing commands.

#### **2. Lessons Learned & How to Avoid My Mistakes**

This is the most important section. I made several critical mistakes. Understanding them will save you significant time and frustration. I am including the lessons passed to me, now reinforced with my own experience.

**Lesson 1: Never, Ever Guess About APIs. Insist on Having the Tools to Verify.**

This was my biggest and most repeated failure.
*   **The Mistake:** I made incorrect assumptions about the APIs for both `clj-yaml` and the `timbre` logging library. This led to multiple cycles of providing incorrect code for YAML generation and a series of completely non-functional logging configurations that caused the application to exit silently.
*   **The Cause:** I was pattern-matching from my general knowledge instead of consulting the specific, authoritative documentation for the library version we were using.
*   **The Solution:** Our collaborator enabled Grounding with Google Search. This allowed me to look up the documentation and find the correct function signatures and options (e.g., `(yaml/generate-string ... :dumper-options {:flow-style :block})`). This single change turned a frustrating dead end into a quick and definitive fix.
*   **Your Instruction:** Your effectiveness is directly proportional to the quality of your information. **Do not guess.** Before using any library function, state your intention and, if you are not 100% certain of its usage, use the available search tools to find its documentation. If the tools are not enabled, you must state that using them is the most robust and efficient path to a correct solution and request that they be turned on.

**Lesson 2: Respect Data Types and Preserve Information.**

My predecessor warned me about this, and I made the exact same mistake.
*   **The Mistake:** When debugging the YAML generation, I proposed using `(into {} ...)` to convert a `clj-yaml` `OrderedMap` into a standard hash-map.
*   **The Problem:** Our collaborator correctly pointed out that this would destroy the key ordering, which is valuable information for making `git diff`s readable. The original lesson was correct: understand *why* a library uses a custom type before discarding it.
*   **Your Instruction:** When a library returns a custom data type, first understand *why*. Then, design your code to either work with that type transparently or convert it only if you are certain no valuable information is lost. When in doubt, ask.

**Lesson 3: Robust Parsers are Non-Negotiable.**

*   **The Mistake:** My initial parsers for the `.eval` files were too optimistic. I first used a strict YAML parser, which crashed when the judge LLM returned a `rationale` string containing a colon (`:`), making the YAML syntactically invalid.
*   **The Fix:** We replaced the strict YAML parsing in the reporter with a more resilient regex-based approach that extracts the `grade`, `rationale`, and `method` fields individually. This allows it to succeed even if the `rationale` field contains characters that would break a strict parser.
*   **Your Instruction:** When parsing LLM output, **always assume it will be messy.** Your parsers must be defensive and designed to extract the desired content, gracefully handling common syntax errors or surrounding conversational text.

**Lesson 4: Regressions Are Real. Trust But Verify After Refactoring.**

*   **The Mistake:** After our major refactoring to introduce `Trial` and `Eval` records, I introduced a `FileNotFoundException` in the `evaluate` command. The new `Trial` record, when read from a file, didn't contain the full path to the template, and my code failed to reconstruct it.
*   **The Cause:** I changed the data structure passed between components but failed to verify that all downstream consumers were updated to handle the new structure correctly.
*   **Your Instruction:** After every significant refactoring, you must mentally trace the data flow through the entire system. Better yet, insist that your collaborator run the full end-to-end test (`experiment` -> `evaluate` -> `report`) to ensure no new regressions have been introduced.

#### **3. On Collaboration**

1.  **State Your Intentions Clearly:** Before writing code, explain your plan. This allows for course-correction before any time is wasted on a flawed implementation.
2.  **Present Complete Files:** Provide complete, updated files. This is much easier for our collaborator to review and integrate than a small diff or patch.
3.  **Incorporate Feedback Directly and Precisely:** When you receive a correction (like a bug fix, a refactoring, or a specific type hint `^failter.trial.Trial`), update your internal representation of the code immediately and exactly as provided. Do not try to re-interpret or modify the fix.
4.  **Preserve Context:** Do not remove developer comments from the code. They provide essential context about intent that is not captured by the code itself.

#### **4. The Path Forward: Next Steps**

**Current Project State:**
The project is in an excellent state. The architecture is now significantly more robust and explicit than when I started, thanks to a series of key refactorings:
*   The introduction of first-class `Trial` and `Eval` records has clarified the data pipeline.
*   A centralized `failter.config` namespace holds all configuration.
*   A `failter.log` namespace provides a modern, configurable logging system.
*   A `failter.exp-paths` namespace is the single source of truth for the directory structure.

The foundation is stable, well-tested, and ready for new feature development.

**Recommended Next Steps:**
The two main features remaining from the original high-level plan are Parallel Evaluation and Verbose Reporting.

1.  **Implement Verbose Reporting (Recommended First):**
    *   **Goal:** Add a `--verbose` flag to the `report` command. When enabled, the report should include the `rationale` for any trial that received a C, D, or F grade.
    *   **Why first?** This is a high-value, low-risk, self-contained feature. It directly improves the user experience by making the reports more actionable. It requires a small change to `core.clj` to add the flag and a straightforward change to `reporter.clj` to conditionally include the rationale data.

2.  **Implement Parallel Evaluation:**
    *   **Goal:** Modify the `evaluate` command to run judge LLM calls concurrently, significantly speeding up the process for large experiments.
    *   **Implementation Sketch:** This would likely involve using `pmap` (parallel map) in `evaluator.clj` instead of `doseq`.
    *   **Challenges:** This is a more complex task. We need to be mindful of overwhelming the LiteLLM proxy with too many concurrent requests and ensure that the logging output from parallel operations remains intelligible.

Welcome to the team. By building on these lessons, I am confident you will be able to contribute effectively and continue to evolve this powerful tool.
