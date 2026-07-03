(ns kotoba.insurance.export-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.insurance :as ins]
            [kotoba.insurance.export :as ex]))

(deftest csv-export
  (let [csv (ex/policies->csv [(ins/policy "P1" "Alice" :life 100000 "2026-01-01" "2036-01-01")])]
    (is (re-find #"policy_id,holder,type,coverage,currency,start,end" csv))
    (is (re-find #"P1,Alice,life,100000" csv))))

(deftest claims-csv-export
  (let [csv (ex/claims->csv [(ins/claim "C1" "P1" "Alice" "2026-06-01" 5000)])]
    (is (re-find #"claim_id,policy,claimant" csv))
    (is (re-find #"C1,P1,Alice" csv))))
