(ns failter.runner-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
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

(deftest run-single-trial-test
  (testing "Correctly processes templates with and without frontmatter"
    (let [template-with-fm-body "Prompt body from FM template. Input: {{INPUT_TEXT}}"
          template-with-fm-content (str "---\nid: P123\n---\n" template-with-fm-body)
          template-no-fm-content "Prompt body from plain template. Input: {{INPUT_TEXT}}"
          input-content (str "---\nauthor: Tester\n---\nOriginal input body.")
          mock-llm-response {:content "Mocked LLM response body."
                             :usage {:total_tokens 100}
                             :cost 0.0001}
          ;; --- FIX: Construct full, absolute paths to all test files ---
          temp-dir-abs-path (.getAbsolutePath (io/file temp-dir-name))
          input-path (.getAbsolutePath (io/file temp-dir-name "inputs" "input.txt"))
          template-fm-path (.getAbsolutePath (io/file temp-dir-name "templates" "template-with-fm.md"))
          template-no-fm-path (.getAbsolutePath (io/file temp-dir-name "templates" "template-no-fm.md"))]

      (spit input-path input-content)
      (spit template-fm-path template-with-fm-content)
      (spit template-no-fm-path template-no-fm-content)

      (with-redefs [failter.llm-interface/call-model
                    (fn [_ final-prompt & _]
                      (if (str/includes? final-prompt "FM template")
                        (is (= "Prompt body from FM template. Input: Original input body." final-prompt))
                        (is (= "Prompt body from plain template. Input: Original input body." final-prompt)))
                      mock-llm-response)]

        (testing "when template has frontmatter"
          ;; --- FIX: Use the absolute paths when creating the Trial record ---
          (let [t (trial/new-trial temp-dir-abs-path "ollama/test" template-fm-path input-path)]
            (runner/run-single-trial t)
            (let [result-content (slurp (:output-path t))
                  {:keys [frontmatter body]} (fm/parse-file-content result-content)]
              (is (= (:content mock-llm-response) body))
              (is (= "Tester" (:author frontmatter))))))

        (testing "when template has no frontmatter"
          (let [t (trial/new-trial temp-dir-abs-path "ollama/test" template-no-fm-path input-path)]
            (runner/run-single-trial t)
            (let [result-content (slurp (:output-path t))
                  {:keys [frontmatter body]} (fm/parse-file-content result-content)]
              (is (= (:content mock-llm-response) body))
              (is (= "Tester" (:author frontmatter))))))))))
