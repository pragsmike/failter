(ns failter.evaluator
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [failter.config :as config]
            [failter.eval :as feval]
            [failter.exp-paths :as exp-paths]
            [failter.frontmatter :as fm]
            [failter.llm-interface :as llm]
            [failter.log :as log]
            [failter.scoring :as scoring]
            [failter.trial :as trial]
            [failter.util :as util]))

(defn- build-judge-prompt
  [{:keys [has-ground-truth? ground-truth-content input-content template-content output-content]}]
  (let [strategy (get-in config/config [:evaluator :scoring-strategy])
        prompt-key (if has-ground-truth? :ground-truth :standard)
        prompt-path (get-in config/config [:evaluator :prompts prompt-key])
        eval-prompt-template (slurp prompt-path)
        scoring-instructions (scoring/get-prompt-instructions {:strategy strategy})]
    (if has-ground-truth?
      (-> eval-prompt-template
          (str/replace "{{SCORING_INSTRUCTIONS}}" scoring-instructions)
          (str/replace "{{ORIGINAL_INPUT}}" input-content)
          (str/replace "{{PROMPT_TEMPLATE}}" template-content)
          (str/replace "{{GROUND_TRUTH_EXAMPLE}}" ground-truth-content)
          (str/replace "{{GENERATED_OUTPUT}}" output-content))
      (-> eval-prompt-template
          (str/replace "{{SCORING_INSTRUCTIONS}}" scoring-instructions)
          (str/replace "{{ORIGINAL_INPUT}}" input-content)
          (str/replace "{{PROMPT_TEMPLATE}}" template-content)
          (str/replace "{{GENERATED_OUTPUT}}" output-content)))))

(defn- build-evaluation-context
  [experiment-dir ^failter.trial.Trial trial]
  (let [output-path (:output-path trial)
        eval-file   (io/file (exp-paths/eval-path output-path))]
    (if (.exists eval-file)
      {:valid? false :reason "already evaluated"}
      (let [input-path    (exp-paths/input-path-for-result experiment-dir output-path)
            template-filename (:template-path trial)
            full-template-path (.getPath (io/file (exp-paths/templates-dir experiment-dir) template-filename))
            gt-path-str   (exp-paths/ground-truth-path-for-input experiment-dir input-path)
            gt-file       (io/file gt-path-str)]
        (merge
         {:valid? true
          :trial trial
          :input-content (:body (fm/parse-file-content (slurp input-path)))
          :output-content (:body (fm/parse-file-content (slurp output-path)))
          :template-content (:body (fm/parse-file-content (slurp full-template-path)))}
         (if (.exists gt-file)
           {:has-ground-truth? true
            :ground-truth-content (:body (fm/parse-file-content (slurp gt-file)))}
           {:has-ground-truth? false}))))))

(defn- execute-evaluation!
  [judge-model context]
  (let [strategy (get-in config/config [:evaluator :scoring-strategy])
        final-prompt (build-judge-prompt context)
        eval-method (if (:has-ground-truth? context) "ground-truth" "rules-based")
        trial-output-path (-> context :trial :output-path)]
    (log/info (str "--- Evaluating Trial Output (" eval-method ") ---"))
    (log/info (str "  Judge Model: " judge-model "  Output File: " trial-output-path))
    (let [eval-response (llm/call-model judge-model final-prompt :timeout 600000)]
      (if-let [err (:error eval-response)]
        (log/error (str "Judge LLM failed for " trial-output-path "\n" err))
        (let [llm-output (:content eval-response)
              score (scoring/parse-raw-score strategy llm-output)
              ;; --- FIX: Default to an empty string if rationale is not found ---
              rationale (or (second (re-find #"(?ms)rationale:\s*(.*)" (util/parse-yaml-block llm-output))) "")
              eval-record (feval/->Eval (:trial context) score (str/trim rationale) eval-method judge-model nil)]
          (feval/write-eval eval-record)
          (log/info (str "  Writing evaluation to: " (exp-paths/eval-path trial-output-path))))))))

(defn run-evaluation
  [experiment-dir & {:keys [judge-model] :or {judge-model (get-in config/config [:evaluator :default-judge-model])}}]
  (log/info (str "Starting evaluation for experiment in: " experiment-dir " using judge: " judge-model))
  (let [result-files (exp-paths/find-all-result-files experiment-dir)]
    (doseq [file result-files]
      (let [trial (trial/from-file file)]
        (if (:error trial)
          (log/info (str "Skipping (runner failed): " (:output-path trial)))
          (let [context (build-evaluation-context experiment-dir trial)]
            (when (:valid? context)
              (execute-evaluation! judge-model context))))))))
