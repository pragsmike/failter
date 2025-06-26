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
          eval-file   (io/file (str (.getPath output-file) ".eval"))]
      (cond
        (.exists eval-file)
        (let [eval-content (slurp eval-file)
              yaml-regex #"(?s)```yaml\s*(.+?)\s*```"
              yaml-str (or (second (re-find yaml-regex eval-content)) eval-content)
              eval-meta (yaml/parse-string yaml-str)]
          (merge output-meta eval-meta))

        (:error output-meta)
        (assoc output-meta :grade "F" :rationale (:error output-meta))

        :else
        output-meta))
    (catch Exception e nil)))

(defn- calculate-summary [results]
  (let [first-result (first results)
        grades (keep :grade results)
        scores (keep grade-scores grades)
        times  (keep :execution-time-ms results)
        costs  (keep :estimated-cost results)
        errors (filter :error results)]
    {:model (:filtered-by-model first-result)
     :template (:filtered-by-template first-result)
     :trials (count results)
     :errors (count errors)
     :avg-score (if (seq scores) (/ (double (apply + scores)) (count scores)) 0.0)
     :avg-time-s (if (seq times) (/ (double (apply + times)) 1000 (count times)) 0.0)
     :avg-cost (if (and (seq costs) (every? some? costs)) (/ (double (apply + costs)) (count costs)) 0.0)
     :grade-dist (frequencies grades)}))

(defn- format-as-table [summaries]
  (let [header (str/join " | "
                         [(format "%-35s" "Model")
                          (format "%-20s" "Template")
                          (format "%-10s" "Avg Score")
                          (format "%-10s" "Avg Time(s)")
                          (format "%-10s" "Avg Cost")
                          (format "%-7s" "Trials")
                          (format "%-6s" "Errors")
                          "Grade Distribution"])
        separator (str/join "" (repeat 140 "-"))
        rows (for [{:keys [model template avg-score avg-time-s avg-cost trials errors grade-dist]} summaries]
               (str/join " | "
                         [(format "%-35s" model)
                          (format "%-20s" template)
                          (format "%-10.2f" avg-score)
                          (format "%-10.2f" avg-time-s)
                          (format "$%-9.6f" avg-cost)
                          (format "%-7d" trials)
                          (format "%-6d" errors)
                          (pr-str (into (sorted-map-by #(compare %2 %1)) grade-dist))]))]
    (str/join "\n" (concat [header separator] rows))))

(defn- format-as-csv [summaries]
  (let [header "Model,Template,Avg_Score,Avg_Time_s,Avg_Cost,Trials,Errors,Grade_Distribution"
        rows (for [{:keys [model template avg-score avg-time-s avg-cost trials errors grade-dist]} summaries]
               (str/join "," [(str "\"" model "\"")
                              (str "\"" template "\"")
                              (format "%.2f" avg-score)
                              (format "%.2f" avg-time-s)
                              (format "%.6f" avg-cost)
                              trials
                              errors
                              (str "\"" (pr-str (into (sorted-map-by #(compare %2 %1)) grade-dist)) "\"")]))]
    (str/join "\n" (cons header rows))))

(defn generate-report [experiment-dir]
  (println (str "Generating report for experiment: " experiment-dir))
  (let [results-root (io/file experiment-dir "results")
        output-files (when (.exists results-root)
                       (->> results-root
                            (file-seq)
                            (filter #(and (.isFile %) (str/ends-with? (.getName %) ".md")))))
        parsed-data (remove nil? (map get-trial-result output-files))
        summaries (->> parsed-data
                       (group-by (juxt :filtered-by-model :filtered-by-template))
                       (vals)
                       (map calculate-summary)
                       (sort-by :avg-score)
                       (reverse))

        table-string (format-as-table summaries)
        csv-string   (format-as-csv summaries)

        report-md-path  (str (io/file experiment-dir "report.md"))
        report-csv-path (str (io/file experiment-dir "report.csv"))]

    (println "\n--- Experiment Report ---\n")
    (println table-string)

    (println (str "\nWriting markdown report to: " report-md-path))
    (spit report-md-path table-string)

    (println (str "Writing CSV report to:      " report-csv-path))
    (spit report-csv-path csv-string)
    (println "\nReport generation complete.")))
