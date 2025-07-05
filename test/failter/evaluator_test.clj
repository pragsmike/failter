(ns failter.evaluator-test
  (:require [clojure.test :refer :all]
            [failter.evaluator :as evaluator]
            [failter.trial :as trial]
            [failter.frontmatter :as fm]
            [clojure.java.io :as io]))

(def ^:private temp-dir-name "temp-evaluator-test-exp")

(defn- create-test-files! []
  (let [base-dir (io/file temp-dir-name)]
    (.mkdirs (io/file base-dir "inputs"))
    (.mkdirs (io/file base-dir "templates"))
    (.mkdirs (io/file base-dir "results" "test-model_template-with-fm"))
    (.mkdirs (io/file base-dir "results" "test-model_template-no-fm")))

  (spit (io/file temp-dir-name "inputs" "input.txt") "Original input text.")

  (let [output-metadata {:filtered-by-model "test-model" :filtered-by-template "template-with-fm.md"}
        output-body "Generated body text."]
    (spit (io/file temp-dir-name "results" "test-model_template-with-fm" "input.txt")
          (fm/serialize output-metadata output-body)))

  (let [output-metadata {:filtered-by-model "test-model" :filtered-by-template "template-no-fm.md"}
        output-body "Generated body text."]
    (spit (io/file temp-dir-name "results" "test-model_template-no-fm" "input.txt")
          (fm/serialize output-metadata output-body))))


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

(deftest build-evaluation-context-test
  (testing "Correctly extracts template body from template file"
    (let [template-with-fm-body "This is the REAL prompt. {{INPUT_TEXT}}"
          template-with-fm-content (str "---\nid: P456\n---\n" template-with-fm-body)
          template-no-fm-content "This is a plain old template. {{INPUT_TEXT}}"]

      ;; Create the test templates on the fly
      (spit (io/file temp-dir-name "templates" "template-with-fm.md") template-with-fm-content)
      (spit (io/file temp-dir-name "templates" "template-no-fm.md") template-no-fm-content)

      (testing "when template has frontmatter"
        (let [result-file (io/file temp-dir-name "results" "test-model_template-with-fm" "input.txt")
              trial-fm (trial/from-file result-file)
              ;; Accessing the private function for focused unit testing
              build-fn #'evaluator/build-evaluation-context
              context (build-fn temp-dir-name trial-fm)]
          (is (= template-with-fm-body (:template-content context))
              "Should only use the body of the template, ignoring its frontmatter.")))

      (testing "when template has no frontmatter"
        (let [result-file (io/file temp-dir-name "results" "test-model_template-no-fm" "input.txt")
              trial-no-fm (trial/from-file result-file)
              build-fn #'evaluator/build-evaluation-context
              context (build-fn temp-dir-name trial-no-fm)]
          (is (= template-no-fm-content (:template-content context))
              "Should use the entire file content when no frontmatter is present."))))))
