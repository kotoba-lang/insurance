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

;; ---------------------------------------------------------------------
;; 都道府県番号 (prefecture code, 01-47)
;; ---------------------------------------------------------------------

(deftest todoufuken-names-test
  (is (= 47 (count jp/todoufuken-names))) ; exactly 47 prefectures, no more/less
  (is (= "北海道" (jp/todoufuken-name "01")))
  (is (= "東京都" (jp/todoufuken-name "13")))
  (is (= "沖縄県" (jp/todoufuken-name "47")))
  (is (nil? (jp/todoufuken-name "00")))
  (is (nil? (jp/todoufuken-name "48")))
  (is (nil? (jp/todoufuken-name nil))))

(deftest valid-todoufuken-bangou-test
  (is (true? (jp/valid-todoufuken-bangou? "01")))
  (is (true? (jp/valid-todoufuken-bangou? "47")))
  (is (false? (jp/valid-todoufuken-bangou? "00")))
  (is (false? (jp/valid-todoufuken-bangou? "48")))
  (is (false? (jp/valid-todoufuken-bangou? "1"))) ; not zero-padded to 2 digits
  (is (false? (jp/valid-todoufuken-bangou? nil))))

;; ---------------------------------------------------------------------
;; 医療機関コード (medical-institution code, 9 digits) -- shape + 都道府県
;; only; no check-digit oracle is available, see jp.cljc NOTE.
;; ---------------------------------------------------------------------

(deftest parse-iryokikan-bangou-test
  (is (= {:iryokikan/todoufuken-bangou "13"
          :iryokikan/tensuhyou-bangou  "1"
          :iryokikan/gunshiku-bangou   "23"
          :iryokikan/kikan-bangou      "456"
          :iryokikan/kenshou-bangou    "7"}
         (jp/parse-iryokikan-bangou "131234567")))
  (is (nil? (jp/parse-iryokikan-bangou "13123456")))   ; 8 digits, not 9
  (is (nil? (jp/parse-iryokikan-bangou "131234567X"))) ; non-numeric
  (is (nil? (jp/parse-iryokikan-bangou nil))))

(deftest valid-iryokikan-todoufuken-bangou-test
  (is (true? (jp/valid-iryokikan-todoufuken-bangou? "131234567")))  ; 13 = 東京都
  (is (false? (jp/valid-iryokikan-todoufuken-bangou? "001234567"))) ; 00 out of range
  (is (false? (jp/valid-iryokikan-todoufuken-bangou? "481234567"))) ; 48 out of range
  (is (false? (jp/valid-iryokikan-todoufuken-bangou? "13123456")))  ; too short
  (is (false? (jp/valid-iryokikan-todoufuken-bangou? nil))))

(deftest validate-iryokikan-bangou-shape-test
  (is (true? (:insurance/valid? (jp/validate-iryokikan-bangou-shape "131234567"))))
  (is (= "13" (:iryokikan/todoufuken-bangou (jp/validate-iryokikan-bangou-shape "131234567"))))
  (is (= :not-9-digits (:insurance/error (jp/validate-iryokikan-bangou-shape "13123456"))))
  (is (= :bad-todoufuken-bangou (:insurance/error (jp/validate-iryokikan-bangou-shape "001234567")))))
