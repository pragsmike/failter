(ns failter.reporter-test
  (:require [clojure.test :refer :all]
            [failter.reporter]))

(def calculate-summary (ns-resolve 'failter.reporter 'calculate-summary))

;; --- FIX: Helper for robust floating point comparison ---
(defn- double-equals? [a b]
  (< (Math/abs (- a b)) 1e-9))

(deftest calculate-summary-test
  (testing "Calculating summary for a set of trial results"
    (let [trial-results [;; Successful A grade
                         {:filtered-by-model "model-a"
                          :filtered-by-template "template-1.md"
                          :execution-time-ms 1000
                          :estimated-cost 0.001
                          :grade "A"}
                         ;; Successful B grade
                         {:filtered-by-model "model-a"
                          :filtered-by-template "template-1.md"
                          :execution-time-ms 2000
                          :estimated-cost 0.002
                          :grade "B"}
                         ;; Failed trial (timeout)
                         {:filtered-by-model "model-a"
                          :filtered-by-template "template-1.md"
                          :execution-time-ms 60000
                          :error "Read timed out"
                          :grade "F"} ; Synthesized grade
                         ;; Successful but unevaluated trial (no grade)
                         {:filtered-by-model "model-a"
                          :filtered-by-template "template-1.md"
                          :execution-time-ms 500}
                         ;; Another A grade
                         {:filtered-by-model "model-a"
                          :filtered-by-template "template-1.md"
                          :execution-time-ms 1500
                          :estimated-cost 0.0015
                          :grade "A"}]
          summary (@calculate-summary trial-results)]

      (is (= "model-a" (:model summary)))
      (is (= "template-1.md" (:template summary)))
      (is (= 5 (:trials summary)))
      (is (= 1 (:errors summary)))

      (is (= 3.75 (:avg-score summary)))
      (is (= 13.0 (:avg-time-s summary)))
      (is (double-equals? 0.0015 (:avg-cost summary)))
      (is (= {"A" 2, "B" 1, "F" 1} (:grade-dist summary)))))

  (testing "Calculating summary with no results"
    (let [summary (@calculate-summary [])]
      (is (nil? summary) "Should return nil for empty input"))))
