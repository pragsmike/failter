(ns failter.reporter-test
  (:require [clojure.test :refer :all]
            [failter.reporter :as reporter]
            [failter.trial :as trial]
            [failter.eval :as feval]))

(deftest generate-report-data-test
  (testing "Correctly transforms Eval records into the final JSON data structure"
    (let [;; --- Test Data Setup ---
          ;; These records now include the source-template-path and source-input-path fields.
          trial-success (trial/->Trial "model-a" "templates/P1.md" "in1.md" "out1.md"
                                       "templates/P1.md" "in1.md" ;; Correct arity
                                       1000 2000 0 [] 150 75 nil)

          trial-retried (trial/->Trial "model-a" "templates/P2.md" "in2.md" "out2.md"
                                       "templates/P2.md" "in2.md" ;; Correct arity
                                       5000 15000 2 ["Timeout" "503"] 300 150 nil)

          trial-failed (trial/->Trial "model-a" "templates/P3.md" "in3.md" "out3.md"
                                      "templates/P3.md" "in3.md" ;; Correct arity
                                      nil nil nil nil nil nil "Final attempt failed.")

          evals [(feval/->Eval trial-success 95 "Rationale A" "ground-truth" "judge-1" nil)
                 (feval/->Eval trial-retried 80 "Rationale B" "rules-based" "judge-1" nil)
                 ;; For a failed trial, score is 0 and error is populated from the trial record.
                 (feval/->Eval trial-failed 0 "Trial failed: Final attempt failed." "failed" nil "Final attempt failed.")]]

      ;; Mock the function that reads from disk to isolate the transformation logic.
      (with-redefs [failter.eval/read-all-evals (fn [_] evals)]
        (let [report-data (reporter/generate-report-data "dummy-dir")
              p1-data (first (filter #(= "P1.md" (:prompt_id %)) report-data))
              p2-data (first (filter #(= "P2.md" (:prompt_id %)) report-data))
              p3-data (first (filter #(= "P3.md" (:prompt_id %)) report-data))]

          ;; --- Assertions for Successful Trial (P1) ---
          (is (= 95 (:score p1-data)))
          (is (= {:model_used "model-a" :tokens_in 150 :tokens_out 75} (:usage p1-data)))
          (is (= {:execution_time_ms 1000 :total_trial_time_ms 2000 :retry_attempts 0} (:performance p1-data)))
          (is (nil? (:error p1-data)))

          ;; --- Assertions for Retried Trial (P2) ---
          (is (= 80 (:score p2-data)))
          (is (= {:model_used "model-a" :tokens_in 300 :tokens_out 150} (:usage p2-data)))
          (is (= {:execution_time_ms 5000 :total_trial_time_ms 15000 :retry_attempts 2} (:performance p2-data)))
          (is (nil? (:error p2-data)))

          ;; --- Assertions for Failed Trial (P3) ---
          (is (nil? (:score p3-data)))
          (is (= {:model_used "model-a" :tokens_in nil :tokens_out nil} (:usage p3-data)))
          (is (some? (:error p3-data)))
          (is (= "Trial failed: Final attempt failed." (:error p3-data))))))))
