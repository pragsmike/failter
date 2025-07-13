(ns failter.runner-test
  (:require [clojure.test :refer :all]
            [failter.runner :as runner]
            [failter.trial :as trial]
            [failter.frontmatter :as fm]
            [failter.llm-interface :as llm]
            [clojure.java.io :as io]))

(def ^:private temp-dir-name "temp-runner-test-exp")

(defn- create-test-files! []
  (let [base-dir (io/file temp-dir-name)]
    (.mkdirs (io/file base-dir "inputs"))
    (.mkdirs (io/file base-dir "templates"))))

(defn- delete-recursively [file]
  (when (.isDirectory file)
    (doseq [f (.listFiles file)]
      (delete-recursively f)))
  (io/delete-file file))

(defn- test-file-fixture [f]
  (try
    (create-test-files!)
    (f)
    (finally
      (delete-recursively (io/file temp-dir-name)))))

(use-fixtures :once test-file-fixture)

(deftest run-single-trial-retry-logic-test
  (let [template-content "Prompt: {{INPUT_TEXT}}"
        input-content "Original input."
        ;; --- Use absolute paths for robustness ---
        artifacts-dir-abs-path (.getAbsolutePath (io/file temp-dir-name "artifacts"))
        input-path (.getAbsolutePath (io/file temp-dir-name "inputs" "input.txt"))
        template-path (.getAbsolutePath (io/file temp-dir-name "templates" "template.md"))
        ;; --- Mock Responses ---
        fail-response-1 {:error "API call timed out"}
        success-response {:content "Mocked LLM response."
                          :usage {:prompt_tokens 20, :completion_tokens 10}}]

    (spit input-path input-content)
    (spit template-path template-content)

    (testing "when trial succeeds, it writes full source provenance to the artifact"
      (let [call-count (atom 0)
            mock-responses [fail-response-1 success-response]
            t (trial/new-trial artifacts-dir-abs-path "ollama/test" template-path input-path)]
        (with-redefs [failter.llm-interface/call-model
                      (fn [_ _ & _]
                        (let [response (get mock-responses @call-count)]
                          (swap! call-count inc)
                          (assoc response :execution-time-ms 500)))]

          (runner/run-single-trial t {:retries 2})

          (let [result-content (slurp (:output-path t))
                {:keys [frontmatter body]} (fm/parse-file-content result-content)]

            (is (= "Mocked LLM response." body))
            (is (= 1 (:retry-attempts frontmatter)) "Should record 1 failed attempt")
            ;; --- Verify source provenance is written correctly ---
            (is (= input-path (:source-input-path frontmatter)))
            (is (= template-path (:source-template-path frontmatter)))
            (is (= {:prompt_tokens 20, :completion_tokens 10} (:token-usage frontmatter)))))))))

