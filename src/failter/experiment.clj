(ns failter.experiment
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [failter.exp-paths :as exp-paths]
            [failter.log :as log]
            [failter.trial :as trial]
            [failter.util :as util]))

(defn print-trial-details
  [^failter.trial.Trial trial]
  (log/info "--- Queued Trial (Dry Run) ---")
  (log/info (with-out-str (pprint/pprint trial)))
  (log/info "------------------------------\n"))

(defn conduct-experiment [experiment-dir trial-fn]
  (try
    (let [inputs        (util/list-file-paths (exp-paths/inputs-dir experiment-dir))
          templates     (util/list-file-paths (exp-paths/templates-dir experiment-dir))
          models        (util/read-model-names (exp-paths/models-file experiment-dir))
          total-trials  (* (count inputs) (count templates) (count models))]
      (log/info (str "Starting experiment in: " experiment-dir))
      (log/info (str "Found " (count inputs) " inputs, " (count templates) " templates, " (count models) " models."))
      (log/info (str "Total trials to consider: " total-trials "\n"))
      (doseq [input-path    inputs
              template-path templates
              model-name    models]
        (let [trial (trial/new-trial experiment-dir model-name template-path input-path)
              output-file  (io/file (:output-path trial))]
          (if (.exists output-file)
            (log/info (str "Skipping existing trial: " (:output-path trial)))
            (try
              (trial-fn trial)
              (catch Exception e
                (log/error (str "\nTrial failed for input '" (.getName (io/file input-path))
                              "' with model '" model-name "'"))
                (log/error (str "  -> " (.getMessage e) "\n"))))))))
    (catch java.io.FileNotFoundException e
      (log/error (str "Could not start experiment. A required file or directory is missing: " (.getMessage e)))
      (System/exit 1))
    (catch Exception e
      (log/error (str "An unexpected error occurred during experiment setup: " (.getMessage e)))
      (System/exit 1))))
