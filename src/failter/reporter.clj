(ns failter.reporter
  (:require [clojure.string :as str]
            [clojure-csv.core :as csv]
            [clojure.java.io :as io]
            [failter.config :as config] ;; <-- NEW require
            [failter.eval :as feval]
            [failter.exp-paths :as exp-paths]
            [failter.log :as log]
            [failter.scoring :as scoring])) ;; <-- NEW require

(defn calculate-summary [evals]
  (let [strategy (get-in config/config [:evaluator :scoring-strategy])
        first-eval (first evals)
        first-trial (:trial first-eval)
        scores (keep :score evals) ;; <-- Scores are now numeric
        times  (keep #(:execution-time-ms (:trial %)) evals)
        costs  (keep #(:estimated-cost (:trial %)) evals)
        errors (filter #(-> % :trial :error) evals)
        methods (frequencies (keep :method evals))]
    {:model (:model-name first-trial)
     :template (.getName (io/file (:template-path first-trial)))
     :trials (count evals)
     :errors (count errors)
     ;; Avg score is now a simple average.
     :avg-score (if (seq scores) (/ (double (apply + scores)) (count scores)) 0.0)
     :avg-time-s (if (seq times) (/ (double (apply + times)) 1000 (count times)) 0.0)
     :avg-cost (if (and (seq costs) (every? some? costs)) (/ (double (apply + costs)) (count costs)) 0.0)
     ;; Use the scoring strategy to format the distribution.
     :score-dist (scoring/format-score-distribution strategy scores)
     :eval-methods methods}))

(defn- prepare-summary-for-display
  [{:keys [model template avg-score avg-time-s avg-cost trials errors score-dist eval-methods]}]
  {:model model
   :template template
   :avg-score (format "%.2f" avg-score)
   :avg-time-s (format "%.2f" avg-time-s)
   :avg-cost (format "$%.6f" avg-cost)
   :trials (str trials)
   :errors (str errors)
   :score-dist score-dist ;; <-- Use the new score distribution string
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
                          "Score Distribution"]) ;; <-- Renamed column
        separator (str/join "" (repeat 155 "-"))
        rows (for [{:keys [model template avg-score avg-time-s avg-cost trials errors score-dist eval-methods]} display-summaries]
               (str/join " | "
                         [(format "%-28s" model)
                          (format "%-20s" template)
                          (format "%-10s" avg-score)
                          (format "%-10s" avg-time-s)
                          (format "%-10s" avg-cost)
                          (format "%-7s" trials)
                          (format "%-6s" errors)
                          (format "%-18s" eval-methods)
                          score-dist]))]
    (str/join "\n" (concat [header separator] rows))))

(defn- format-as-csv [display-summaries]
  (let [header ["Model" "Template" "Avg_Score" "Avg_Time_s" "Avg_Cost" "Trials" "Errors" "Eval_Methods" "Score_Distribution"]
        rows (for [{:keys [model template avg-score avg-time-s avg-cost trials errors score-dist eval-methods]} display-summaries]
               [model template avg-score avg-time-s avg-cost trials errors eval-methods score-dist])
        data-to-write (cons header rows)]
    (csv/write-csv data-to-write)))

(defn generate-report [experiment-dir]
  (log/info (str "Generating report for experiment: " experiment-dir))
  (let [evals (feval/read-all-evals experiment-dir)
        combo-key (fn [e] [(-> e :trial :model-name) (-> e :trial :template-path)])
        summaries (->> evals
                       (group-by combo-key)
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

    (log/info "--- Experiment Report ---")
    (log/info table-string)

    (log/info (str "Writing markdown report to: " report-md-path))
    (spit report-md-path table-string)

    (log/info (str "Writing CSV report to:      " report-csv-path))
    (spit report-csv-path csv-string)
    (log/info "Report generation complete.")))
