(ns failter.reporter-test
  (:require [clojure.test :refer :all]
            [failter.reporter :as reporter]
            [failter.trial :as trial]
            [failter.eval :as feval]))

(defn- double-equals?
  "Helper for robust floating point comparison."
  [a b]
  (< (Math/abs (- a b)) 1e-9))

(deftest calculate-summary-test
  (testing "Calculating summary for a set of Eval records"
    (let [;; --- THIS IS THE FIX ---
          ;; We now construct proper Trial and Eval records for the test.
          trial-1 (trial/->Trial "model-a" "template-1.md" "in.md" "out.md" 1000 nil 0.001 nil)
          trial-2 (trial/->Trial "model-a" "template-1.md" "in.md" "out.md" 2000 nil 0.002 nil)
          trial-3 (trial/->Trial "model-a" "template-1.md" "in.md" "out.md" 60000 nil nil "Read timed out")
          trial-4 (trial/->Trial "model-a" "template-1.md" "in.md" "out.md" 500 nil nil nil)
          trial-5 (trial/->Trial "model-a" "template-1.md" "in.md" "out.md" 1500 nil 0.0015 nil)

          evals [(feval/->Eval trial-1 "A" "Rationale A" "gt" "judge" nil)
                 (feval/->Eval trial-2 "B" "Rationale B" "gt" "judge" nil)
                 (feval/->Eval trial-3 "F" "Read timed out" "failed" nil nil)
                 (feval/->Eval trial-4 nil nil "unevaluated" nil nil)
                 (feval/->Eval trial-5 "A" "Rationale A2" "gt" "judge" nil)]

          ;; The function under test now takes the sequence of Eval records
          summary (reporter/calculate-summary evals)]

      (is (= "model-a" (:model summary)))
      (is (= "template-1.md" (:template summary)))
      (is (= 5 (:trials summary)))
      (is (= 1 (:errors summary)))

      (is (= 3.75 (:avg-score summary)) "Average score should be (5+4+1)/3")
      (is (= 13.0 (:avg-time-s summary)) "Average time should be (1+2+60+0.5+1.5)/5")
      (is (double-equals? 0.0015 (:avg-cost summary)) "Average cost should be (0.001+0.002+0.0015)/3")
      (is (= {"A" 2, "B" 1, "F" 1} (:grade-dist summary)))
      (is (= {"gt" 3, "failed" 1, "unevaluated" 1} (:eval-methods summary))))))
