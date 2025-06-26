(ns failter.runner
  (:require [failter.llm-interface :as llm]
            [failter.frontmatter :as fm]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn run-single-trial
  "Executes a single filtering trial. Now measures time and handles LLM
  errors by writing a file with error metadata."
  [{:keys [model-name template-path input-path output-path]}]
  (println "--- Running Trial (Frontmatter-aware) ---")
  (println (str "  Model: " model-name))
  (println (str "  Template: " template-path))

  (let [input-content (slurp input-path)
        {:keys [frontmatter body]} (fm/parse-file-content input-content)
        prompt-template (slurp template-path)
        final-prompt (str/replace prompt-template "{{INPUT_TEXT}}" body)

        [elapsed-time llm-response] (let [start (System/nanoTime)]
                                      [(/ (- (System/nanoTime) start) 1e6)
                                       (llm/call-model model-name final-prompt :timeout 60000)])

        base-metadata (assoc frontmatter
                             :filtered-by-model model-name
                             :filtered-by-template (.getName (io/file template-path))
                             :execution-time-ms (long elapsed-time))]

    (io/make-parents output-path)

    (if-let [error-msg (:error llm-response)]
      ;; --- ERROR PATH ---
      (let [error-metadata (assoc base-metadata :error error-msg)
            final-content (fm/serialize error-metadata "")] ; Empty body
        (println (str "  LLM call failed. Writing error metadata to: " output-path))
        (spit output-path final-content))
      ;; --- SUCCESS PATH ---
      (let [{:keys [content usage cost]} llm-response
            success-metadata (assoc base-metadata
                                    :token-usage usage
                                    :estimated-cost cost)
            final-content (fm/serialize success-metadata content)]
        (println (str "  LLM call succeeded. Writing response to: " output-path))
        (spit output-path final-content)))
    (println "--- Trial Complete ---\n")))

(defn live-trial-runner [params] (run-single-trial params))
