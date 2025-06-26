(ns failter.core
  (:require [failter.llm-interface :as llm]
            [failter.runner :as runner]
            [failter.experiment :as experiment]
            [failter.evaluator :as evaluator]
            [failter.reporter :as reporter])) ; Added reporter

(defn- print-usage-and-exit []
  (println "Usage: failter <command> [options]")
  (println "")
  (println "Commands:")
  (println "  single <input> <output>              Run a single transformation using hardcoded settings.")
  (println "  experiment <dir> [--dry-run]         Run a full experiment from a structured directory.")
  (println "  evaluate <dir> [--judge-model name]  Evaluate results in an experiment directory.")
  (println "  report <dir>                         Generate a summary report from .eval files.")
  (println "")
  (System/exit 1))

(defn -main [& args]
  (llm/pre-flight-checks)

  (let [[command & params] args]
    (case command
      "single"
      (if (not= (count params) 2)
        (print-usage-and-exit)
        (let [[input-file output-file] params]
          (runner/run-single-trial
           {:model-name "ollama/qwen3:32b"
            :template-path "prompts/cleanup-small-model.md"
            :input-path    input-file
            :output-path   output-file})))

      "experiment"
      (if (empty? params)
        (print-usage-and-exit)
        (let [[experiment-dir dry-run-flag] params
              trial-fn (if (= dry-run-flag "--dry-run")
                         experiment/print-trial-details
                         runner/live-trial-runner)]
          (println (str "--- Starting Experiment (" (if (= dry-run-flag "--dry-run") "Dry" "Live") " Run) ---"))
          (experiment/conduct-experiment experiment-dir trial-fn)))

      "evaluate"
      (if (empty? params)
        (print-usage-and-exit)
        (let [[experiment-dir & [flag model-name]] params]
          (if (and flag (not= flag "--judge-model"))
            (print-usage-and-exit)
            (if model-name
              (evaluator/run-evaluation experiment-dir :judge-model model-name)
              (evaluator/run-evaluation experiment-dir)))))

      "report"
      (if (not= (count params) 1)
        (print-usage-and-exit)
        (reporter/generate-report (first params)))

      (print-usage-and-exit))))
