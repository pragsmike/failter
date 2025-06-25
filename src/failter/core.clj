(ns failter.core
  (:require [failter.llm-interface :as llm]
            [clojure.string :as str])
  (:gen-class))

(def MODEL "ollama/qwen3:32b")
#_(def MODEL "ollama/mistral-nemo:12b")
(def TEMPLATE "prompts/cleanup-small-model.md")
#_(def TEMPLATE "prompts/cleanup-prompt.md")

(defn -main [& args]
  ;; First, ensure the environment is configured correctly.
  (llm/pre-flight-checks)

  ;; Check for the correct number of command-line arguments.
  (if (not= (count args) 2)
    (do
      (println "Usage: failter <input-file> <output-file>")
      (System/exit 1))
    ;; If arguments are correct, proceed with the full workflow.
    (let [[input-file output-file] args
          prompt-template-path TEMPLATE
          model-name MODEL]
      (println "--- FAILTER ---")
      (try
        (println (str "Reading prompt template from: " prompt-template-path))
        (let [prompt-template (slurp prompt-template-path)
              _ (println (str "Reading input content from: " input-file))
              input-content (slurp input-file)
              _ (println "Injecting input content into prompt template...")
              final-prompt (str/replace prompt-template
                                        "{{INPUT_TEXT}}"
                                        input-content)
              _ (println (str "Sending request to LLM (" model-name ")..."))
              llm-response (llm/call-model model-name
                                           final-prompt
                                           :timeout 600000)] ;; <--- MODIFIED: 10 minute timeout

          ;; The llm-interface returns a JSON string with an :error key on failure.
          (if (str/starts-with? llm-response "{\"error\":")
            (do
              (println "--- ERROR FROM LLM INTERFACE ---")
              (println llm-response)
              (println "------------------------------------")
              (System/exit 1))
            (do
              (println (str "Writing LLM response to: " output-file))
              (spit output-file llm-response)
              (println "Success: Filtered content written to output file."))))

        (catch java.io.FileNotFoundException e
          (println (str "Error: File not found - " (.getMessage e)))
          (System/exit 1))
        (catch Exception e
          (println (str "An unexpected error occurred: " (.getMessage e)))
          (System/exit 1))))))
