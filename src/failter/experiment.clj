(ns failter.experiment
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

(defn- sanitize-name [s]
  (-> s (str/replace #"\.md$" "") (str/replace #"[/:.]+" "-")))

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
              model-name    models
              :let [sanitized-model    (sanitize-name model-name)
                    sanitized-template (sanitize-name (.getName (io/file template-path)))
                    output-dir-name    (str sanitized-model "_" sanitized-template)
                    ;; --- THIS IS THE MODIFIED LINE ---
                    output-dir         (io/file experiment-dir "results" output-dir-name)
                    input-filename     (.getName (io/file input-path))
                    output-file        (io/file output-dir input-filename)]]
        (if (.exists output-file)
          (println (str "Skipping existing trial: " (.getPath output-file)))
          (try
            (let [trial-params {:model-name    model-name
                                :template-path template-path
                                :input-path    input-path
                                :output-path   (.getPath output-file)}]
              (trial-fn trial-params))
            (catch Exception e
              (println (str "\nERROR: Trial failed for input '" input-filename
                            "' with model '" model-name "'"))
              (println (str "  -> " (.getMessage e) "\n")))))))
    (catch java.io.FileNotFoundException e
      (println (str "ERROR: Could not start experiment. A required file or directory is missing: " (.getMessage e)))
      (System/exit 1))
    (catch Exception e
      (println (str "ERROR: An unexpected error occurred during experiment setup: " (.getMessage e)))
      (System/exit 1))))
