(ns dk.emcken.edifact.schema
  "A schema is used to verify an edifact message integrity. A schema
can only be applied to a tokenized edifact representation. A schema
is bacically just a list of rules describing both order and contents
of the segments contained within the edifact.

A rule is described using a vector and can either describe a segment
or a segment group.

Segment:

  [tag repetition & validations]

The segment tag is a string of 3 characters containing the name of the
segment. The segment repetition is described using either an integer
telling exactly how many times the segments is expected to be repeated
or a vector enclosing the minimum and maximum times a segment can be
repeated. The validation allows for describing the content of the
segment using complex data structures (very flexible).

Segment-group:

  [:group repetition specification]

The repetition works just as with a segment and the specification can
be either a list of rules (as described above) or a function that
returns a list of rules."
  (:require [dk.emcken.edifact.validator :as validator]))

(declare schemafy-group)

(defn handle-group
  "Segment groups can sometime be described by a list of rules other times
  dynamicly handled by a function (or multimethod). This handler automatically
  dispatches the segments to correct handler."
  [handler-or-rules segments]
  (schemafy-group
   (if (ifn? handler-or-rules)
     (handler-or-rules segments)
     handler-or-rules)
   segments))

(defmulti match
  "Returns a vector describing the remaining unmatched segments and
the schemafication."
  (fn [[rule-name] _] (type rule-name)))

(defmethod match java.lang.String
  [[rule-tag _ validation] [[[segment-tag] & elements :as segment] & remaining-segments :as segments]]
  (when (= rule-tag segment-tag)
    (if-let [error (validator/validate-segment validation elements)]
      (throw (ex-info "Unable to validate segment" (assoc error :type ::validation :segment (count segments))))
      (vector remaining-segments segment))))

(defmethod match clojure.lang.Keyword
  [[group-name _ rules] segments]
  (try
    (let [[remaining-segments schemafication] (handle-group rules segments)]
      (when-not (nil? schemafication)
        (vector remaining-segments (vector group-name schemafication))))
    (catch clojure.lang.ExceptionInfo e
      ;; Structure errors inside segment groups are allowed
      ;; because when a segment group cease to repeat the structure won't match
      (when-not (= (get (ex-data e) :type) ::structure)
        (throw e)))))

(defn dec-rule
  "Takes a rule and return a new rule with repetition counters decremented or
  nil (essential skipping the rule) if max repetition is reached."
  [[rule-name [min-rep max-rep] & validation]]
  (when (> max-rep 1)
    [(apply conj [rule-name [(dec min-rep) (dec max-rep)]] validation)]))

(defn skip-rule
  [[rule-name [min-rep _] & _] segments]
  (when (pos? min-rep)
    (let [error-msg (str "Unable to find mandatory segment: " rule-name)]
      (throw (ex-info error-msg
                      {:type ::structure
                       :segment (count segments)
                       :error error-msg})))))

(defn apply-rules
  [[rule & rest-rules] segments]
  (let [[remaining-segments schemafication] (match rule segments)]
    (vector
     (concat (if schemafication (dec-rule rule) (skip-rule rule segments)) rest-rules)
     (if schemafication remaining-segments segments) ; why not always remaining segments?
     schemafication)))

(defn schemafy-group
  "Returns a vector containing of remaining segments not matched
against the rules and the schemafication of the segments that where
matched. When the rules match the segments perfectly there wont be
any remaining segments. If a validation error occurs it will return
a map describing the error."
  [initial-rules initial-segments]
  (loop [rules initial-rules
         segments initial-segments
         schemafied-edifact []]
    (if (empty? rules)
      (vector segments schemafied-edifact)
      (let [[new-ruleset remaining-segments schemafication] (apply-rules rules segments)]
        (recur new-ruleset
               remaining-segments
               (if (nil? schemafication)
                 schemafied-edifact
                 (conj schemafied-edifact schemafication)))))))


(defmulti schema-based-on-unh
  "Multi method to hook in new types of edifact messages"
  (fn [[[_ _ identifier]]]
    identifier))

;; All functions used by segment groups must return nil if the segments it is
;; supposed to parse doesn't match. This is due to the fact that a schema group
;; can exist multiple times and there is no way for the parser to know if it is
;; the first iteration or a subsequent one AND maybe the the segment group is
;; optionally.
(defmethod schema-based-on-unh :default [_] nil)

(defn wrap-in-message
  "A convinience function to wrap a schema within rules about UNH and UNT
  segments. These 2 segments are mandatory for all messages in an EDIFACT
  interchange."
  [rules]
  (concat '(["UNH" [1 1]]) rules '(["UNT" [1 1]])))

(defn schemafy
  "Takes a tokenized edifact envelope and applies schemafication returned by the
  message-dispatcher. An edifact envelope can contain multiple edifact
  messages."
  [tokenized-edifact message-dispatcher]
  ;; An EDIFACT message have the same data structure as a segment group
  (schemafy-group
   (list ["UNB" [1 1]]
         [:sg0 [1 999] (comp wrap-in-message message-dispatcher)]
         ["UNZ" [1 1]])
   tokenized-edifact))
