(ns failter.evaluator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [failter.frontmatter :as fm]
            [failter.llm-interface :as llm]))

(def JUDGE_MODEL "gpt-4o") ; Default powerful model for evaluation
(def EVALUATION_PROMPT_PATH "prompts/evaluation-prompt.md")

(defn- evaluate-one-file
  "Private function to perform the evaluation of a single output file."
  [{:keys [judge-model template-path input-path output-path]}]
  (try
    (println "--- Evaluating Trial Output (Body-only) ---")
    (let [eval-prompt-template (slurp EVALUATION_PROMPT_PATH)
          original-input-body  (:body (fm/parse-file-content (slurp input-path)))
          prompt-template      (slurp template-path)
          generated-output-body(:body (fm/parse-file-content (slurp output-path)))

          final-prompt (-> eval-prompt-template
                           (str/replace "{{ORIGINAL_INPUT}}" original-input-body)
                           (str/replace "{{PROMPT_TEMPLATE}}" prompt-template)
                           (str/replace "{{GENERATED_OUTPUT}}" generated-output-body))

          _ (println (str "  Judge Model: " judge-model "  Output File: " output-path))
          eval-response (llm/call-model judge-model final-prompt :timeout 600000)
          eval-file-path (str output-path ".eval")]

      (if (:error eval-response)
        (println (str "ERROR: Judge LLM failed for " output-path "\n" (:error eval-response)))
        (do
          (spit eval-file-path (:content eval-response))
          (println (str "  Writing evaluation to: " eval-file-path)))))
    (catch Exception e
      (println (str "ERROR: An unexpected error occurred during evaluation of " output-path ": " (.getMessage e))))))

(defn run-evaluation
  "Scans an experiment directory for outputs and runs the evaluation process on them,
  now reading metadata from the output file itself."
  [experiment-dir & {:keys [judge-model] :or {judge-model JUDGE_MODEL}}]
  (println (str "Starting evaluation for experiment in: " experiment-dir))
  (let [output-files (->> (io/file experiment-dir)
                          (file-seq)
                          (filter #(.isFile %))
                          ;; --- THIS IS THE CORRECTED LINE ---
                          ;; It now correctly finds files in directories that are NOT 'inputs' or 'templates'.
                          (filter #(not (re-find #"^(inputs|templates)$" (-> % .getParentFile .getName))))
                          (remove #(str/ends-with? (.getName %) ".eval")))]
    (doseq [output-file output-files
            :let [output-path (.getPath output-file)
                  eval-path   (str output-path ".eval")]]
      (if (.exists (io/file eval-path))
        (println (str "Skipping existing evaluation: " eval-path))
        (let [metadata (:frontmatter (fm/parse-file-content (slurp output-file)))]
          (if (or (not (:filtered-by-template metadata)) (:error metadata))
            (println (str "Skipping evaluation for file with error or missing metadata: " output-path))
            (let [template-path (str (io/file experiment-dir "templates" (:filtered-by-template metadata)))
                  input-path (str (io/file experiment-dir "inputs" (.getName output-file)))]
              (evaluate-one-file {:judge-model   judge-model
                                  :template-path template-path
                                  :input-path    input-path
                                  :output-path   output-path}))))))))
