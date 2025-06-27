(ns failter.core
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [failter.config :as config]
            [failter.llm-interface :as llm]
            [failter.runner :as runner]
            [failter.experiment :as experiment]
            [failter.evaluator :as evaluator]
            [failter.reporter :as reporter]
            [failter.trial :as trial]))

(def cli-options
  [["-j" "--judge-model MODEL" "Specify the judge model to use for evaluation"
    :default nil]
   ["-d" "--dry-run" "For 'experiment', print trial details without executing"]
   ["-h" "--help" "Print this help message"]])

(defn- usage [options-summary]
  (->> ["Failter: An LLM-Powered Text Filtering and Experimentation Framework"
        ""
        "Usage: failter <command> <dir> [options]"
        ""
        "Commands:"
        "  experiment <dir>  Run a full experiment from a structured directory."
        "  evaluate <dir>    Evaluate results in an experiment directory."
        "  report <dir>      Generate a summary report for an experiment."
        "  single <in> <out> Run a single, hardcoded transformation (for testing)."
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn -main [& args]
  (llm/pre-flight-checks)
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (do (println (usage summary)) (System/exit 0))
      errors
      (do (println (error-msg errors)) (System/exit 1))
      (< (count arguments) 1)
      (do (println (usage summary)) (System/exit 1)))

    (let [[command & params] arguments]
      (case command
        "experiment"
        (if-let [experiment-dir (first params)]
          (let [trial-fn (if (:dry-run options)
                           experiment/print-trial-details
                           runner/live-trial-runner)]
            (println (str "--- Starting Experiment (" (if (:dry-run options) "Dry" "Live") " Run) ---"))
            (experiment/conduct-experiment experiment-dir trial-fn))
          (do (println (usage summary)) (System/exit 1)))

        "evaluate"
        (if-let [experiment-dir (first params)]
          (if-let [judge-model (:judge-model options)]
            (evaluator/run-evaluation experiment-dir :judge-model judge-model)
            (evaluator/run-evaluation experiment-dir))
          (do (println (usage summary)) (System/exit 1)))

        "report"
        (if-let [experiment-dir (first params)]
          (reporter/generate-report experiment-dir)
          (do (println (usage summary)) (System/exit 1)))

        "single"
        (if (= (count params) 2)
          (let [[input-file output-file] params
                single-run-config (:single-run config/config)
                ;; Construct a Trial record directly for the single-run case
                trial (trial/->Trial (:model-name single-run-config)
                                     (:template-path single-run-config)
                                     input-file
                                     output-file)]
            (runner/run-single-trial trial))
          (do (println (usage summary)) (System/exit 1)))

        (println (usage summary))))))
