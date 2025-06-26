(ns failter.runner
  (:require [failter.llm-interface :as llm]
            [failter.frontmatter :as fm]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn run-single-trial
  "Executes a single filtering trial, now with frontmatter awareness."
  [{:keys [model-name template-path input-path output-path timeout]
    :or {timeout 600000}}]
  (try
    (println "--- Running Trial (Frontmatter-aware) ---")
    (println (str "  Model: " model-name))
    (println (str "  Template: " template-path))
    (println (str "  Input: " input-path))

    (let [;; 1. Parse input file into frontmatter and body
          input-content (slurp input-path)
          {:keys [frontmatter body]} (fm/parse-file-content input-content)

          ;; 2. Prepare and send ONLY the body to the LLM
          prompt-template (slurp template-path)
          final-prompt (str/replace prompt-template "{{INPUT_TEXT}}" body)
          _ (println (str "  Sending request to LLM (" model-name ")..."))
          filtered-body (llm/call-model model-name final-prompt :timeout timeout)]

      (if (str/starts-with? filtered-body "{\"error\":")
        (do
          (println "--- ERROR FROM LLM INTERFACE ---")
          (println filtered-body))
        (do
          ;; 3. Amend metadata and re-serialize with filtered body
          (let [updated-frontmatter (assoc frontmatter
                                           :filtered-by-model model-name
                                           :filtered-by-template (.getName (io/file template-path)))
                final-output-content (fm/serialize updated-frontmatter filtered-body)]

            (io/make-parents output-path)
            (println (str "  Writing LLM response with updated frontmatter to: " output-path))
            (spit output-path final-output-content)
            (println "--- Trial Complete ---\n")))))

    (catch java.io.FileNotFoundException e
      (println (str "ERROR: File not found during trial - " (.getMessage e))))
    (catch Exception e
      (println (str "ERROR: An unexpected error occurred during trial: " (.getMessage e))))))

(defn live-trial-runner
  "A wrapper function that matches the signature for conduct-experiment
  and calls the main trial execution logic."
  [params]
  (run-single-trial params))
