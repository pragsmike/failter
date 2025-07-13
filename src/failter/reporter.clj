(ns failter.reporter
  (:require [clojure.data.json :as json]
            [failter.eval :as feval]
            [failter.exp-paths :as exp-paths]
            [failter.log :as log]
            [clojure.java.io :as io]))

(defn- trial-error->str
  "Creates a canonical error string from a trial's error field."
  [trial]
  (when-let [err (:error trial)]
    (str "Trial failed: " err)))

(defn- eval->json
  "Transforms a single Eval record into the map structure for the final JSON report."
  [e]
  (let [t (:trial e)
        ;; This is the bug fix. The canonical source of the error message must
        ;; be the trial record itself, not a combination of the trial and eval.
        ;; This guarantees consistent formatting.
        final-error (trial-error->str t)]
    {:prompt_id (.getName (io/file (:template-path t)))
     :score (when (nil? final-error) (:score e))
     :usage {:model_used (:model-name t)
             :tokens_in (:tokens-in t)
             :tokens_out (:tokens-out t)}
     :performance {:execution_time_ms (:execution-time-ms t)
                   :total_trial_time_ms (:total-trial-time-ms t)
                   :retry_attempts (:retry-attempts t)}
     :error final-error}))

(defn generate-report-data
  "Reads all evaluations from an experiment and returns them as a sequence of maps,
  ready for JSON serialization."
  [experiment-dir]
  (log/info (str "Generating report data for experiment: " experiment-dir))
  (let [evals (feval/read-all-evals experiment-dir)]
    (->> evals
         (map eval->json)
         (sort-by :prompt_id))))

(defn generate-and-write-report
  "High-level function for the legacy 'report' command. This will be replaced
  by the new 'run' command's orchestration logic."
  [experiment-dir]
  (let [report-data (generate-report-data experiment-dir)
        json-string (json/write-str report-data :value-fn (fn [_ v] (if (Double/isNaN v) 0.0 v)))
        report-json-path (exp-paths/report-json-path experiment-dir)]

    (log/info "--- Experiment Report (JSON) ---")
    (log/info json-string)

    (log/info (str "Writing JSON report to: " report-json-path))
    (spit report-json-path json-string)
    (log/info "Report generation complete.")))
