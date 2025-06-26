(ns failter.experiment
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

(defn- sanitize-name
  "Replaces filesystem-unfriendly characters in a string with hyphens."
  [s]
  (-> s (str/replace #"\.md$" "") (str/replace #"[/:.\s]+" "-")))

(defn- build-trial-params
  "Constructs the full parameter map for a single trial run."
  [experiment-dir model-name template-path input-path]
  (let [sanitized-model    (sanitize-name model-name)
        sanitized-template (sanitize-name (.getName (io/file template-path)))
        output-dir-name    (str sanitized-model "_" sanitized-template)
        output-dir         (io/file experiment-dir "results" output-dir-name)
        input-filename     (.getName (io/file input-path))
        output-file        (io/file output-dir input-filename)]
    {:model-name    model-name
     :template-path template-path
     :input-path    input-path
     :output-path   (.getPath output-file)}))

(defn- list-file-paths [dir]
  (->> (io/file dir) .listFiles (filter #(.isFile %)) (map #(.getPath %))))

(defn- read-model-names [file-path]
  (with-open [rdr (io/reader file-path)]
    (->> (line-seq rdr) (map str/trim) (remove str/blank?) (doall))))

(defn print-trial-details [params]
  (println "--- Queued Trial (Dry Run) ---")
  (pprint/pprint params)
  (println "------------------------------\n"))

(defn conduct-experiment [experiment-dir trial-fn]
  (try
    (let [inputs-dir    (io/file experiment-dir "inputs")
          templates-dir (io/file experiment-dir "templates")
          models-file   (io/file experiment-dir "model-names.txt")
          inputs        (list-file-paths inputs-dir)
          templates     (list-file-paths templates-dir)
          models        (read-model-names models-file)
          total-trials  (* (count inputs) (count templates) (count models))]
      (println (str "Starting experiment in: " experiment-dir))
      (println (str "Found " (count inputs) " inputs, " (count templates) " templates, " (count models) " models."))
      (println (str "Total trials to consider: " total-trials "\n"))
      (doseq [input-path    inputs
              template-path templates
              model-name    models]
        (let [trial-params (build-trial-params experiment-dir model-name template-path input-path)
              output-file  (io/file (:output-path trial-params))]
          (if (.exists output-file)
            (println (str "Skipping existing trial: " (:output-path trial-params)))
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
