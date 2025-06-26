(ns failter.reporter
  (:require [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [failter.frontmatter :as fm]
            [clojure.string :as str]))

(def grade-scores {"A" 5 "B" 4 "C" 3 "D" 2 "F" 1})

(defn- parse-data-from-pair [eval-file]
  (try
    (let [output-file (io/file (str/replace (.getPath eval-file) #"\.eval$" ""))
          output-meta (:frontmatter (fm/parse-file-content (slurp output-file)))
          eval-meta   (yaml/parse-string (second (re-find #"(?s)```yaml\s*(.+?)\s*```" (slurp eval-file))))
          combo-name  (-> output-file .getParentFile .getName)]
      (merge output-meta eval-meta {:combo combo-name}))
    (catch Exception e nil)))

(defn- calculate-summary [combo-name results]
  (let [grades (keep :grade results)
        scores (keep grade-scores grades)
        times  (keep :execution-time-ms results)
        costs  (keep :estimated-cost results)
        errors (filter :error results)]
    {:combo combo-name
     :trials (count results)
     :errors (count errors)
     :avg-score (if (seq scores) (/ (double (apply + scores)) (count scores)) 0.0)
     :avg-time-s (if (seq times) (/ (double (apply + times)) 1000 (count times)) 0.0)
     :avg-cost (if (seq costs) (/ (double (apply + costs)) (count costs)) 0.0)
     :grade-dist (frequencies grades)}))

(defn generate-report [experiment-dir]
  (println (str "Generating report for experiment: " experiment-dir "\n"))
  (let [eval-files (filter #(.exists (io/file (str/replace (.getPath %) #"\.eval$" "")))
                           (->> (io/file experiment-dir) file-seq (filter #(str/ends-with? (.getName %) ".eval"))))
        parsed-data (remove nil? (map parse-data-from-pair eval-files))
        summaries (->> (group-by :combo parsed-data)
                       (map (fn [[combo results]] (calculate-summary combo results)))
                       (sort-by :avg-score)
                       (reverse))]
    (println (str/join " | "
                       [(format "%-45s" "Model_Template_Combination")
                        (format "%-10s" "Avg Score")
                        (format "%-10s" "Avg Time(s)")
                        (format "%-10s" "Avg Cost")
                        (format "%-7s" "Trials")
                        (format "%-6s" "Errors")
                        "Grade Distribution"]))
    (println (str/join "" (repeat 130 "-")))
    (doseq [{:keys [combo avg-score avg-time-s avg-cost trials errors grade-dist]} summaries]
      (println (str/join " | "
                         [(format "%-45s" combo)
                          (format "%-10.2f" avg-score)
                          (format "%-10.2f" avg-time-s)
                          (format "$%-9.6f" avg-cost)
                          (format "%-7d" trials)
                          (format "%-6d" errors)
                          (pr-str (into (sorted-map) grade-dist))])))))
