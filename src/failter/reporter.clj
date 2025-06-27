(ns failter.reporter
  (:require [clojure.java.io :as io]
            [clj-yaml.core :as yaml]
            [failter.frontmatter :as fm]
            [clojure.string :as str]
            [clojure-csv.core :as csv]
            [failter.exp-paths :as exp-paths]))

(def grade-scores {"A" 5 "B" 4 "C" 3 "D" 2 "F" 1})

(defn- get-trial-result
  "Parses data from a primary output file and its corresponding .eval file."
  [output-file]
  (try
    (let [output-meta (:frontmatter (fm/parse-file-content (slurp output-file)))
          eval-file   (io/file (exp-paths/eval-path (.getPath output-file)))]
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
  (when-let [first-result (first results)]
    (let [grades (keep :grade results)
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
       :grade-dist (frequencies grades)})))

(defn- prepare-summary-for-display
  "Takes a raw summary map and returns a map with values formatted for printing."
  [{:keys [model template avg-score avg-time-s avg-cost trials errors grade-dist]}]
  {:model model
   :template template
   :avg-score (format "%.2f" avg-score)
   :avg-time-s (format "%.2f" avg-time-s)
   :avg-cost (format "$%.6f" avg-cost)
   :trials (str trials)
   :errors (str errors)
   :grade-dist (pr-str (into (sorted-map-by #(compare %2 %1)) grade-dist))})

(defn- format-as-table [display-summaries]
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
        rows (for [{:keys [model template avg-score avg-time-s avg-cost trials errors grade-dist]} display-summaries]
               (str/join " | "
                         [(format "%-35s" model)
                          (format "%-20s" template)
                          (format "%-10s" avg-score)
                          (format "%-10s" avg-time-s)
                          (format "%-10s" avg-cost)
                          (format "%-7s" trials)
                          (format "%-6s" errors)
                          grade-dist]))]
    (str/join "\n" (concat [header separator] rows))))

(defn- format-as-csv [display-summaries]
  (let [header ["Model" "Template" "Avg_Score" "Avg_Time_s" "Avg_Cost" "Trials" "Errors" "Grade_Distribution"]
        rows (for [{:keys [model template avg-score avg-time-s avg-cost trials errors grade-dist]} display-summaries]
               [model template avg-score avg-time-s avg-cost trials errors grade-dist])
        data-to-write (cons header rows)]
    (csv/write-csv data-to-write)))

(defn generate-report [experiment-dir]
  (println (str "Generating report for experiment: " experiment-dir))
  (let [results-root (exp-paths/results-dir experiment-dir)
        output-files (when (.exists results-root)
                       (->> results-root
                            (file-seq)
                            (filter #(and (.isFile %) (str/ends-with? (.getName %) ".md")))))

        summaries (->> output-files
                       (map get-trial-result)
                       (remove nil?)
                       (group-by (juxt :filtered-by-model :filtered-by-template))
                       (vals)
                       (map calculate-summary)
                       (remove nil?)
                       (sort-by :avg-score)
                       (reverse))

        display-summaries (map prepare-summary-for-display summaries)
        table-string (format-as-table display-summaries)
        csv-string   (format-as-csv display-summaries)
        report-md-path  (exp-paths/report-md-path experiment-dir)
        report-csv-path (exp-paths/report-csv-path experiment-dir)]

    (println "\n--- Experiment Report ---\n")
    (println table-string)

    (println (str "\nWriting markdown report to: " report-md-path))
    (spit report-md-path table-string)

    (println (str "Writing CSV report to:      " report-csv-path))
    (spit report-csv-path csv-string)
    (println "\nReport generation complete.")))
