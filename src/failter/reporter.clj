(ns failter.reporter
  (:require [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]))

(def grade-scores {"A" 5, "B" 4, "C" 3, "D" 2, "F" 1})

(defn- find-eval-files [experiment-dir]
  (->> (io/file experiment-dir)
       (file-seq)
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".eval"))))

(defn- parse-eval-file
  "Parses a .eval file. Robustly handles LLM output by extracting the YAML
  block from surrounding text and markdown fences."
  [file]
  (try
    (let [combo-name (-> file .getParentFile .getName)
          raw-content (slurp file)
          ;; Regex to find a yaml block, ignoring surrounding text.
          ;; (?s) allows . to match newlines.
          yaml-regex #"(?s)```yaml\s*(.+?)\s*```"
          ;; re-find returns [full-match, captured-group-1, ...]
          yaml-content (second (re-find yaml-regex raw-content))]

      ;; If the regex fails, try parsing the whole file as a fallback.
      (if-let [content-to-parse (or yaml-content raw-content)]
        (let [parsed-yaml (yaml/parse-string content-to-parse)]
          (assoc parsed-yaml :combo combo-name))
        (throw (Exception. "No YAML content found."))))
    (catch Exception e
      (println (str "Warning: Could not parse " (.getPath file) ". Error: " (.getMessage e) ". Skipping."))
      nil)))

(defn- calculate-summary [combo-name results]
  (let [grades (map :grade results)
        scores (keep grade-scores grades)
        avg-score (if (seq scores)
                    (/ (double (apply + scores)) (count scores))
                    0.0)]
    {:combo combo-name
     :trials (count results)
     :avg-score avg-score
     :grade-dist (frequencies grades)}))

(defn generate-report [experiment-dir]
  (println (str "Generating report for experiment: " experiment-dir "\n"))
  (let [eval-files (find-eval-files experiment-dir)
        parsed-data (->> eval-files
                         (map parse-eval-file)
                         (remove nil?))
        grouped-by-combo (group-by :combo parsed-data)
        summaries (->> grouped-by-combo
                       (map (fn [[combo results]] (calculate-summary combo results)))
                       (sort-by :avg-score)
                       (reverse))]

    ;; Print Header
    (println (str/join " | "
                       [(format "%-50s" "Model_Template_Combination")
                        (format "%-12s" "Avg Score")
                        (format "%-8s" "Trials")
                        "Grade Distribution"]))
    (println (str/join "" (repeat 120 "-")))

    ;; Print Results
    (doseq [{:keys [combo avg-score trials grade-dist]} summaries]
      (println (str/join " | "
                         [(format "%-50s" combo)
                          (format "%-12.2f" avg-score)
                          (format "%-8d" trials)
                          (pr-str (into (sorted-map) grade-dist))])))))
