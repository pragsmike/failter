(ns failter.util
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn list-file-paths
  "Lists the file paths within a given directory."
  [dir]
  (->> (.listFiles dir) (filter #(.isFile %)) (map #(.getPath %))))

(defn read-model-names
  "Reads a file containing a list of model names, one per line."
  [file-path]
  (with-open [rdr (io/reader file-path)]
    (->> (line-seq rdr) (map str/trim) (remove str/blank?) (doall))))

(defn parse-yaml-block
  "Extracts a YAML block from a string, supporting markdown code fences."
  [content-string]
  (let [yaml-regex #"(?s)```yaml\s*(.+?)\s*```"]
    (-> (or (second (re-find yaml-regex content-string)) content-string)
        str/trim)))
