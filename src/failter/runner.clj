(ns failter.runner
  (:require [failter.llm-interface :as llm]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn run-single-trial
  "Executes a single filtering trial.
  Reads input and template, calls the LLM, and writes the output.
  Params map keys:
  - :model-name (String) - The name of the LLM to use.
  - :template-path (String) - Path to the prompt template file.
  - :input-path (String) - Path to the input text file.
  - :output-path (String) - Path to write the final output.
  - :timeout (Long, optional) - Timeout in milliseconds for the LLM call."
  [{:keys [model-name template-path input-path output-path timeout]
    :or {timeout 600000}}] ; Set a default timeout for all trials
  (try
    (println "--- Running Trial ---")
    (println (str "  Model: " model-name))
    (println (str "  Template: " template-path))
    (println (str "  Input: " input-path))

    (let [prompt-template (slurp template-path)
          input-content (slurp input-path)
          final-prompt (str/replace prompt-template
                                    "{{INPUT_TEXT}}"
                                    input-content)
          _ (println (str "  Sending request to LLM (" model-name ")..."))
          llm-response (llm/call-model model-name final-prompt :timeout timeout)]

      (if (str/starts-with? llm-response "{\"error\":")
        (do
          (println "--- ERROR FROM LLM INTERFACE ---")
          (println llm-response)
          (println "------------------------------------"))
        (do
          (println (str "  Ensuring output directory exists for: " output-path))
          (io/make-parents output-path)
          (println (str "  Writing LLM response to: " output-path))
          (spit output-path llm-response)
          (println "--- Trial Complete ---\n"))))

    (catch java.io.FileNotFoundException e
      (println (str "ERROR: File not found during trial - " (.getMessage e))))
    (catch Exception e
      (println (str "ERROR: An unexpected error occurred during trial: " (.getMessage e))))))

;; --- Bridge function for the experiment orchestrator ---

(defn live-trial-runner
  "A wrapper function that matches the signature for conduct-experiment
  and calls the main trial execution logic."
  [params]
  (run-single-trial params))
