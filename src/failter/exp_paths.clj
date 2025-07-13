(ns failter.exp-paths
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn- sanitize-name
  "Replaces filesystem-unfriendly characters in a string with hyphens."
  [s]
  (-> s (str/replace #"\.prompt$" "") (str/replace #"\.md$" "") (str/replace #"[/:.\s]+" "-")))

;; --- Derivative Paths based on an artifacts_dir ---

(defn output-path-for-trial
  "Calculates the full path for a trial's output artifact within a given artifacts directory."
  [artifacts-dir model-name template-path input-path]
  (let [sanitized-model    (sanitize-name model-name)
        sanitized-template (sanitize-name (.getName (io/file template-path)))
        output-dir-name    (str sanitized-model "_" sanitized-template)
        output-dir         (io/file artifacts-dir output-dir-name)
        input-filename     (.getName (io/file input-path))]
    (.getPath (io/file output-dir input-filename))))

;; --- Sibling Artifact Paths ---

(defn thoughts-path
  "Returns the path for a .thoughts file, given the path to the primary result artifact."
  [result-path]
  (str result-path ".thoughts"))

(defn eval-path
  "Returns the path for a .eval file, given the path to the primary result artifact."
  [result-path]
  (str result-path ".eval"))

;; --- Report Path ---

(defn report-json-path
  "Returns the path for the final JSON report file within an experiment directory.
  Note: This is now less critical as JSON is written to stdout, but is used when
  an output file is specified."
  [exp-dir]
  (.getPath (io/file exp-dir "report.json")))


;; --- File Discovery ---

(defn find-all-result-files
  "Finds all primary output files within an artifacts directory, excluding Failter's own
  ancillary artifact files like .eval and .thoughts."
  [artifacts-dir]
  (let [artifacts-root (io/file artifacts-dir)]
    (when (.exists artifacts-root)
      (->> artifacts-root
           (file-seq)
           (filter #(and (.isFile %)
                         (not (str/ends-with? (.getName %) ".thoughts"))
                         (not (str/ends-with? (.getName %) ".eval"))))))))
