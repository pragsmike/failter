(ns failter.frontmatter
  (:require [clojure.string :as str]
            [clj-yaml.core :as yaml]))

(def frontmatter-regex #"(?s)^---\s*\n(.*?)\n---\s*\n(.*)")

(defn parse-file-content
  "Parses a string into a map with :frontmatter and :body.
  If no valid frontmatter is found, returns an empty map for :frontmatter
  and the original string as the :body."
  [content]
  (if-let [matches (re-matches frontmatter-regex content)]
    (let [[_ yaml-str body-str] matches]
      (try
        {:frontmatter (yaml/parse-string yaml-str)
         :body body-str}
        (catch Exception _e
          ;; In case of malformed YAML, treat it as no frontmatter.
          {:frontmatter {}
           :body content})))
    ;; No frontmatter found
    {:frontmatter {}
     :body content}))

(defn serialize
  "Takes a frontmatter map and a body string, and returns a single
  string with YAML frontmatter."
  [frontmatter body]
  (if (empty? frontmatter)
    body
    (str "---\n"
         (yaml/generate-string frontmatter :flow-style :block)
         "---\n"
         body)))
