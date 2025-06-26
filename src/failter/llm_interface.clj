(ns failter.llm-interface
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

(def LITELLM_ENDPOINT "http://localhost:8000/chat/completions")
(def LITELLM_API_KEY (System/getenv "LITELLM_API_KEY"))
(def DEFAULT_TIMEOUT_MS 300000)

(defn pre-flight-checks []
  (when-not (seq LITELLM_API_KEY)
    (println "---")
    (println "ERROR: LITELLM_API_KEY environment variable not set.")
    (println "Please set it before running the application.")
    (System/exit 1))
  true)

(defn- parse-llm-response [response-body model-name]
  (println (str "DEBUG: Response from " model-name "\n" response-body))
  (try
    (let [parsed-body (json/read-str response-body :key-fn keyword)
          content (-> parsed-body :choices first :message :content)]
      (if content
        ;; Return a rich map instead of just the content string
        {:content content
         :usage (:usage parsed-body)
         :cost (get-in parsed-body [:choices 0 :message :tool_calls 0 :function :arguments :cost])} ; Specific to LiteLLM cost tracking
        (do
          (println (str "ERROR: Could not extract content from LLM response for " model-name ". Body: " response-body))
          {:error (str "No content in LLM response: " (pr-str parsed-body))})))
    (catch Exception e
      (println (str "ERROR: Failed to parse LLM JSON response for " model-name ". Error: " (.getMessage e) ". Body: " response-body))
      {:error (str "Malformed JSON from LLM: " (.getMessage e))})))

(defn call-model
  "Makes an actual HTTP call to the LiteLLM endpoint.
  Returns a map with :content, :usage, :cost or an :error key."
  [model-name prompt-string & {:keys [timeout] :or {timeout DEFAULT_TIMEOUT_MS}}]
  (println (str "\n;; --- ACTUALLY Calling LLM: " model-name " via " LITELLM_ENDPOINT " ---"))
  (println (str ";; --- Using timeout: " timeout "ms ---"))
  (try
    (let [request-body {:model model-name
                        :messages [{:role "user" :content prompt-string}]}
          headers {"Authorization" (str "Bearer " LITELLM_API_KEY)}
          response (http/post LITELLM_ENDPOINT
                              {:body (json/write-str request-body)
                               :content-type :json
                               :accept :json
                               :headers headers
                               :throw-exceptions false
                               :socket-timeout timeout
                               :connection-timeout timeout})]
      (if (= 200 (:status response))
        (parse-llm-response (:body response) model-name)
        (do
          (println (str "ERROR: LLM call to " model-name " failed with status " (:status response) ". Body: " (:body response)))
          {:error (str "LLM API Error: " (:status response) " " (:body response))})))
    (catch Exception e
      (println (str "ERROR: Exception during LLM call to " model-name ". Error: " (.getMessage e)))
      {:error (str "Network or client exception: " (.getMessage e))})))
