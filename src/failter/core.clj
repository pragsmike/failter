(ns failter.core
  (:require [failter.llm-interface :as llm]
            [failter.runner :as runner])
  (:gen-class))

;; These are now used for the legacy single-run mode
(def MODEL "ollama/qwen3:32b")
#_(def MODEL "ollama/mistral-nemo:12b")
(def TEMPLATE "prompts/cleanup-small-model.md")
#_(def TEMPLATE "prompts/cleanup-prompt.md")

(defn -main [& args]
  ;; Pre-flight checks are still important at the entry point
  (llm/pre-flight-checks)

  ;; Check for the correct number of command-line arguments.
  (if (not= (count args) 2)
    (do
      (println "Usage: failter <input-file> <output-file>")
      (println "This runs a single file transformation using the default model and template.")
      (System/exit 1))
    ;; If arguments are correct, call the runner.
    (let [[input-file output-file] args]
      (println "--- FAILTER (Single Run Mode) ---")
      (runner/run-single-trial
       {:model-name MODEL
        :template-path TEMPLATE
        :input-path input-file
        :output-path output-file
        :timeout 600000}))))
