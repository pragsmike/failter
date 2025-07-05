(ns failter.reporter-test
  (:require [clojure.test :refer :all]
            [failter.reporter :as reporter]
            [failter.trial :as trial]
            [failter.eval :as feval]
            [failter.config :as config]
            [clojure.edn :as edn]))

(defn- double-equals?
  "Helper for robust floating point comparison."
  [a b]
  (< (Math/abs (- a b)) 1e-9))

(deftest calculate-summary-test
  (testing "Calculating summary for a set of Eval records"
    (let [;; Test data that accurately reflects a real experiment's state
          trial-1 (trial/->Trial "model-a" "template-1.md" "in1.md" "out1.md" 1000 {} 0.001 nil)
          eval-1  (feval/->Eval trial-1 100 "Rationale A" "ground-truth" "judge-1" nil)

          trial-2 (trial/->Trial "model-a" "template-1.md" "in2.md" "out2.md" 2000 {} 0.002 nil)
          eval-2  (feval/->Eval trial-2 80 "Rationale B" "ground-truth" "judge-1" nil)

          trial-3 (trial/->Trial "model-a" "template-1.md" "in3.md" "out3.md" 60000 nil nil "Read timed out")
          eval-3  (feval/->Eval trial-3 0 "Read timed out" "failed" nil "Read timed out")

          trial-4 (trial/->Trial "model-a" "template-1.md" "in4.md" "out4.md" 500 {} nil nil)
          eval-4  (feval/->Eval trial-4 nil nil "unevaluated" nil nil)

          trial-5 (trial/->Trial "model-a" "template-1.md" "in5.md" "out5.md" 1500 {} 0.0015 nil)
          eval-5  (feval/->Eval trial-5 60 "Rationale C" "rules-based" "judge-1" nil)

          evals [eval-1 eval-2 eval-3 eval-4 eval-5]]

      (with-redefs [config/config (assoc-in config/config [:evaluator :scoring-strategy] :letter-grade)]
        (let [summary (reporter/calculate-summary evals)
              ;; Parse the result string back into a map for robust comparison
              actual-dist-map (edn/read-string (:score-dist summary))]

          (is (= 5 (:trials summary)))
          (is (= 1 (:errors summary)))
          ;; Scores being averaged: [100, 80, 0, 60]. Avg = 240 / 4 = 60.0
          (is (= 60.0 (:avg-score summary)))

          ;; --- FIX: The assertion now correctly checks the map produced by the fixed scoring code ---
          ;; The expected distribution from scores (100, 80, 0, 60) is one of each grade.
          (is (= {"A" 1, "B" 1, "C" 1, "F*" 1} actual-dist-map)))))))
