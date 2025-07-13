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
  "Constructs the complete prompt for the judge LLM based on the evaluation context."
  [{:keys [has-ground-truth? ground-truth-content input-content template-content output-content]}]
  (let [;; The scoring strategy is now fixed, but could be made spec-configurable in the future.
        strategy (get-in config/config [:evaluator :scoring-strategy] :letter-grade)
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
  "Builds the map of data needed for evaluation. Now relies on the Trial record
  for source paths, making it independent of directory structure."
  [^failter.trial.Trial trial]
  (let [output-path (:output-path trial)
        eval-file   (io/file (exp-paths/eval-path output-path))]
    (if (.exists eval-file)
      {:valid? false :reason "already evaluated"}
      (let [;; These paths now come directly from the hydrated Trial record.
            input-path (:source-input-path trial)
            template-path (:source-template-path trial)
            ;; The ground truth path is derived by assuming a parallel directory structure.
            gt-path-str (str/replace input-path "/inputs/" "/ground_truth/")
            gt-file (io/file gt-path-str)]
        (merge
         {:valid? true
          :trial trial
          :input-content (:body (fm/parse-file-content (slurp input-path)))
          :output-content (:body (fm/parse-file-content (slurp output-path)))
          :template-content (:body (fm/parse-file-content (slurp template-path)))}
         (if (.exists gt-file)
           {:has-ground-truth? true
            :ground-truth-content (:body (fm/parse-file-content (slurp gt-file)))}
           {:has-ground-truth? false}))))))

(defn- execute-evaluation!
  "Calls the judge model and writes the .eval artifact."
  [judge-model context]
  (let [strategy (get-in config/config [:evaluator :scoring-strategy] :letter-grade)
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
              rationale (or (second (re-find #"(?ms)rationale:\s*(.*)" (util/parse-yaml-block llm-output))) "")
              eval-record (feval/->Eval (:trial context) score (str/trim rationale) eval-method judge-model nil)]
          (feval/write-eval eval-record)
          (log/info (str "  Writing evaluation to: " (exp-paths/eval-path trial-output-path))))))))

(defn run-evaluation
  "Iterates through all result files in an artifacts directory and evaluates them if needed.
  This function is now idempotent at the level of individual evaluations."
  [artifacts-dir & {:keys [judge-model] :or {judge-model "openai/gpt-4o-mini"}}]
  (log/info (str "Starting evaluation for artifacts in: " artifacts-dir " using judge: " judge-model))
  (let [result-files (exp-paths/find-all-result-files artifacts-dir)]
    (doseq [file result-files]
      ;; Create the Trial record from the on-disk artifact.
      ;; This now includes the critical source-input-path and source-template-path.
      (let [trial (trial/from-file file)]
        (if (:error trial)
          (log/info (str "Skipping (runner failed): " (:output-path trial)))
          (let [context (build-evaluation-context trial)]
            (when (:valid? context)
              (execute-evaluation! judge-model context))))))))
