(ns dk.emcken.edifact.validator
  "Validation rules for the tokenized edifact data is described using Clojure data structures.

  - nil will ignore the that part of the data completely.
  - string (java.lang.String) indicates exact match.
  - Regular expression (java.util.regex.Pattern) allows for more dynamic matching.
  - hashmap (clojure.lang.PersistentHashSet) matches agains any of the sets
    members but only strings are supported at the moment.
  - predicate (clojure.lan.IFn) a function or multimethod that takes the data an
    returns truthy/falsey depending on the validity of the data.


Rule describing an entire segment:

    [[#{\"MR\" \"MS\"} nil \"5791003344891\"] [empty? #\"^[0-9]+$\"]

Using the above rule the following data is found valid:

    [[\"NAD\"] [\"MR\" \"anything R34LLY!\" \"5791003344891\"] [\"\" \"042\"]]

Remember only the first error is returned.")

(def number #"^[1-9]{1}[0-9]*$")

(def not-empty? (comp not empty?))

(defmulti validate
  "Validates an edifact component. Returns string with error message upon failure
  otherwise nil."
  (fn [component-rule _] (type component-rule)))

(defmethod validate java.lang.String
  [component-rule component]
  (when (not= component-rule component)
    (str "Must match \"" component-rule "\"")))

(defmethod validate clojure.lang.PersistentHashSet
  [component-rule component]
  (when (not (contains? component-rule component))
    "Must match item in set"))

(defmethod validate clojure.lang.IFn
  [component-rule-fn component]
  (when (not (component-rule-fn component))
    "Must conform to predicate"))

(defmethod validate java.util.regex.Pattern
  [component-rule-pattern component]
  (when (not (re-find component-rule-pattern component))
    "Must match regular expression"))

(defmethod validate nil
  [_ _]
  nil)

(defn validate-element
  "Takes a list of validation rules describing an element and returns a hashmap
  describing the first component in that element that doesn't conform."
  [rules element position]
  (let [validation (map validate rules (concat element (repeat nil)))]
    (first (keep-indexed #(when %2 {:component (inc %1)
                                    :element (+ 2 position)
                                    :error %2}) validation))))

(defn validate-segment
  "Takes a list of validation rules describing a segment and returns a hashmap
  describing the first element/component in that segment that doesn't conform."
  [rules elements]
  (first (remove nil? (map validate-element rules (concat elements (repeat '())) (range)))))
