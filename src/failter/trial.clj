(ns failter.trial
  (:require [failter.exp-paths :as exp-paths]))

(defrecord Trial
  [model-name template-path input-path output-path])

(defn new-trial
  "Constructor for a planned Trial. Computes the output path but performs no I/O."
  [experiment-dir model-name template-path input-path]
  (let [output-path (exp-paths/output-path-for-trial experiment-dir model-name template-path input-path)]
    (->Trial model-name template-path input-path output-path)))
