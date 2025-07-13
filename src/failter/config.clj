(ns failter.config)

(def config
  "A centralized map for all application configuration."
  {:llm {:endpoint "http://localhost:8000/chat/completions"
         :default-timeout-ms 300000}

   ;; Internal prompt templates used by the evaluator. These are considered
   ;; part of the application's logic, not user-configurable per-run.
   :evaluator {:prompts {:standard "prompts/evaluation-prompt.md"
                         :ground-truth "prompts/evaluation-prompt-gt.md"}}})
