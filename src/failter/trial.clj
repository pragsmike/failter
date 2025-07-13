(ns failter.trial
  (:require [failter.exp-paths :as exp-paths]
            [failter.frontmatter :as fm]))

(defrecord Trial
  [;; --- Plan ---
   model-name
   template-path ;; The path to the template used in this trial
   input-path    ;; The path to the input used in this trial
   output-path   ;; The calculated path for the final artifact
   ;; --- Source Provenance (for evaluator) ---
   source-template-path ;; The original template path, stored in the artifact
   source-input-path    ;; The original input path, stored in the artifact
   ;; --- Result ---
   execution-time-ms
   total-trial-time-ms
   retry-attempts
   errors-on-retry
   tokens-in
   tokens-out
   error])

(defn new-trial
  "Constructor for a planned Trial. Computes the output path but performs no I/O."
  [artifacts-dir model-name template-path input-path]
  (let [output-path (exp-paths/output-path-for-trial artifacts-dir model-name template-path input-path)]
    (->Trial model-name template-path input-path output-path
             nil nil ;; Source paths are nil until read from a file
             nil nil nil nil nil nil nil)))

(defn from-file
  "Constructs a completed Trial record by reading its output file from disk."
  [output-file]
  (let [output-path (.getPath output-file)
        metadata    (:frontmatter (fm/parse-file-content (slurp output-file)))]
    (->Trial (:filtered-by-model metadata)
             ;; Reconstruct the template path used for this trial from metadata
             (:filtered-by-template metadata)
             ;; The original input path is now read from the artifact
             (:source-input-path metadata)
             output-path
             (:source-template-path metadata)
             (:source-input-path metadata)
             (:execution-time-ms metadata)
             (:total-trial-time-ms metadata)
             (:retry-attempts metadata)
             (:errors-on-retry metadata)
             (get-in metadata [:token-usage :prompt_tokens])
             (get-in metadata [:token-usage :completion_tokens])
             (:error metadata))))
