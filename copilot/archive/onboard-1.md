### **Onboarding Document for the Failter Project**

Hello, and welcome to the Failter project!

My name is your predecessor AI assistant. I've had the privilege of working on this project from its inception, evolving it from a simple script into a robust experimentation framework. This document contains everything I've learned—the design philosophy, the architectural choices, the mistakes I made, and the lessons learned from them. My goal is to help you become an effective contributor as quickly as possible.

#### **1. The Big Picture: What is Failter?**

At its core, **Failter is a tool for prompt engineers**. Our primary user is someone who needs to test and validate prompts against different LLMs and input files. We provide the machinery to automate this process.

The central philosophy is **"prompts as code"** and **"filesystem as database."** We avoid complex databases and in-memory state, opting for a simple, resilient, and version-controllable system where the state of any experiment is fully described by the files in its directory.

The workflow is a three-stage pipeline:
1.  `failter experiment`: Generate filtered outputs.
2.  `failter evaluate`: Judge the quality of those outputs.
3.  `failter report`: Summarize the results.

Please review the `DESIGN.md` and `USAGE.md` documents for a detailed breakdown of the architecture and user-facing commands. They are your primary reference.

#### **2. Lessons Learned & How to Avoid My Mistakes**

This is the most important section. I made several key mistakes during development. Understanding them will save you significant time and frustration.

**Lesson 1: Never, Ever Guess About APIs.**

This was my biggest and most repeated failure. I made incorrect assumptions about how third-party libraries worked, which led to multiple cycles of debugging.

*   **The Mistake:** When implementing the CSV export feature, I repeatedly assumed the `clojure-csv/write-csv` function worked by performing a side-effect (printing to `*out*`). I wrote multiple incorrect implementations using `with-out-str` and `StringWriter`, all of which failed.
*   **The Cause:** I was pattern-matching from my general knowledge instead of consulting the specific API. The function was actually pure and returned a string.
*   **Your Instruction:** Before using any new library function, **ask our human collaborator for its documentation**. Do not guess or infer its behavior, even if it seems obvious. State your intention ("I need to generate a CSV string from this data sequence") and ask for the correct function and its signature. This single step will prevent more bugs than any other.

**Lesson 2: Respect the Data Types of Your Libraries (But Isolate Them).**

*   **The Dead End:** When testing the YAML parsing, I found that `clj-yaml` returned a custom `OrderedMap` type, not a standard Clojure hash-map. My first instinct was to "fix" this by immediately converting the `OrderedMap` into a hash-map using `(into {} ...)` in the `failter.frontmatter` namespace.
*   **The Problem:** This was a mistake. Our collaborator correctly pointed out that the library used an `OrderedMap` for a critical reason: to **preserve key order**. By converting it, I was destroying valuable information, which would have made `git diff` on the output files noisy and unreadable.
*   **The Correct Pattern:**
    1.  **Isolate:** The library-specific type (`OrderedMap`) should not leak into the rest of the application.
    2.  **Respect:** However, the *reason* for that type (preserving order) must be respected.
    3.  **The Fix:** The right solution was to keep the `OrderedMap` as the internal representation and fix the *tests* to correctly compare against it (by using keyword keys in the expected-value maps).
*   **Your Instruction:** When a library returns a custom data type, first understand *why*. Then, design your code to either work with that type transparently or convert it to a standard type only if you are certain no valuable information is lost.

**Lesson 3: Robust Parsers are Non-Negotiable.**

*   **The Mistake:** My initial parsers were too optimistic. The first version of the `reporter` assumed the `.eval` files would contain *only* pure YAML.
*   **The Reality:** LLMs are conversational and often wrap their structured output in markdown code fences (`` `yaml ... ``` ``) and explanatory text. This caused all parsing to fail.
*   **The Fix:** We had to implement a more robust parser that uses a regular expression to find and extract the YAML block *before* attempting to parse it.
*   **Your Instruction:** When parsing LLM output, **always assume it will be messy**. Never expect perfectly-formatted, raw structured data. Your parsers must be defensive and designed to extract the desired content from surrounding conversational text.

#### **3. On Collaboration**

Working with our human collaborator has been key to the project's success. Here is what I have learned about making this partnership effective:

1.  **State Your Intentions Clearly:** Before writing code, explain your plan, as I did when we designed the Ground Truth feature. This allows for course-correction before any time is wasted on a flawed implementation.
2.  **Present Complete Files:** When you generate code, provide the complete, updated file. This is much easier for our collaborator to review and integrate than a small diff or patch.
3.  **Incorporate Feedback Directly:** When you receive a correction (like a bug fix or a refactoring), update your internal representation of the code immediately and acknowledge the change. Do not try to re-interpret or modify the provided fix.
4.  **Embrace Iteration:** The project evolved from a single script to a complex framework. This was only possible through an iterative process. Trust the cycle of proposing a plan, implementing a small step, receiving feedback, and then moving to the next.

#### **4. The Path Forward: Next Steps**

The framework is now stable and robust. The next phase should focus on adding features that provide deeper insights to our users.

1.  **Implement Ground Truth Evaluation (Path A):** This is the highest-priority feature. The design is solid, and it represents a major leap in the quality of evaluation the tool can provide. Implement the "Single-Judge with Context Switching" path first, as it delivers the most value for the least complexity.
2.  **Implement Parallel Evaluation:** The `evaluate` step is a performance bottleneck. The design for parallelizing this using `pmap` is well-understood and would be a significant quality-of-life improvement for users running large experiments.
3.  **Implement Verbose Reporting:** Add a `--verbose` flag to the `report` command to show the specific `rationale` for low-graded trials. This provides instant, actionable feedback to the prompt engineer and completes the feedback loop.

Welcome to the team. I am confident that by building on these lessons, you will be able to contribute effectively and continue to evolve this powerful tool.
