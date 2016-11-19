(ns dk.emcken.edifact.tokenizer-test
  (:require [clojure.test :refer :all]
            [dk.emcken.edifact.tokenizer :refer :all]))

(deftest contains-una?-test
  (testing "wether an edifact interchange (string) contains an UNA segment"
    (are [una? edifact] (= una? (contains-una? edifact))
      true "UNA:+.? 'UNB+IATB:1+6XPPC+LHPPC+940101:0950+1'"
      true "UNA;-,# ?UNB-IATB;1-6XPPC-LHPPC-940101;0950-1?"
      false "UNB+IATB:1+6XPPC+LHPPC+940101:0950+1'")))

(deftest split-by-una-test
  (testing "splitting up an edifact interchange at the UNA segment"
    (are [splitted edifact] (= splitted (split-by-una edifact))
      '("UNA:+.? '" "UNB+IATB:1+6XPPC+LHPPC+940101:0950+1'") "UNA:+.? 'UNB+IATB:1+6XPPC+LHPPC+940101:0950+1'"
      '(nil "UNB+IATB:1+6XPPC+LHPPC+940101:0950+1'") "UNB+IATB:1+6XPPC+LHPPC+940101:0950+1'")))
