(ns failter.evaluator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [failter.frontmatter :as fm]
            [failter.llm-interface :as llm]
            [failter.exp-paths :as exp-paths]
            [failter.util :as util]))

(def evaluator-config
  {:default-judge-model "openai/gpt-4o"
   :prompts {:standard "prompts/evaluation-prompt.md"
             :ground-truth "prompts/evaluation-prompt-gt.md"}})

(defn- find-all-trial-outputs
  [experiment-dir]
  (let [results-root (exp-paths/results-dir experiment-dir)]
    (when (.exists results-root)
      (->> results-root
           (file-seq)
           (filter #(and (.isFile %)
                         (str/ends-with? (.getName %) ".md")))))))

(defn- build-evaluation-context
  [experiment-dir output-file]
  (let [output-path (.getPath output-file)
        eval-file   (io/file (exp-paths/eval-path output-path))]
    (if (.exists eval-file)
      {:valid? false :reason "already evaluated" :output-path output-path}
      (try
        (let [output-content (fm/parse-file-content (slurp output-file))
              metadata (:frontmatter output-content)]
          (cond
            (:error metadata)
            {:valid? false :reason "runner failed" :output-path output-path}
            (not (:filtered-by-template metadata))
            {:valid? false :reason "missing metadata" :output-path output-path}
            :else
            (let [input-path (exp-paths/input-path-for-result experiment-dir output-path)
                  template-path (exp-paths/template-path-for-result experiment-dir metadata)
                  gt-path-str (exp-paths/ground-truth-path-for-input experiment-dir input-path)
                  gt-file (io/file gt-path-str)]
              (merge
               {:valid? true
                :output-path output-path
                :output-content (:body output-content)
                :input-path input-path
                :input-content (:body (fm/parse-file-content (slurp input-path)))
                :template-path template-path
                :template-content (slurp template-path)}
               (if (.exists gt-file)
                 {:has-ground-truth? true
                  :ground-truth-content (:body (fm/parse-file-content (slurp gt-file)))}
                 {:has-ground-truth? false
                  :ground-truth-content nil})))))
        (catch Exception e
          {:valid? false :reason (str "file read error: " (.getMessage e)) :output-path output-path})))))

(defn- build-judge-prompt
  [{:keys [has-ground-truth? ground-truth-content input-content template-content output-content]}]
  (if has-ground-truth?
    (let [prompt-path (get-in evaluator-config [:prompts :ground-truth])
          eval-prompt-template (slurp prompt-path)]
      (-> eval-prompt-template
          (str/replace "{{ORIGINAL_INPUT}}" input-content)
          (str/replace "{{PROMPT_TEMPLATE}}" template-content)
          (str/replace "{{GROUND_TRUTH_EXAMPLE}}" ground-truth-content)
          (str/replace "{{GENERATED_OUTPUT}}" output-content)))
    (let [prompt-path (get-in evaluator-config [:prompts :standard])
          eval-prompt-template (slurp prompt-path)]
      (-> eval-prompt-template
          (str/replace "{{ORIGINAL_INPUT}}" input-content)
          (str/replace "{{PROMPT_TEMPLATE}}" template-content)
          (str/replace "{{GENERATED_OUTPUT}}" output-content)))))

(defn- execute-evaluation!
  [judge-model context]
  (try
    (let [final-prompt (build-judge-prompt context)
          output-path (:output-path context)
          eval-file-path (exp-paths/eval-path output-path)
          eval-method (if (:has-ground-truth? context) "ground-truth" "rules-based")]
      (println (str "--- Evaluating Trial Output (" eval-method ") ---"))
      (println (str "  Judge Model: " judge-model "  Output File: " output-path))
      (let [eval-response (llm/call-model judge-model final-prompt :timeout 600000)]
        (if (:error eval-response)
          (println (str "ERROR: Judge LLM failed for " output-path "\n" (:error eval-response)))
          (let [eval-content (:content eval-response)
                yaml-str (util/parse-yaml-block eval-content)
                method-str (str "evaluation-method: " eval-method)
                final-content (str yaml-str "\n" method-str "\n")]
            (spit eval-file-path final-content)
            (println (str "  Writing evaluation to: " eval-file-path))))))
    (catch Exception e
      (println (str "ERROR: An unexpected error occurred during evaluation of " (:output-path context) ": " (.getMessage e))))))

(defn- run-evaluations-for-contexts
  [judge-model contexts]
  (doseq [context contexts]
    (if (:valid? context)
      (execute-evaluation! judge-model context)
      (println (str "Skipping (" (:reason context) "): " (:output-path context))))))

(defn run-evaluation
  [experiment-dir & {:keys [judge-model] :or {judge-model (:default-judge-model evaluator-config)}}]
  (println (str "Starting evaluation for experiment in: " experiment-dir " using judge: " judge-model))
  (->> (find-all-trial-outputs experiment-dir)
       (map #(build-evaluation-context experiment-dir %))
       (run-evaluations-for-contexts judge-model)))
