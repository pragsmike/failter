(ns failter.core
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [failter.llm-interface :as llm]
            [failter.log :as log]
            [failter.run :as run]
            [failter.runner :as runner]
            [failter.trial :as trial]
            [failter.config :as config]))

(def cli-options
  [["-s" "--spec FILE" "The YAML spec file defining the run"]
   ["-h" "--help" "Print this help message"]])

(defn- usage [options-summary]
  (->> ["Failter: An LLM-Powered Text Filtering and Experimentation Framework"
        ""
        "Usage: failter <command> [options]"
        ""
        "Primary Command:"
        "  run --spec <file>  Executes a full, idempotent run defined by a spec file."
        ""
        "Other Commands:"
        "  single <in> <out> Run a single, hardcoded transformation (for testing)."
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

(defn- error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn -main [& args]
  (try
    (log/setup-logging!)
    (llm/pre-flight-checks)
    (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
      (cond
        (:help options)
        (do (log/info (usage summary)) (System/exit 0))

        errors
        (do (log/error (error-msg errors)) (System/exit 1))

        (empty? arguments)
        (do (log/info (usage summary)) (System/exit 1)))

      (let [[command & params] arguments]
        (case command
          "run"
          (if-let [spec-file (:spec options)]
            (run/execute-run spec-file)
            (do (log/error "The 'run' command requires a --spec file.") (System/exit 1)))

          "single"
          (if (= (count params) 2)
            (let [[input-file output-file] params
                  ;; Note: 'single' now uses hardcoded values from config, as it's for simple tests.
                  single-run-config {:model-name "ollama/qwen3:8b"
                                     :template-path "prompts/cleanup-basic.md"}]
              (runner/run-single-trial
               (trial/new-trial "." (:model-name single-run-config) (:template-path single-run-config) input-file)
               {:retries 0}))
            (do (log/info (usage summary)) (System/exit 1)))

          (log/info (usage summary)))))
    (finally
      (shutdown-agents))))
