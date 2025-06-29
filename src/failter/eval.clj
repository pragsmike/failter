(ns failter.eval
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [failter.exp-paths :as exp-paths]
            [failter.trial :as trial]
            ))

;; The core data structure now stores a numeric score.
(defrecord Eval [trial score rationale method judge-model error])

(defn write-eval
  "Writes an Eval record to its corresponding .eval file."
  [^Eval e]
  (let [output-path (-> e :trial :output-path)
        eval-file-path (exp-paths/eval-path output-path)
        ;; The .eval file now contains the numeric score.
        final-content (str "score: " (:score e) "\n"
                           "rationale: " (:rationale e) "\n"
                           "evaluation-method: " (:method e) "\n"
                           "judge-model: " (:judge-model e) "\n")]
    (spit eval-file-path final-content)))

(defn read-eval
  "Reads a result file and its corresponding .eval file into a single Eval record."
  [output-file]
  (let [t (trial/from-file output-file)
        eval-file (io/file (exp-paths/eval-path (.getPath output-file)))]
    (cond
      (:error t)
      ;; A failed trial gets a score of 0.
      (->Eval t 0 (:error t) "failed" nil nil)

      (.exists eval-file)
      (let [eval-content (slurp eval-file)
            ;; We now parse 'score' as an integer.
            score (try (some-> (re-find #"(?m)^score:\s*(\d+)" eval-content) second Integer/parseInt) (catch Exception _ 0))
            method (second (re-find #"(?m)^evaluation-method:\s*(\S+)" eval-content))
            judge (second (re-find #"(?m)^judge-model:\s*(\S+)" eval-content))
            rationale (second (re-find #"(?ms)^rationale:\s*(.*)" eval-content))]
        (->Eval t score (str/trim rationale) method judge nil))

      :else
      (->Eval t nil nil "unevaluated" nil nil))))

(defn read-all-evals
  "Reads all evaluations from an experiment directory into a sequence of Eval records."
  [experiment-dir]
  (->> (exp-paths/find-all-result-files experiment-dir)
       (map read-eval)
       (remove nil?)))
