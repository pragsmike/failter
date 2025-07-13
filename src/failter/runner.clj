(ns failter.runner
  (:require [failter.llm-interface :as llm]
            [failter.frontmatter :as fm]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [failter.exp-paths :as exp-paths]
            [failter.log :as log]))

(defn- execute-trial!
  "The core trial execution logic. Performs one LLM call and returns the result.
  This is the function that will be retried."
  [model-name prompt-string]
  (let [start-time (System/nanoTime)
        llm-response (llm/call-model model-name prompt-string :timeout 60000)
        elapsed-ms (long (/ (- (System/nanoTime) start-time) 1e6))]
    (assoc llm-response :execution-time-ms elapsed-ms)))

(defn- write-artifact!
  "Writes the final content (body + metadata) to the trial's output path.
  Also handles writing the .thoughts file."
  [output-path final-metadata body]
  (io/make-parents output-path)
  (let [final-content (fm/serialize final-metadata body)]
    (log/info (str "  Writing result artifact to: " output-path))
    (spit output-path final-content))

  (let [monologue-content-regex #"(?s)<(?:think|scratchpad)>(.*?)</(?:think|scratchpad)>"
        thoughts (some-> (re-find monologue-content-regex body) second str/trim)
        thoughts-path (exp-paths/thoughts-path output-path)]
    (when (and thoughts (not (str/blank? thoughts)))
      (log/info (str "  Extracted monologue. Writing to: " thoughts-path))
      (spit thoughts-path thoughts))))

(defn run-single-trial
  "Runs a single trial with a configurable number of retries. This is the main
  entry point for the runner component."
  [trial {:keys [retries] :or {retries 0}}]
  (let [{:keys [model-name template-path input-path output-path]} trial
        total-start-time (System/nanoTime)]

    (log/info "--- Running Trial ---")
    (log/info (str "  Model: " model-name " | Template: " template-path))
    (log/info (str "  Retries enabled: " retries))

    (let [input-content (slurp input-path)
          {:keys [frontmatter body]} (fm/parse-file-content input-content)
          prompt-template (:body (fm/parse-file-content (slurp template-path)))
          final-prompt (str/replace prompt-template "{{INPUT_TEXT}}" body)

          ;; --- Retry Loop ---
          loop-result (loop [attempt 1
                             errors-so-far []]
                        (let [response (execute-trial! model-name final-prompt)]
                          (if (and (:error response) (< attempt (inc retries)))
                            (do
                              (log/warn (str "  Attempt " attempt " failed. Retrying... Error: " (:error response)))
                              (recur (inc attempt) (conj errors-so-far (:error response))))
                            ;; Return the response on success or final failure
                            (assoc response :errors-on-retry errors-so-far))))]

      (let [total-trial-time-ms (long (/ (- (System/nanoTime) total-start-time) 1e6))
            ;; --- CRITICAL CHANGE: Add source paths to the metadata ---
            base-metadata (assoc frontmatter
                                 :filtered-by-model model-name
                                 :filtered-by-template (.getName (io/file template-path))
                                 :source-input-path input-path
                                 :source-template-path template-path
                                 :total-trial-time-ms total-trial-time-ms)]

        (if-let [final-error (:error loop-result)]
          ;; --- Handle Final Failure ---
          (let [error-metadata (assoc base-metadata
                                      :error final-error
                                      :retry-attempts (count (:errors-on-retry loop-result))
                                      :errors-on-retry (:errors-on-retry loop-result))]
            (log/error (str "  Trial ultimately failed after " (inc retries) " attempts. Error: " final-error))
            (write-artifact! output-path error-metadata ""))

          ;; --- Handle Success ---
          (let [monologue-block-regex   #"(?s)(<think>.*?</think>|<scratchpad>.*?</scratchpad>)\s*"
                cleaned-content (str/replace (:content loop-result) monologue-block-regex "")
                success-metadata (assoc base-metadata
                                        :execution-time-ms (:execution-time-ms loop-result)
                                        :retry-attempts (count (:errors-on-retry loop-result))
                                        :errors-on-retry (:errors-on-retry loop-result)
                                        :token-usage (:usage loop-result))]
            (log/info (str "  LLM call succeeded."))
            (write-artifact! output-path success-metadata cleaned-content))))

      (log/info "--- Trial Complete ---\n"))))
