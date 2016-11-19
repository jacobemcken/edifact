(ns dk.emcken.edifact.tokenizer
  (:require [clojure.string :as string]
            [dk.emcken.edifact.regex :as regex]))

(def una-length 9)

(def default-special-chars
  {:component \:  ; component separator
   :element \+    ; element separator
   :decimal \.    ; decimal notification
   :segment \'    ; segment terminator
   :escape \?     ; escape/release character
   })

(defn extract-chars
  "Extract characters used as separators and indicators for the
  interchange if the optional UNA segment exist."
  [una-segment]
  (if-let [[_ _ _ component element decimal escape _ segment] una-segment]
    (hash-map
     :component component
     :element element
     :decimal decimal
     :segment segment
     :escape escape)
    default-special-chars))

(defn contains-una?
  "Returns whether an edifact contains the optional UNA segment"
  [edifact]
  (= 0 (string/index-of edifact "UNA")))

(defn split-by-una
  [edifact]
  (if (contains-una? edifact)
    (list (subs edifact 0 una-length) (subs edifact una-length))
    (list nil edifact)))

(defn tokenize-str
  "Takes a string that is a complete part of EDIFACT data.
  Several concatinated segments (usually a whole interchange) will be returned
  as a list of segments. A single segment will be returned as a list of elements.
  A single element will be returned as a list of components. The splitting
  respects the release (escape) character.

  Using \"?\" as release character on the string \"abc+123?+456\" will retult in:
[\"abc\" \"123?+456\"]"
  [s seperator release-char]
  (string/split s (regex/split-pattern seperator release-char)))

(defn recursive-split
  "Splits edifact-chunk (string) using the first character in the list of
  seperators, directly followed by unescaping escaped split
  characters. Doing this recursive until list of seperators have been
  exhausted."
  [edifact-chunk seperators escape]
  (let [sperated-data (tokenize-str edifact-chunk (first seperators) escape)]
    (if-let [new-seps (not-empty (rest seperators))]
      (map #(recursive-split % new-seps escape) sperated-data)
      sperated-data)))

(defn sanitize
  "Removes all newline characters from edifact which is sometimes used
  to make interchagens human readable."
  [edifact]
  (-> edifact
      (string/trim)
      (string/replace #"[\n\r]" "")))

(defn tokenize-interchange
  "Takes an edifact interchange without an UNA segment and return a tokenized
  representation of the same. It is highly likely that the function you are
  looking for is \"tokenize\" instead of this one."
  [edifact-interchange {:keys [escape] :as special-chars}]
  (let [seperators ((juxt :segment :element :component) special-chars)
        escaped-table (regex/escaped-chars-table seperators escape)
        unescape (regex/unescape-fn escaped-table)]
    (->>
     (recursive-split edifact-interchange seperators escape)
     (clojure.walk/postwalk unescape))))

(defn tokenize
  "Takes an edifact interchange and returns a tokenized representation of that
  interchange. If the interchange contains an UNA segment the seperators
  specified here will be used, alternatively default seperators will be used for
  tokenizing the interchange."
  [edifact]
  (let [[una-segment remaining-edifact] (split-by-una (sanitize edifact))]
    (tokenize-interchange remaining-edifact (extract-chars una-segment))))
