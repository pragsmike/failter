(ns failter.core
  (:require [failter.llm-interface :as llm]
            [failter.runner :as runner]
            [failter.experiment :as experiment])
  (:gen-class))

;; These hardcoded settings are now only used for the 'single' subcommand.
(def MODEL "ollama/qwen3:32b")
#_(def MODEL "ollama/mistral-nemo:12b")
(def TEMPLATE "prompts/cleanup-small-model.md")
#_(def TEMPLATE "prompts/cleanup-prompt.md")

(defn- print-usage-and-exit []
  (println "Usage: failter <command> [options]")
  (println "")
  (println "Commands:")
  (println "  single <input-file> <output-file>   Run a single transformation using hardcoded settings in core.clj.")
  (println "  experiment <dir> [--dry-run]        Run a full experiment from a structured directory.")
  (println "                                        --dry-run: Print trial details without running them.")
  (println "")
  (System/exit 1))

(defn -main [& args]
  ;; Pre-flight checks are always run first.
  (llm/pre-flight-checks)

  (let [[command & params] args]
    (case command
      "single"
      (if (not= (count params) 2)
        (print-usage-and-exit)
        (let [[input-file output-file] params]
          (println "--- FAILTER (Single Run Mode) ---")
          (runner/run-single-trial
           {:model-name    MODEL
            :template-path TEMPLATE
            :input-path    input-file
            :output-path   output-file})))

      "experiment"
      (if (empty? params)
        (print-usage-and-exit)
        (let [[experiment-dir dry-run-flag] params
              trial-fn (if (= dry-run-flag "--dry-run")
                         (do
                           (println "--- Starting Experiment (Dry Run) ---")
                           experiment/print-trial-details)
                         (do
                           (println "--- Starting Experiment (Live Run) ---")
                           runner/live-trial-runner))]
          (experiment/conduct-experiment experiment-dir trial-fn)))

      ;; Default case for invalid command
      (print-usage-and-exit))))
