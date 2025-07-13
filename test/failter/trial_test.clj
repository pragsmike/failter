(ns failter.trial-test
  (:require [clojure.test :refer :all]
            [failter.trial :as trial]
            [failter.frontmatter :as fm]
            [clojure.java.io :as io]))

(def ^:private temp-dir-name "temp-trial-test")

(defn- delete-recursively [file]
  (when (.isDirectory file)
    (doseq [f (.listFiles file)]
      (delete-recursively f)))
  (io/delete-file file))

(defn- test-file-fixture [f]
  (try
    (.mkdirs (io/file temp-dir-name))
    (f)
    (finally
      (delete-recursively (io/file temp-dir-name)))))

(use-fixtures :once test-file-fixture)

(deftest from-file-test
  (testing "Correctly reads a successful trial result with full metadata"
    (let [output-file (io/file temp-dir-name "successful-trial.md")
          metadata {:filtered-by-model "ollama/test-model"
                    :filtered-by-template "P1.md"
                    :source-input-path "/path/to/inputs/input.txt"
                    :source-template-path "/path/to/templates/P1.md"
                    :execution-time-ms 5000
                    :total-trial-time-ms 12000
                    :retry-attempts 2
                    :errors-on-retry ["Timeout" "Service Unavailable"]
                    :token-usage {:prompt_tokens 100 :completion_tokens 50}}
          body "This is the successful output."
          content (fm/serialize metadata body)]
      (spit output-file content)

      (let [t (trial/from-file output-file)]
        (is (= "ollama/test-model" (:model-name t)))
        (is (= "P1.md" (:template-path t)))
        (is (= 5000 (:execution-time-ms t)))
        (is (= 12000 (:total-trial-time-ms t)))
        (is (= 2 (:retry-attempts t)))
        (is (= 100 (:tokens-in t)))
        (is (= 50 (:tokens-out t)))
        (is (nil? (:error t)))
        ;; --- Verify source provenance is read correctly ---
        (is (= "/path/to/inputs/input.txt" (:source-input-path t)))
        (is (= "/path/to/templates/P1.md" (:source-template-path t)))))))
