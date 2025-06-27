(ns failter.trial
  (:require [failter.exp-paths :as exp-paths]
            [failter.frontmatter :as fm]))

(defrecord Trial
  [model-name template-path input-path output-path ;; Plan
   execution-time-ms token-usage estimated-cost error]) ;; Result

(defn new-trial
  "Constructor for a planned Trial. Computes the output path but performs no I/O."
  [experiment-dir model-name template-path input-path]
  (let [output-path (exp-paths/output-path-for-trial experiment-dir model-name template-path input-path)]
    (->Trial model-name template-path input-path output-path nil nil nil nil )))

(defn from-file
  "Constructs a completed Trial record by reading its output file from disk."
  [output-file]
  (let [output-path (.getPath output-file)
        metadata    (:frontmatter (fm/parse-file-content (slurp output-file)))]
    (->Trial (:filtered-by-model metadata)
             (:filtered-by-template metadata)
             nil ;; Input path isn't stored in the result file, so it's not needed here
             output-path
             (:execution-time-ms metadata)
             (:token-usage metadata)
             (:estimated-cost metadata)
             (:error metadata))))
