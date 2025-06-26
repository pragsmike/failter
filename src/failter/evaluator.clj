(ns failter.evaluator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [failter.frontmatter :as fm]
            [failter.llm-interface :as llm]))

(def evaluator-config
  {:default-judge-model "openai/gpt-4o"
   :prompts {:standard "prompts/evaluation-prompt.md"
             :ground-truth "prompts/evaluation-prompt-gt.md"}}) ; Pre-emptively adding this key

(defn- find-all-trial-outputs
  "Finds all primary .md output files within an experiment's results directory."
  [experiment-dir]
  (let [results-root (io/file experiment-dir "results")]
    (when (.exists results-root)
      (->> results-root
           (file-seq)
           (filter #(and (.isFile %)
                         (str/ends-with? (.getName %) ".md")))))))

(defn- build-evaluation-context
  "Gathers all file paths, content, and metadata needed for one evaluation.
  Returns a context map, including a :valid? flag and a :reason for skipping."
  [experiment-dir output-file]
  (let [output-path (.getPath output-file)
        eval-path   (io/file (str output-path ".eval"))]
    (if (.exists eval-path)
      {:valid? false :reason "already evaluated" :output-path output-path}
      (try
        (let [output-content (fm/parse-file-content (slurp output-file))
              metadata (:frontmatter output-content)
              template-name (:filtered-by-template metadata)
              input-name (.getName output-file)]
          (cond
            (:error metadata)
            {:valid? false :reason "runner failed" :output-path output-path}

            (not template-name)
            {:valid? false :reason "missing metadata" :output-path output-path}

            :else
            (let [template-path (io/file experiment-dir "templates" template-name)
                  input-path (io/file experiment-dir "inputs" input-name)]
              {:valid? true
               :output-path output-path
               :output-content (:body output-content)
               :input-path (.getPath input-path)
               :input-content (:body (fm/parse-file-content (slurp input-path)))
               :template-path (.getPath template-path)
               :template-content (slurp template-path)})))
        (catch Exception e
          {:valid? false :reason (str "file read error: " (.getMessage e)) :output-path output-path})))))

(defn- evaluate-one-file
  "Private function to perform the evaluation of a single output file, using a context map."
  [judge-model {:keys [input-content template-content output-content output-path]}]
  (try
    (println "--- Evaluating Trial Output ---")
    (let [;; Read prompt path from the new config map
          eval-prompt-template (slurp (get-in evaluator-config [:prompts :standard]))
          final-prompt (-> eval-prompt-template
                           (str/replace "{{ORIGINAL_INPUT}}" input-content)
                           (str/replace "{{PROMPT_TEMPLATE}}" template-content)
                           (str/replace "{{GENERATED_OUTPUT}}" output-content))

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

(defn- run-evaluations-for-contexts
  "Iterates over a sequence of contexts, evaluating the valid ones."
  [judge-model contexts]
  (doseq [context contexts]
    (if (:valid? context)
      (evaluate-one-file judge-model context)
      (println (str "Skipping (" (:reason context) "): " (:output-path context))))))

(defn run-evaluation
  "Scans an experiment directory for outputs and runs the evaluation process on them."
  [experiment-dir & {:keys [judge-model] :or {judge-model (:default-judge-model evaluator-config)}}]
  (let [normalized-judge-model (if (str/includes? judge-model "/")
                                 judge-model
                                 (str "openai/" judge-model))]
    (println (str "Starting evaluation for experiment in: " experiment-dir " using judge: " normalized-judge-model))
    (->> (find-all-trial-outputs experiment-dir)
         (map #(build-evaluation-context experiment-dir %))
         (run-evaluations-for-contexts normalized-judge-model))))
