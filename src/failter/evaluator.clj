(ns failter.evaluator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [failter.config :as config]
            [failter.eval :as feval]
            [failter.exp-paths :as exp-paths]
            [failter.frontmatter :as fm]
            [failter.llm-interface :as llm]
            [failter.trial :as trial]
            [failter.util :as util]))

(defn- build-judge-prompt
  "Constructs the final prompt string for the judge LLM."
  [{:keys [has-ground-truth? ground-truth-content input-content template-content output-content]}]
  (if has-ground-truth?
    (let [prompt-key (if has-ground-truth? :ground-truth :standard)
          prompt-path (get-in config/config [:evaluator :prompts prompt-key])
          eval-prompt-template (slurp prompt-path)]
      (-> eval-prompt-template
          (str/replace "{{ORIGINAL_INPUT}}" input-content)
          (str/replace "{{PROMPT_TEMPLATE}}" template-content)
          (str/replace "{{GROUND_TRUTH_EXAMPLE}}" ground-truth-content)
          (str/replace "{{GENERATED_OUTPUT}}" output-content)))
    (let [prompt-key :standard
          prompt-path (get-in config/config [:evaluator :prompts prompt-key])
          eval-prompt-template (slurp prompt-path)]
      (-> eval-prompt-template
          (str/replace "{{ORIGINAL_INPUT}}" input-content)
          (str/replace "{{PROMPT_TEMPLATE}}" template-content)
          (str/replace "{{GENERATED_OUTPUT}}" output-content)))))

(defn- build-evaluation-context
  "Builds the context map needed for evaluation, including all file contents."
  [experiment-dir ^failter.trial.Trial trial]
  (let [output-path (:output-path trial)
        eval-file   (io/file (exp-paths/eval-path output-path))]
    (if (.exists eval-file)
      {:valid? false :reason "already evaluated"}
      (let [input-path    (exp-paths/input-path-for-result experiment-dir output-path)
            ;; --- THIS IS THE FIX ---
            ;; The trial record only has the template's filename.
            ;; We must construct the full path before trying to read it.
            template-filename (:template-path trial)
            full-template-path (.getPath (io/file (exp-paths/templates-dir experiment-dir) template-filename))
            gt-path-str   (exp-paths/ground-truth-path-for-input experiment-dir input-path)
            gt-file       (io/file gt-path-str)]
        (merge
         {:valid? true
          :trial trial
          :input-content (:body (fm/parse-file-content (slurp input-path)))
          :output-content (:body (fm/parse-file-content (slurp output-path)))
          :template-content (slurp full-template-path)} ;; Slurp the corrected full path
         (if (.exists gt-file)
           {:has-ground-truth? true
            :ground-truth-content (:body (fm/parse-file-content (slurp gt-file)))}
           {:has-ground-truth? false}))))))

(defn- execute-evaluation!
  "Calls the judge LLM and writes the resulting Eval record to disk."
  [judge-model context]
  (let [final-prompt (build-judge-prompt context)
        eval-method (if (:has-ground-truth? context) "ground-truth" "rules-based")
        trial-output-path (-> context :trial :output-path)]
    (println (str "--- Evaluating Trial Output (" eval-method ") ---"))
    (println (str "  Judge Model: " judge-model "  Output File: " trial-output-path))
    (let [eval-response (llm/call-model judge-model final-prompt :timeout 600000)]
      (if-let [err (:error eval-response)]
        (println (str "ERROR: Judge LLM failed for " trial-output-path "\n" err))
        (let [llm-output (util/parse-yaml-block (:content eval-response))
              grade (second (re-find #"(?m)grade:\s*([A-DF])" llm-output))
              rationale (second (re-find #"(?ms)rationale:\s*(.*)" llm-output))
              eval-record (feval/->Eval (:trial context) grade (str/trim rationale) eval-method judge-model nil)]
          (feval/write-eval eval-record)
          (println (str "  Writing evaluation to: " (exp-paths/eval-path trial-output-path))))))))

(defn run-evaluation
  "Main entry point for the evaluation process."
  [experiment-dir & {:keys [judge-model] :or {judge-model (get-in config/config [:evaluator :default-judge-model])}}]
  (println (str "Starting evaluation for experiment in: " experiment-dir " using judge: " judge-model))
  (let [result-files (exp-paths/find-all-result-files experiment-dir)]
    (doseq [file result-files]
      (let [trial (trial/from-file file)]
        (if (:error trial)
          (println (str "Skipping (runner failed): " (:output-path trial)))
          (let [context (build-evaluation-context experiment-dir trial)]
            (when (:valid? context)
              (execute-evaluation! judge-model context))))))))
