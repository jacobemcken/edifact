(ns dk.emcken.edifact.transformer)

;; Decide whether to fail or ignore segments (and segment groups)
;; that doesn't have a transformer

;; The following allows to avoid duplicated specification when nesting Mapping a
;; funtion for each segment type allows different transformation of the same
;; segment but at the same time reuse

;; This needs support for allowing several segments of the same type to be grouped in a list


(def group-identifiers
  #{clojure.lang.Keyword nil})

(defn transform-dispatcher
  [_ _ [segment-identifier]]
  (if (contains? group-identifiers (type segment-identifier))
    ::group
    ::segment))

(defmulti transform #'transform-dispatcher)

(defmethod transform ::group
  [spec m [grp-id nested-segments]]
  (assoc m grp-id (reduce #(transform (get spec grp-id) %1 %2) {} nested-segments)))

(defmethod transform ::segment
  [spec m [[segment-name] :as segment]]
  (if-let [transform-fn (get spec segment-name)]
    (apply transform-fn [m segment])
    m))




