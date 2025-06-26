(ns failter.evaluator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [failter.frontmatter :as fm]
            [failter.llm-interface :as llm]))

(def JUDGE_MODEL "gpt-4o") ; Default powerful model for evaluation
(def EVALUATION_PROMPT_PATH "prompts/evaluation-prompt.md")

(defn- evaluate-one-file
  "Private function to perform the evaluation of a single output file,
  now using only the body of the text."
  [{:keys [judge-model template-path input-path output-path]}]
  (try
    (println "--- Evaluating Trial Output (Body-only) ---")
    (println (str "  Judge Model: " judge-model "  Output File: " output-path))

    (let [eval-prompt-template (slurp EVALUATION_PROMPT_PATH)
          original-input-body  (:body (fm/parse-file-content (slurp input-path)))
          prompt-template      (slurp template-path)
          generated-output-body(:body (fm/parse-file-content (slurp output-path)))

          final-prompt (-> eval-prompt-template
                           (str/replace "{{ORIGINAL_INPUT}}" original-input-body)
                           (str/replace "{{PROMPT_TEMPLATE}}" prompt-template)
                           (str/replace "{{GENERATED_OUTPUT}}" generated-output-body))

          _ (println "  Sending request to Judge LLM...")
          eval-response (llm/call-model judge-model final-prompt :timeout 600000)
          eval-file-path (str output-path ".eval")]

      (if (str/starts-with? eval-response "{\"error\":")
        (println (str "ERROR: Judge LLM failed for " output-path "\n" eval-response))
        (do
          (spit eval-file-path eval-response)
          (println (str "  Writing evaluation to: " eval-file-path) "\n--- Evaluation Complete ---\n"))))
    (catch Exception e
      (println (str "ERROR: An unexpected error occurred during evaluation of " output-path ": " (.getMessage e))))))

(defn run-evaluation
  "Scans an experiment directory for outputs and runs the evaluation process on them."
  [experiment-dir & {:keys [judge-model] :or {judge-model JUDGE_MODEL}}]
  (println (str "Starting evaluation for experiment in: " experiment-dir))
  (let [exp-dir-file (io/file experiment-dir)
        result-dirs  (->> (.listFiles exp-dir-file)
                          (filter #(.isDirectory %))
                          (filter #(not (#{"inputs" "templates"} (.getName %)))))]

    (doseq [dir result-dirs]
      (let [output-files (->> (.listFiles dir)
                              (filter #(.isFile %))
                              (filter #(not (str/ends-with? (.getName %) ".eval"))))]
        (doseq [output-file output-files
                :let [output-path (.getPath output-file)
                      eval-path   (str output-path ".eval")]]
          (if (.exists (io/file eval-path))
            (println (str "Skipping existing evaluation: " eval-path))
            (let [output-dir-name (.getName dir)
                  last-underscore (str/last-index-of output-dir-name "_")

                  ;; Reverse-engineer the original template name
                  sanitized-template-name (subs output-dir-name (inc last-underscore))
                  template-filename (str sanitized-template-name ".md")
                  template-path (-> (io/file experiment-dir "templates" template-filename) .getPath)

                  ;; Reverse-engineer the original input path
                  input-filename (.getName output-file)
                  input-path (-> (io/file experiment-dir "inputs" input-filename) .getPath)]

              (evaluate-one-file {:judge-model   judge-model
                                  :template-path template-path
                                  :input-path    input-path
                                  :output-path   output-path}))))))))
