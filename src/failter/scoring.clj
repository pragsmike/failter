(ns failter.scoring
  (:require [clojure.string :as str]))

;;; --- Multimethod Definitions ---
;;; We define a pluggable system for scoring. Each strategy must implement these three behaviors.

(defmulti get-prompt-instructions
  "Returns the instructional text to be injected into the evaluation prompt for a given strategy."
  :strategy)

(defmulti parse-raw-score
  "Parses the raw text response from the judge LLM and returns a normalized numeric score (0-100)."
  (fn [strategy _response-text] strategy))

(defmulti format-score-distribution
  "Takes a sequence of numeric scores and returns a formatted string representing their distribution."
  (fn [strategy _scores] strategy))


;;; --- Strategy 1: :letter-grade (The original method, now formalized) ---

(defmethod get-prompt-instructions :letter-grade [_]
  "## Grading Scale:\n
-   **A:** Perfect or near-perfect execution. All instructions followed. The output is clean and ready to use.
-   **B:** Good execution with minor flaws. For example, it might have missed one small pollution element or left a bit of extra whitespace.
-   **C:** Acceptable execution with noticeable errors. It may have failed to remove a major pollution element (like a post embed) or incorrectly deleted a small piece of valid content.
-   **D:** Poor execution. The model failed on multiple instructions or significantly damaged the original text.
-   **F:** Completely failed. The output is nonsensical, empty, or a complete deviation from the instructions.

## Output Format:\n
You MUST provide your response as a single YAML block. Do NOT include any other explanatory text or markdown formatting.
```yaml
grade: [Your Grade: A, B, C, D, or F]
rationale: [A concise, one-to-three sentence explanation for your grade. Be specific about what it did right or wrong.]
```")

(defmethod parse-raw-score :letter-grade [_ response-text]
  (let [grade-to-score {"A" 100, "B" 80, "C" 60, "D" 40, "F" 20, nil 0}
        grade-char (second (re-find #"(?m)^grade:\s*([A-DF])" response-text))]
    (get grade-to-score grade-char 0)))

(defmethod format-score-distribution :letter-grade [_ scores]
  (let [score-to-grade #(condp >= %
                          100 "A"
                          80 "B"
                          60 "C"
                          40 "D"
                          20 "F"
                          "F*")
        grades (map score-to-grade scores)
        dist (frequencies grades)]
    (pr-str (into (sorted-map-by #(compare %2 %1)) dist))))


;;; --- Strategy 2: :numeric-100 (The new, more direct method) ---

(defmethod get-prompt-instructions :numeric-100 [_]
  "## Scoring Scale:\n
Evaluate the output on a scale from 0 to 100, where 0 is a complete failure and 100 is a perfect result that exactly matches the intent of the prompt.

## Output Format:\n
You MUST provide your response as a single YAML block. Do NOT include any other explanatory text or markdown formatting.
```yaml
score: [A single integer between 0 and 100]
rationale: [A concise, one-to-three sentence explanation for your score. Be specific about what it did right or wrong.]
```")

(defmethod parse-raw-score :numeric-100 [_ response-text]
  (try
    (let [score-str (second (re-find #"(?m)^score:\s*(\d+)" response-text))]
      (Integer/parseInt score-str))
    (catch Exception _ 0)))

(defmethod format-score-distribution :numeric-100 [_ scores]
  (let [buckets (group-by #(quot % 10) scores)
        summary (into (sorted-map) (for [[k v] buckets] [(str (* k 10) "s") (count v)]))]
    (pr-str summary)))
