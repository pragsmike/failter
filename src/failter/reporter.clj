(ns failter.reporter
  (:require [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [failter.frontmatter :as fm]
            [clojure.string :as str]))

(def grade-scores {"A" 5 "B" 4 "C" 3 "D" 2 "F" 1})

(defn- get-trial-result
  "Parses data from a primary output file. If a corresponding .eval file
  exists, its data is merged. If not, it checks for an :error in the
  frontmatter and synthetically creates an 'F' grade."
  [output-file]
  (try
    (let [output-meta (:frontmatter (fm/parse-file-content (slurp output-file)))
          combo-name  (-> output-file .getParentFile .getName)
          eval-file   (io/file (str (.getPath output-file) ".eval"))
          base-result (assoc output-meta :combo combo-name)]
      (cond
        (.exists eval-file)
        (let [eval-content (slurp eval-file)
              yaml-regex #"(?s)```yaml\s*(.+?)\s*```"
              yaml-str (or (second (re-find yaml-regex eval-content)) eval-content)
              eval-meta (yaml/parse-string yaml-str)]
          (merge base-result eval-meta))

        (:error output-meta)
        (assoc base-result :grade "F" :rationale (:error output-meta))

        :else
        base-result))
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
     :avg-cost (if (and (seq costs) (every? some? costs)) (/ (double (apply + costs)) (count costs)) 0.0)
     :grade-dist (frequencies grades)}))

(defn generate-report [experiment-dir]
  (println (str "Generating report for experiment: " experiment-dir "\n"))
  (let [;; --- NEW LOGIC: Find primary outputs, not .eval files ---
        output-files (->> (io/file experiment-dir)
                          (file-seq)
                          (filter #(and (.isFile %)
                                        (str/ends-with? (.getName %) ".md")
                                        (not (re-find #"^(inputs|templates)$"
                                                      (-> % .getParentFile .getName))))))
        parsed-data (remove nil? (map get-trial-result output-files))
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
                          (pr-str (into (sorted-map-by #(compare %2 %1)) grade-dist))])))))
