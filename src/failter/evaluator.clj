(ns failter.evaluator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [failter.frontmatter :as fm]
            [failter.llm-interface :as llm]))

(def JUDGE_MODEL "gpt-4o")
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
  "Scans an experiment directory for outputs and runs the evaluation process on them."
  [experiment-dir & {:keys [judge-model] :or {judge-model JUDGE_MODEL}}]
  (println (str "Starting evaluation for experiment in: " experiment-dir))
  (let [results-root (io/file experiment-dir "results")
        output-files (when (.exists results-root)
                       (->> results-root
                            (file-seq)
                            (filter #(and (.isFile %)
                                          (str/ends-with? (.getName %) ".md")))))]
    (doseq [output-file output-files
            :let [output-path (.getPath output-file)
                  eval-path   (io/file (str output-path ".eval"))
                  metadata    (:frontmatter (fm/parse-file-content (slurp output-file)))]]
      (cond
        (.exists eval-path)
        (println (str "Skipping (already evaluated): " output-path))
        (:error metadata)
        (println (str "Skipping (runner failed):       " output-path))
        (not (:filtered-by-template metadata))
        (println (str "Skipping (missing metadata):    " output-path))
        :else
        (let [template-path (str (io/file experiment-dir "templates" (:filtered-by-template metadata)))
              input-path    (str (io/file experiment-dir "inputs" (.getName output-file)))]
          (evaluate-one-file {:judge-model   judge-model
                              :template-path template-path
                              :input-path    input-path
                              :output-path   output-path}))))))
