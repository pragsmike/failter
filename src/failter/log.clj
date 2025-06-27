(ns failter.log
  (:require [taoensso.telemere :as tel]))

(defn setup-logging!
  "Configures Timbre with sane defaults for the application."
  []
  (tel/set-min-level! :info))

(defn shutdown-logging! []
  (tel/call-on-shutdown!
   (fn [] (tel/stop-handlers!))))


(defn my-format-nsecs-fn [nsecs]
  (let [inst (java.time.Instant/ofEpochMilli (quot nsecs 1000000))]
    (.toString inst))) ; Or use clj-time, java.time.format, etc.

#_(tel/remove-handler! :default/console)
#_(tel/add-handler! :console (tel/handler:console {:output-fn println}))


;; Re-export the core telemere logging macros under our own namespace.
(defmacro info [& args]  `(tel/log! {:level :info, :msg ~@args}))
(defmacro warn [& args]  `(tel/log! {:level :warn, :msg ~@args}))
(defmacro error [& args] `(tel/log! {:level :error, :msg ~@args}))
(defmacro debug [& args] `(tel/log! {:level :debug, :msg ~@args}))
