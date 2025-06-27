(ns failter.eval
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [failter.exp-paths :as exp-paths]
            [failter.trial :as trial]
            [failter.util :as util]))

(defrecord Eval [trial grade rationale method judge-model error])

(defn write-eval
  "Writes an Eval record to its corresponding .eval file."
  [^Eval e]
  (let [output-path (-> e :trial :output-path)
        eval-file-path (exp-paths/eval-path output-path)
        ;; Create a map with only the data that belongs in the .eval file
        eval-data {:grade (:grade e)
                   :rationale (:rationale e)
                   :evaluation-method (:method e)
                   :judge-model (:judge-model e)}
        content (str (util/parse-yaml-block (:rationale e)) "\n")
        final-content (str "grade: " (:grade e) "\n"
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
      (->Eval t "F" (:error t) "failed" nil nil)

      (.exists eval-file)
      (let [eval-content (slurp eval-file)
            grade (second (re-find #"(?m)^grade:\s*([A-DF])" eval-content))
            method (second (re-find #"(?m)^evaluation-method:\s*(\S+)" eval-content))
            judge (second (re-find #"(?m)^judge-model:\s*(\S+)" eval-content))
            rationale (second (re-find #"(?ms)^rationale:\s*(.*)" eval-content))]
        (->Eval t grade (str/trim rationale) method judge nil))

      :else
      (->Eval t nil nil "unevaluated" nil nil))))

(defn read-all-evals
  "Reads all evaluations from an experiment directory into a sequence of Eval records."
  [experiment-dir]
  (->> (exp-paths/find-all-result-files experiment-dir)
       (map read-eval)
       (remove nil?)))
