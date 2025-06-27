(ns failter.reporter
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure-csv.core :as csv]
            [failter.exp-paths :as exp-paths]
            [failter.frontmatter :as fm]
            [failter.util :as util]))

(def grade-scores {"A" 5 "B" 4 "C" 3 "D" 2 "F" 1})

(defn- get-trial-result
  [output-file]
  (try
    (let [output-meta (:frontmatter (fm/parse-file-content (slurp output-file)))
          eval-file   (io/file (exp-paths/eval-path (.getPath output-file)))]
      (cond
        (.exists eval-file)
        (let [raw-eval-content (slurp eval-file)
              ;; First, use the utility to strip any potential markdown fence.
              clean-eval-content (util/parse-yaml-block raw-eval-content)

              ;; Then, run the robust regex parsers on the clean content.
              grade (second (re-find #"(?m)^grade:\s*([A-DF])" clean-eval-content))
              method (second (re-find #"(?m)^evaluation-method:\s*(\S+)" clean-eval-content))
              rationale (second (re-find #"(?ms)^rationale:\s*(.*)" clean-eval-content))
              eval-meta {:grade grade
                         :rationale (str/trim rationale)
                         :evaluation-method method}]
          (merge output-meta eval-meta))

        (:error output-meta)
        (assoc output-meta :grade "F" :rationale (:error output-meta) :evaluation-method "failed")

        :else
        (assoc output-meta :evaluation-method "unevaluated")))
    (catch Exception e nil)))

(defn- calculate-summary [results]
  (when-let [first-result (first results)]
    (let [grades (keep :grade results)
          scores (keep grade-scores grades)
          times  (keep :execution-time-ms results)
          costs  (keep :estimated-cost results)
          errors (filter :error results)
          methods (frequencies (keep :evaluation-method results))]
      {:model (:filtered-by-model first-result)
       :template (:filtered-by-template first-result)
       :trials (count results)
       :errors (count errors)
       :avg-score (if (seq scores) (/ (double (apply + scores)) (count scores)) 0.0)
       :avg-time-s (if (seq times) (/ (double (apply + times)) 1000 (count times)) 0.0)
       :avg-cost (if (and (seq costs) (every? some? costs)) (/ (double (apply + costs)) (count costs)) 0.0)
       :grade-dist (frequencies grades)
       :eval-methods methods})))

(defn- prepare-summary-for-display
  [{:keys [model template avg-score avg-time-s avg-cost trials errors grade-dist eval-methods]}]
  {:model model
   :template template
   :avg-score (format "%.2f" avg-score)
   :avg-time-s (format "%.2f" avg-time-s)
   :avg-cost (format "$%.6f" avg-cost)
   :trials (str trials)
   :errors (str errors)
   :grade-dist (pr-str (into (sorted-map-by #(compare %2 %1)) grade-dist))
   :eval-methods (str/join ", " (for [[k v] eval-methods] (str v " " k)))})

(defn- format-as-table [display-summaries]
  (let [header (str/join " | "
                         [(format "%-28s" "Model")
                          (format "%-20s" "Template")
                          (format "%-10s" "Avg Score")
                          (format "%-10s" "Avg Time(s)")
                          (format "%-10s" "Avg Cost")
                          (format "%-7s" "Trials")
                          (format "%-6s" "Errors")
                          (format "%-18s" "Eval Methods")
                          "Grade Distribution"])
        separator (str/join "" (repeat 155 "-"))
        rows (for [{:keys [model template avg-score avg-time-s avg-cost trials errors grade-dist eval-methods]} display-summaries]
               (str/join " | "
                         [(format "%-28s" model)
                          (format "%-20s" template)
                          (format "%-10s" avg-score)
                          (format "%-10s" avg-time-s)
                          (format "%-10s" avg-cost)
                          (format "%-7s" trials)
                          (format "%-6s" errors)
                          (format "%-18s" eval-methods)
                          grade-dist]))]
    (str/join "\n" (concat [header separator] rows))))

(defn- format-as-csv [display-summaries]
  (let [header ["Model" "Template" "Avg_Score" "Avg_Time_s" "Avg_Cost" "Trials" "Errors" "Eval_Methods" "Grade_Distribution"]
        rows (for [{:keys [model template avg-score avg-time-s avg-cost trials errors grade-dist eval-methods]} display-summaries]
               [model template avg-score avg-time-s avg-cost trials errors eval-methods grade-dist])
        data-to-write (cons header rows)]
    (csv/write-csv data-to-write)))

(defn generate-report [experiment-dir]
  (println (str "Generating report for experiment: " experiment-dir))
  (let [output-files (exp-paths/find-all-result-files experiment-dir)
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
