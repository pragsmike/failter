(ns failter.core
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [failter.llm-interface :as llm]
            [failter.log :as log]
            [failter.run :as run]
            [failter.runner :as runner]
            [failter.trial :as trial]))

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
        (log/info (usage summary))

        errors
        (log/error (error-msg errors))

        (empty? arguments)
        (log/info (usage summary))

        :else
        (let [[command & params] arguments]
          (case command
            "run"
            (if-let [spec-file (:spec options)]
              (run/execute-run spec-file)
              (log/error "The 'run' command requires a --spec file."))

            "single"
            (if (= (count params) 2)
              (let [[input-file output-file] params
                    single-run-config {:model-name "ollama/qwen3:8b"
                                       :template-path "prompts/cleanup-basic.md"}]
                (runner/run-single-trial
                 (trial/new-trial "." (:model-name single-run-config) (:template-path single-run-config) input-file)
                 {:retries 0}))
              (log/info (usage summary)))

            (log/info (usage summary))))))
    (catch Exception e
      (log/error "An unhandled exception reached the main entry point:" (.getMessage e)))
    (finally
      (shutdown-agents))))
