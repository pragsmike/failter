(ns failter.evaluator-test
  (:require [clojure.test :refer :all]
            [failter.evaluator :as evaluator]
            [failter.trial :as trial]
            [failter.frontmatter :as fm]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private test-root-dir "temp-evaluator-test")
(def ^:private inputs-dir (io/file test-root-dir "source" "inputs"))
(def ^:private templates-dir (io/file test-root-dir "source" "templates"))
(def ^:private gt-dir (io/file test-root-dir "source" "ground_truth"))
(def ^:private artifacts-dir (io/file test-root-dir "artifacts"))

(defn- setup-test-environment! []
  (.mkdirs inputs-dir)
  (.mkdirs templates-dir)
  (.mkdirs gt-dir)
  (.mkdirs (io/file artifacts-dir "model_template")))

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

(deftest build-evaluation-context-test
  (testing "Correctly builds context using source paths from the trial record"
    (let [;; --- Define source files and their content ---
          input-path (.getAbsolutePath (io/file inputs-dir "input.txt"))
          template-path (.getAbsolutePath (io/file templates-dir "template.md"))
          gt-path (.getAbsolutePath (io/file gt-dir "input.txt"))
          input-content "The original input body."
          template-body "The real prompt body."
          template-with-fm (str "---\nid: P1\n---\n" template-body)
          gt-content "The perfect output."
          output-body "The model-generated output."
          ;; --- Create the artifact file that the runner would produce ---
          artifact-path (.getAbsolutePath (io/file artifacts-dir "model_template" "input.txt"))
          artifact-metadata {:filtered-by-model "ollama/test"
                             :filtered-by-template "template.md"
                             :source-input-path input-path     ;; Critical piece of data
                             :source-template-path template-path ;; Critical piece of data
                             }
          artifact-content (fm/serialize artifact-metadata output-body)]

      (spit input-path input-content)
      (spit template-path template-with-fm)
      (spit gt-path gt-content)
      (spit artifact-path artifact-content)

      ;; Now, read the trial record from the artifact file
      (let [t (trial/from-file (io/file artifact-path))
            ;; Access the private function for focused unit testing
            build-fn #'evaluator/build-evaluation-context
            context (build-fn t)]

        (is (:valid? context))
        (is (= input-content (:input-content context)) "Should read the body of the original input file.")
        (is (= output-body (:output-content context)) "Should read the body of the artifact file.")
        (is (= template-body (:template-content context)) "Should use only the body of the template, ignoring its frontmatter.")
        (is (= true (:has-ground-truth? context)) "Should find the ground truth file.")
        (is (= gt-content (:ground-truth-content context)) "Should correctly read the ground truth content.")))))
