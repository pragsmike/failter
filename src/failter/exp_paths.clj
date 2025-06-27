(ns failter.exp-paths
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn- sanitize-name
  "Replaces filesystem-unfriendly characters in a string with hyphens."
  [s]
  (-> s (str/replace #"\.md$" "") (str/replace #"[/:.\s]+" "-")))

;; --- Root Level Directories/Files ---

(defn inputs-dir [exp-dir] (io/file exp-dir "inputs"))
(defn templates-dir [exp-dir] (io/file exp-dir "templates"))
(defn results-dir [exp-dir] (io/file exp-dir "results"))
(defn ground-truth-dir [exp-dir] (io/file exp-dir "ground_truth")) ; New
(defn models-file [exp-dir] (io/file exp-dir "model-names.txt"))
(defn report-md-path [exp-dir] (.getPath (io/file exp-dir "report.md")))
(defn report-csv-path [exp-dir] (.getPath (io/file exp-dir "report.csv")))

;; --- Derivative Paths ---

(defn output-path-for-trial
  "Constructs the full output file path for a given trial's components."
  [exp-dir model-name template-path input-path]
  (let [sanitized-model    (sanitize-name model-name)
        sanitized-template (sanitize-name (.getName (io/file template-path)))
        output-dir-name    (str sanitized-model "_" sanitized-template)
        output-dir         (io/file (results-dir exp-dir) output-dir-name)
        input-filename     (.getName (io/file input-path))]
    (.getPath (io/file output-dir input-filename))))

(defn input-path-for-result
  "Finds the original input path from a result file's path."
  [exp-dir result-path]
  (let [input-filename (.getName (io/file result-path))]
    (.getPath (io/file (inputs-dir exp-dir) input-filename))))

(defn template-path-for-result
  "Finds the original template path from a result file's metadata."
  [exp-dir result-metadata]
  (let [template-filename (:filtered-by-template result-metadata)]
    (.getPath (io/file (templates-dir exp-dir) template-filename))))

(defn ground-truth-path-for-input
  "Constructs the expected path for a ground truth file corresponding to an input file."
  [exp-dir input-path]
  (let [input-filename (.getName (io/file input-path))]
    (.getPath (io/file (ground-truth-dir exp-dir) input-filename))))

;; --- Sibling Artifact Paths ---

(defn thoughts-path [result-path] (str result-path ".thoughts"))
(defn eval-path [result-path] (str result-path ".eval"))
