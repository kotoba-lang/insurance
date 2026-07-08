(ns kotoba.insurance.jp
  "Japan health-insurance identifiers -- pure format / check-digit validation.

  The 保険者番号 (hokensha-bangou, health-insurer number) printed on a
  Japanese health-insurance card (被保険者証) is an 8-digit number: a
  2-digit 法別番号 (insurance-category code), a 2-digit 都道府県番号
  (prefecture code), a 3-digit 保険者別番号 (insurer serial) and a 1-digit
  検証番号 (check digit) computed from the first seven digits.

  Field widths and the check-digit algorithm are taken verbatim from the
  Ministry of Health, Labour and Welfare notification 別添２ 『保険者番号、
  公費負担者番号、公費負担医療の受給者番号並びに医療機関コード及び薬局コード
  設定要領』 第1 -- weights of 2 and 1 alternate starting from the
  rightmost digit of the 7-digit body, a doubled digit >= 10 is folded to
  its own digit sum, and the check digit is 10 minus the last digit of that
  weighted sum (wrapping 10 -> 0). Verified against 第1-5's own worked
  example: body 0613048 -> weighted sum 22 -> check digit 8 -> full number
  06130488.

  This namespace only recognizes the *shape* of the number (digit
  count/regex) and *recomputes the same check digit an insurer's system
  would* -- it does not look up a real 法別番号/都道府県番号 registry,
  does not know which numbers are actually issued, and does not touch
  医療機関コード (medical-institution codes), diagnosis codes, fee
  schedules, or any real claims-adjustment logic. Like the rest of
  kotoba.insurance, this is a pure function; an operator's real
  insurer-master lookup sits behind it."
  (:require [clojure.string :as str]))

(defn- digit->int
  "Portable char / 1-char-string -> 0-9 int, or nil if c is not a decimal
  digit. Works the same on JVM Characters and ClojureScript's 1-char
  strings, since (str c) normalizes either to a 1-char string."
  [c]
  (str/index-of "0123456789" (str c)))

(defn- fold-digits
  "Sum of the decimal digits of a non-negative int < 100. A doubled digit
  is at most 18 (9 * 2), so this is at most a two-term sum."
  [n]
  (if (< n 10) n (+ (quot n 10) (rem n 10))))

(defn hokensha-bangou-check-digit
  "Compute the 検証番号 (check digit, an int 0-9) for a 7-digit body
  (法別番号 2 + 都道府県番号 2 + 保険者別番号 3, as a 7-character digit
  string or anything str-able to one). Returns nil when body is not
  exactly 7 decimal digits."
  [body]
  (when (re-matches #"\d{7}" (str body))
    (let [digits (mapv digit->int (seq (str body)))
          n      (count digits)
          total  (reduce +
                          (map-indexed
                            (fn [i d]
                              (let [weight (if (even? (- n 1 i)) 2 1)]
                                (fold-digits (* d weight))))
                            digits))]
      (mod (- 10 (mod total 10)) 10))))

(defn parse-hokensha-bangou
  "Split an 8-digit 保険者番号 string into its components. Returns nil when
  s is not exactly 8 decimal digits. Does not check the check digit -- see
  valid-hokensha-bangou?."
  [s]
  (when (re-matches #"\d{8}" (str s))
    {:hokensha/houbetsu-bangou       (subs s 0 2)
     :hokensha/todoufuken-bangou     (subs s 2 4)
     :hokensha/hokensha-betsu-bangou (subs s 4 7)
     :hokensha/kenshou-bangou        (subs s 7 8)}))

(defn valid-hokensha-bangou?
  "True when s is an 8-digit 保険者番号 whose 8th digit matches the check
  digit recomputed from its first 7 digits."
  [s]
  (boolean
    (when-let [{:hokensha/keys [houbetsu-bangou todoufuken-bangou
                                 hokensha-betsu-bangou kenshou-bangou]}
               (parse-hokensha-bangou s)]
      (= (str (hokensha-bangou-check-digit
                (str houbetsu-bangou todoufuken-bangou hokensha-betsu-bangou)))
         kenshou-bangou))))

(defn validate-hokensha-bangou
  "Return a validation result for a 保険者番号, in the same
  {:insurance/valid? ... :insurance/error ...} shape as
  kotoba.insurance/validate-policy and validate-claim."
  [s]
  (cond
    (not (re-matches #"\d{8}" (str s)))
    {:insurance/valid? false :insurance/error :not-8-digits}

    (not (valid-hokensha-bangou? s))
    {:insurance/valid? false :insurance/error :bad-check-digit}

    :else
    (merge {:insurance/valid? true} (parse-hokensha-bangou s))))
