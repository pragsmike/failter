(ns failter.config)

(def config
  "A centralized map for all application configuration."
  {:llm {:endpoint "http://localhost:8000/chat/completions"
         :default-timeout-ms 300000}

   :single-run {:model-name "ollama/qwen3:32b"
                :template-path "prompts/cleanup-small-model.md"}

   :evaluator {:default-judge-model "openai/gpt-4o"
               :prompts {:standard "prompts/evaluation-prompt.md"
                         :ground-truth "prompts/evaluation-prompt-gt.md"}}})
