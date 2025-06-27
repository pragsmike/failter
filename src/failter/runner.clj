(ns failter.runner
  (:require [failter.llm-interface :as llm]
            [failter.frontmatter :as fm]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [failter.exp-paths :as exp-paths]
            [failter.trial :as trial]))

(defn run-single-trial
  "Executes a single filtering trial defined by a Trial record."
  [^failter.trial.Trial trial]
  (let [{:keys [model-name template-path input-path output-path]} trial]
    (println "--- Running Trial (Frontmatter-aware) ---")
    (println (str "  Model: " model-name))
    (println (str "  Template: " template-path))

    (let [input-content (slurp input-path)
          {:keys [frontmatter body]} (fm/parse-file-content input-content)
          prompt-template (slurp template-path)
          final-prompt (str/replace prompt-template "{{INPUT_TEXT}}" body)

          start-time (System/nanoTime)
          llm-response (llm/call-model model-name final-prompt :timeout 60000)
          elapsed-ms (long (/ (- (System/nanoTime) start-time) 1e6))

          base-metadata (assoc frontmatter
                               :filtered-by-model model-name
                               :filtered-by-template (.getName (io/file template-path))
                               :execution-time-ms elapsed-ms)]

      (io/make-parents output-path)

      (if-let [error-msg (:error llm-response)]
        (let [error-metadata (assoc base-metadata :error error-msg)
              final-content (fm/serialize error-metadata "")]
          (println (str "  LLM call failed. Writing error metadata to: " output-path))
          (spit output-path final-content))

        (let [monologue-content-regex #"(?s)<(?:think|scratchpad)>(.*?)</(?:think|scratchpad)>"
              monologue-block-regex   #"(?s)(<think>.*?</think>|<scratchpad>.*?</scratchpad>)\s*"
              {:keys [content usage cost]} llm-response
              thoughts (some-> (re-find monologue-content-regex content) second str/trim)
              cleaned-content (str/replace content monologue-block-regex "")
              success-metadata (assoc base-metadata
                                      :token-usage usage
                                      :estimated-cost cost)
              final-content (fm/serialize success-metadata cleaned-content)
              thoughts-path (exp-paths/thoughts-path output-path)]

          (println (str "  LLM call succeeded. Writing response to: " output-path))
          (spit output-path final-content)

          (when (and thoughts (not (str/blank? thoughts)))
            (println (str "  Extracted monologue. Writing to: " thoughts-path))
            (spit thoughts-path thoughts))))

      (println "--- Trial Complete ---\n"))))

(defn live-trial-runner
  "The function passed to conduct-experiment for live runs."
  [trial]
  (run-single-trial trial))
