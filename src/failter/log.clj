(ns failter.log
  "Central logging façade for Failter.

  * Wraps Taoensso Telemere so that callers only import this namespace.
  * Injects a custom timestamp format that can be overridden via env vars.
  * Exposes thin `info/warn/error/debug` macros that are drop‑in println replacements.
  * Initialises a single console handler on first require; safe to require from
    any namespace without double‑registration.

  ### Env‑vars
  | Var | Purpose | Example |
  |------|---------|---------|
  | `FAILTER_LOG_TS_PATTERN` | DateTimeFormatter pattern | `yyyy-MM-dd HH:mm:ss.SSS` |
  | `FAILTER_LOG_TZ`         | IANA/Java TZ ID            | `America/New_York` |
  | `FAILTER_LOG_HANDLER`    | Handler key (rare)         | `:console-2` |

  ### Usage
  ```clj
  (ns my.ns
    (:require [failter.log :as log]))

  (log/info \"Hello\" {:a 1})
  ```
  Produces:
  ```
  2025-06-27 16:12:03.456 INFO  my.ns                    | Hello {:a 1}
  ```"
  (:require [taoensso.telemere          :as tel]
            [taoensso.telemere.utils   :as u])
  (:import (java.time.format DateTimeFormatter)
           (java.time        ZoneId)))


(defn setup-logging!
  "Configures Timbre with sane defaults for the application."
  []
  (tel/set-min-level! :info)
  (tel/call-on-shutdown!
   (fn [] (tel/stop-handlers!))))


;; -----------------------------------------------------------------------------
;; Configuration helpers
;; -----------------------------------------------------------------------------
(def ^:private default-ts-pattern "yyyy-MM-dd HH:mm:ss.SSS")

(defn- env-or [k fallback]
  (if-let [v (System/getenv k)] v fallback))

(defn- ->zone-id [s]
  (cond
    (nil? s) (ZoneId/systemDefault)
    (instance? ZoneId s) s
    :else (ZoneId/of (str s))))

;; -----------------------------------------------------------------------------
;; Handler / output‑fn construction
;; -----------------------------------------------------------------------------

(defonce ^:private initialized? (atom false))

(defn init!
  "Idempotently register the console handler with custom timestamp formatting.
  Accepts optional opts map:
  ```clj
  {:handler-id :console  ;; key passed to add-handler!
   :pattern    \" HH:mm:ss \"  ;; force pattern (overrides env)
   :zone       \" UTC \"}      ;; force TZ (overrides env)
  ```"
  ([] (init! {}))
  ([{:keys [handler-id pattern zone] :or {handler-id (keyword (env-or "FAILTER_LOG_HANDLER" "console"))}}]
   (when-not @initialized?
     (let [pattern'  (or pattern (env-or "FAILTER_LOG_TS_PATTERN" default-ts-pattern))
           zone'     (->zone-id (or zone (System/getenv "FAILTER_LOG_TZ")))
           zone      (.getOffset (java.time.ZonedDateTime/now zone'))
           formatter (java.time.format.DateTimeFormatter/ofPattern pattern')
           ts->str   (u/format-inst-fn {:formatter formatter :zone zone})
           output-fn (u/format-signal-fn
                      {:preamble-fn nil
                       :content-fn
                       (fn [{:keys [inst level ns msg_]}]
                         (format "%s %-5s %-25s | %s"
                                 (ts->str inst)
                                 (u/format-level level)
                                 ns msg_))})
           ]
       (tel/add-handler! handler-id (tel/handler:console {:output-fn output-fn}))
       (reset! initialized? true)))))

;; Ensure logging is ready as soon as namespace loads.
(tel/remove-handler! :default/console)
(init!)

;; -----------------------------------------------------------------------------
;; Facade macros (avoid reflection on varargs)
;; -----------------------------------------------------------------------------
(defmacro info [& args]  `(tel/log! {:level :info, :msg ~@args}))
(defmacro warn [& args]  `(tel/log! {:level :warn, :msg ~@args}))
(defmacro error [& args] `(tel/log! {:level :error, :msg ~@args}))
(defmacro debug [& args] `(tel/log! {:level :debug, :msg ~@args}))
