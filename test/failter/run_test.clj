(ns failter.run-test
  (:require [clojure.test :refer :all]
            [failter.run :as run]
            [failter.frontmatter :as fm]
            [clojure.java.io :as io]))

(def ^:private test-root-dir "temp-run-test-dir")
(def ^:private inputs-dir (io/file test-root-dir "inputs"))
(def ^:private templates-dir (io/file test-root-dir "templates"))
(def ^:private artifacts-dir (io/file test-root-dir "artifacts"))
(def ^:private spec-file (io/file test-root-dir "spec.yml"))
(def ^:private report-file (io/file test-root-dir "report.json"))

(defn- setup-test-environment! []
  (.mkdirs inputs-dir)
  (.mkdirs templates-dir)
  (spit (io/file inputs-dir "input1.txt") "Input text.")
  (spit (io/file templates-dir "P1.md") "Template P1: {{INPUT_TEXT}}")
  (spit (io/file templates-dir "P2.md") "Template P2: {{INPUT_TEXT}}")
  (spit spec-file
        (str "version: 2\n"
             "inputs_dir: " (.getAbsolutePath inputs-dir) "\n"
             "templates_dir: " (.getAbsolutePath templates-dir) "\n"
             "artifacts_dir: " (.getAbsolutePath artifacts-dir) "\n"
             "output_file: " (.getAbsolutePath report-file) "\n"
             "judge_model: ollama/judge\n"
             "templates: [P1.md, P2.md]\n"
             "models: [ollama/model-1]\n")))

(defn- cleanup-test-environment! []
  (when (.exists (io/file test-root-dir))
    (doseq [f (reverse (file-seq (io/file test-root-dir)))]
      (io/delete-file f))))

(defn- test-fixture [f]
  (try
    (cleanup-test-environment!)
    (setup-test-environment!)
    (f)
    (finally
      (cleanup-test-environment!))))

(use-fixtures :each test-fixture)

(deftest execute-run-orchestration-test
  (testing "The full run command orchestrates all components correctly and is idempotent"
    (let [runner-call-count (atom 0)
          evaluator-call-count (atom 0)]

      (with-redefs [;; Mock the runner to just create a dummy artifact file
                    failter.runner/run-single-trial
                    (fn [trial _]
                      (swap! runner-call-count inc)
                      (let [metadata {:filtered-by-model (:model-name trial)
                                      :filtered-by-template (.getName (io/file (:template-path trial)))
                                      :token-usage {:prompt_tokens 10 :completion_tokens 5}}]
                        (spit (:output-path trial) (fm/serialize metadata "body"))))

                    ;; Mock the evaluator to create a dummy .eval file
                    failter.evaluator/run-evaluation
                    (fn [arts-dir & _]
                      (swap! evaluator-call-count inc)
                      (doseq [f (failter.exp-paths/find-all-result-files arts-dir)]
                        (let [eval-path (failter.exp-paths/eval-path (.getPath f))]
                          (spit eval-path "score: 99\nrationale: Mock eval."))))]

        ;; --- First Run ---
        (let [output (with-out-str (run/execute-run (.getPath spec-file)))]
          (is (= 2 @runner-call-count) "Runner should be called for both trials on the first run")
          (is (= 1 @evaluator-call-count) "Evaluator should be called once on the first run")
          (is (clojure.string/includes? output "\"prompt_id\":\"P1.md\""))
          (is (clojure.string/includes? output "\"score\":99"))
          (is (.exists report-file) "Should create the report file specified in the spec"))

        ;; --- Second Run (Idempotency Check) ---
        (let [output (with-out-str (run/execute-run (.getPath spec-file)))]
          (is (= 2 @runner-call-count) "Runner should NOT be called again on the second run")
          (is (= 2 @evaluator-call-count) "Evaluator IS called again, as its idempotency is internal")
          (is (clojure.string/includes? output "\"prompt_id\":\"P1.md\"") "Should still produce a report"))))))

(deftest load-spec-validation-test
  (testing "Spec loading throws an error if a required key is missing"
    (spit spec-file "models: [a,b,c]")
    (is (thrown? IllegalArgumentException (run/execute-run (.getPath spec-file))))))
