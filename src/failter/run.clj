(ns failter.run
  (:require [clj-yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [failter.log :as log]
            [failter.util :as util]
            [failter.trial :as trial]
            [failter.runner :as runner]
            [failter.evaluator :as evaluator]
            [failter.reporter :as reporter]))

(defn- validate-spec
  "Performs basic validation on the parsed spec map."
  [spec]
  (let [required-keys [:inputs_dir :templates_dir :templates :models :judge_model :artifacts_dir]]
    (doseq [k required-keys]
      (when-not (get spec k)
        (throw (IllegalArgumentException. (str "Spec file is missing required key: " k)))))
    spec))

(defn- load-spec [spec-path]
  (log/info (str "Loading spec file from: " spec-path))
  (let [spec (-> (slurp spec-path)
                 (yaml/parse-string :keywords true))]
    (validate-spec
     ;; Defensively parse numeric keys right after loading to ensure correct types.
     (cond-> spec
       (:retries spec) (update :retries #(Integer/parseInt (str %)))))))

(defn- run-trials! [spec trials]
  ;; The :retries value is now guaranteed to be a number if it exists.
  (let [runner-opts {:retries (get spec :retries 0)}]
    (doseq [t trials]
      (let [output-file (io/file (:output-path t))]
        (if (.exists output-file)
          (log/info (str "Skipping existing trial: " (:output-path t)))
          (try
            (runner/run-single-trial t runner-opts)
            (catch Exception e
              (log/error (str "\nTrial failed unexpectedly for input '" (.getName (io/file (:input-path t)))
                            "' with model '" (:model-name t) "'"))
              (log/error (str "  -> " (.getMessage e) "\n")))))))))

(defn- run-evaluations! [spec]
  (log/info "--- Starting Evaluation Phase ---")
  (let [judge-model (:judge_model spec)
        artifacts-dir (:artifacts_dir spec)]
    (evaluator/run-evaluation artifacts-dir :judge-model judge-model)))

(defn- generate-and-output-report [spec]
  (log/info "--- Starting Report Generation Phase ---")
  (let [artifacts-dir (:artifacts_dir spec)
        report-data (reporter/generate-report-data artifacts-dir)
        json-string (json/write-str report-data :value-fn (fn [_ v] (if (Double/isNaN v) 0.0 v)))]

    (log/info "--- Final Report (JSON) ---")
    (println json-string)

    (when-let [output-file (:output_file spec)]
      (log/info (str "Writing JSON report to file: " output-file))
      (spit output-file json-string))

    (log/info "Run complete.")))

(defn execute-run
  "Main entry point for the 'run' command. Orchestrates the entire workflow."
  [spec-path]
  (try
    (let [spec (load-spec spec-path)
          artifacts-dir (:artifacts_dir spec)
          inputs (util/list-file-paths (io/file (:inputs_dir spec)))
          templates (map #(.getPath (io/file (:templates_dir spec) %)) (:templates spec))
          models (:models spec)]

      (log/info (str "--- Starting Failter Run ---"))
      (log/info (str "Inputs: " (count inputs) " | Templates: " (count templates) " | Models: " (count models)))
      (log/info (str "Artifacts will be stored in: " artifacts-dir))

      (let [all-trials (for [in inputs
                             tpl templates
                             mdl models]
                         (trial/new-trial artifacts-dir mdl tpl in))]
        (run-trials! spec all-trials))

      (run-evaluations! spec)

      (generate-and-output-report spec))

    (catch Exception e
      (log/error (str "The run failed with an unrecoverable error:" (.getMessage e)))
      (System/exit 1))))
