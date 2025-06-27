(ns failter.llm-interface
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [failter.config :as config]
            [failter.log :as log]))

(def LITELLM_API_KEY (System/getenv "LITELLM_API_KEY"))

(defn pre-flight-checks []
  (when-not (seq LITELLM_API_KEY)
    (log/error "---")
    (log/error "LITELLM_API_KEY environment variable not set.")
    (log/error "Please set it before running the application.")
    (System/exit 1))
  true)

(defn- parse-llm-response [response-body model-name]
  (log/debug (str "Response from " model-name "\n" response-body))
  (try
    (let [parsed-body (json/read-str response-body :key-fn keyword)
          content (-> parsed-body :choices first :message :content)]
      (if content
        {:content content
         :usage (:usage parsed-body)
         :cost (:cost parsed-body)}
        (do
          (log/error (str "Could not extract content from LLM response for " model-name ". Body: " response-body))
          {:error (str "No content in LLM response: " (pr-str parsed-body))})))
    (catch Exception e
      (log/error (str "Failed to parse LLM JSON response for " model-name ". Error: " (.getMessage e) ". Body: " response-body))
      {:error (str "Malformed JSON from LLM: " (.getMessage e))})))

(defn call-model
  [model-name prompt-string & {:keys [timeout] :or {timeout (get-in config/config [:llm :default-timeout-ms])}}]
  (let [endpoint (get-in config/config [:llm :endpoint])]
    (log/info (str "\n;; --- ACTUALLY Calling LLM: " model-name " via " endpoint " ---"))
    (log/info (str ";; --- Using timeout: " timeout "ms ---"))
    (try
      (let [request-body {:model model-name
                          :messages [{:role "user" :content prompt-string}]}
            headers {"Authorization" (str "Bearer " LITELLM_API_KEY)}
            response (http/post endpoint
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
            (log/error (str "LLM call to " model-name " failed with status " (:status response) ". Body: " (:body response)))
            {:error (str "LLM API Error: " (:status response) " " (:body response))})))
      (catch Exception e
        (log/error (str "Exception during LLM call to " model-name ". Error: " (.getMessage e)))
        {:error (str "Network or client exception: " (.getMessage e))}))))
