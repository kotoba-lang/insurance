(ns kotoba.insurance.jp-test
  (:require [clojure.test :refer [deftest is]]
            [kotoba.insurance.jp :as jp]))

;; Worked example from MHLW 別添２ 第1-5: body 0613048 -> weighted sum 22
;; -> check digit 8 -> full 保険者番号 06130488.
(deftest check-digit-worked-example
  (is (= 8 (jp/hokensha-bangou-check-digit "0613048"))))

(deftest check-digit-zero-wrap
  ;; All-zero body -> weighted sum 0 -> "1の位の数が0のときは検証番号を0" ->
  ;; check digit 0 (an algorithmic edge case, not a claim that 00000000 is
  ;; an issued insurer number).
  (is (= 0 (jp/hokensha-bangou-check-digit "0000000"))))

(deftest check-digit-bad-input
  (is (nil? (jp/hokensha-bangou-check-digit "061304")))   ; 6 digits
  (is (nil? (jp/hokensha-bangou-check-digit "0613048X"))) ; non-numeric
  (is (nil? (jp/hokensha-bangou-check-digit nil))))

(deftest parse-test
  (is (= {:hokensha/houbetsu-bangou       "06"
          :hokensha/todoufuken-bangou     "13"
          :hokensha/hokensha-betsu-bangou "048"
          :hokensha/kenshou-bangou        "8"}
         (jp/parse-hokensha-bangou "06130488")))
  (is (nil? (jp/parse-hokensha-bangou "0613048")))   ; 7 digits, not 8
  (is (nil? (jp/parse-hokensha-bangou "06130488X"))) ; non-numeric
  (is (nil? (jp/parse-hokensha-bangou nil))))

(deftest valid-test
  (is (true? (jp/valid-hokensha-bangou? "06130488")))
  (is (false? (jp/valid-hokensha-bangou? "06130480"))) ; wrong check digit
  (is (false? (jp/valid-hokensha-bangou? "0613048")))  ; too short
  (is (false? (jp/valid-hokensha-bangou? "0613048X"))) ; non-numeric
  (is (false? (jp/valid-hokensha-bangou? nil))))

(deftest validate-hokensha-bangou-test
  (is (true? (:insurance/valid? (jp/validate-hokensha-bangou "06130488"))))
  (is (= "06" (:hokensha/houbetsu-bangou (jp/validate-hokensha-bangou "06130488"))))
  (is (= :not-8-digits (:insurance/error (jp/validate-hokensha-bangou "0613048"))))
  (is (= :bad-check-digit (:insurance/error (jp/validate-hokensha-bangou "06130480")))))
