(ns failter.experiment
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [failter.exp-paths :as exp-paths]))

(defn- list-file-paths [dir]
  (->> (.listFiles dir) (filter #(.isFile %)) (map #(.getPath %))))

(defn- read-model-names [file-path]
  (with-open [rdr (io/reader file-path)]
    (->> (line-seq rdr) (map str/trim) (remove str/blank?) (doall))))

(defn print-trial-details [params]
  (println "--- Queued Trial (Dry Run) ---")
  (pprint/pprint params)
  (println "------------------------------\n"))

(defn conduct-experiment [experiment-dir trial-fn]
  (try
    (let [inputs        (list-file-paths (exp-paths/inputs-dir experiment-dir))
          templates     (list-file-paths (exp-paths/templates-dir experiment-dir))
          models        (read-model-names (exp-paths/models-file experiment-dir))
          total-trials  (* (count inputs) (count templates) (count models))]
      (println (str "Starting experiment in: " experiment-dir))
      (println (str "Found " (count inputs) " inputs, " (count templates) " templates, " (count models) " models."))
      (println (str "Total trials to consider: " total-trials "\n"))
      (doseq [input-path    inputs
              template-path templates
              model-name    models]
        (let [output-path  (exp-paths/output-path-for-trial experiment-dir model-name template-path input-path)
              output-file  (io/file output-path)
              trial-params {:model-name    model-name
                            :template-path template-path
                            :input-path    input-path
                            :output-path   output-path}]
          (if (.exists output-file)
            (println (str "Skipping existing trial: " output-path))
            (try
              (trial-fn trial-params)
              (catch Exception e
                (println (str "\nERROR: Trial failed for input '" (.getName (io/file input-path))
                              "' with model '" model-name "'"))
                (println (str "  -> " (.getMessage e) "\n"))))))))
    (catch java.io.FileNotFoundException e
      (println (str "ERROR: Could not start experiment. A required file or directory is missing: " (.getMessage e)))
      (System/exit 1))
    (catch Exception e
      (println (str "ERROR: An unexpected error occurred during experiment setup: " (.getMessage e)))
      (System/exit 1))))
