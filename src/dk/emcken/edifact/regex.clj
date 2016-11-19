(ns dk.emcken.edifact.regex
  (:require [clojure.string :as string]))

;; Characters that needs escaping outside character classes
;; http://stackoverflow.com/questions/399078/what-special-characters-must-be-escaped-in-regular-expressions
(def special-chars
  #{\. \^ \$ \* \+ \? \( \) \[ \{ \\ \| \: \'})

(defn escape
  "Escape character with special meaning for regex patterns"
  [c]
  (str (when (contains? special-chars c) \\) c))

(defn escape-str
  [s]
  (apply str (map escape s)))

(defn split-pattern
  [seperator release-char]
  (re-pattern (str "(?<!" (escape release-char) ")" (escape seperator))))

(defn escaped-chars-table
  [seperators release-char]
  (reduce #(assoc %1 (str release-char %2) (str %2)) {} seperators))

(defn unescape-pattern
  [escaped-table]
  (->>
   escaped-table
   (keys)
   (map escape-str)
   (string/join "|")
   (re-pattern)))

(defn unescape-fn
  [escaped-table]
  (let [replace-pattern (unescape-pattern escaped-table)]
    (fn [element]
      (if (string? element)
       (string/replace element replace-pattern escaped-table)
       element))))
