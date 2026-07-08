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
  would* -- it does not know which 保険者別番号 are actually issued, and
  does not touch diagnosis codes, fee schedules, or any real
  claims-adjustment logic. Like the rest of kotoba.insurance, this is a
  pure function; an operator's real insurer-master lookup sits behind it.

  都道府県番号 (2-digit prefecture code, 01-47) is embedded below as a
  47-entry constant table (JIS X 0401, 1970) -- unlike the tens-of-
  thousands-of-rows 診療行為/薬剤/傷病名 masters that kotoba.insurance
  deliberately does *not* embed and instead ingests (see the iryo actor's
  master-honesty ADR-2606074000), a stable 47-entry public code table
  carries no meaningful maintenance/licensing burden.

  医療機関コード (medical-institution code, 9 digits: 都道府県番号(2) +
  点数表番号(1) + 郡市区番号(2) + 医療機関番号(3) + 検証番号(1), per the
  same 別添２ notification) is only *partially* covered: this namespace
  parses its shape and validates the 都道府県番号 sub-field against the
  same 47-entry table, but does **not** compute or verify its 検証番号
  (check digit) and does **not** decode 点数表番号 into a 医科/歯科/薬局
  category. Both are left as 要検証 (see NOTE on parse-iryokikan-bangou
  below) rather than guessed at, because this codebase has no worked
  example to test either against -- unlike hokensha-bangou-check-digit,
  which is verified against 別添２ 第1-5's own worked example."
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

;; ---------------------------------------------------------------------
;; 都道府県番号 (prefecture code, 01-47)
;; ---------------------------------------------------------------------

(def todoufuken-names
  "都道府県番号 (2-digit string, 01-47) -> 都道府県名. This is the JIS X
  0401 (制定 1970) prefecture-code assignment, which is public, stable and
  only 47 entries -- general knowledge, not a proprietary/copyrighted
  master. There is no primary-source PDF available in this environment to
  cross-check every entry against; this table is written from that
  well-known, long-standing public assignment. If any single entry is
  ever found to be wrong, fix that entry rather than distrust the whole
  table -- the code<->prefecture pairing has been stable for decades and
  is reused verbatim as the 都道府県番号 field inside both 保険者番号 and
  医療機関コード."
  {"01" "北海道"   "02" "青森県"   "03" "岩手県"   "04" "宮城県"
   "05" "秋田県"   "06" "山形県"   "07" "福島県"   "08" "茨城県"
   "09" "栃木県"   "10" "群馬県"   "11" "埼玉県"   "12" "千葉県"
   "13" "東京都"   "14" "神奈川県" "15" "新潟県"   "16" "富山県"
   "17" "石川県"   "18" "福井県"   "19" "山梨県"   "20" "長野県"
   "21" "岐阜県"   "22" "静岡県"   "23" "愛知県"   "24" "三重県"
   "25" "滋賀県"   "26" "京都府"   "27" "大阪府"   "28" "兵庫県"
   "29" "奈良県"   "30" "和歌山県" "31" "鳥取県"   "32" "島根県"
   "33" "岡山県"   "34" "広島県"   "35" "山口県"   "36" "徳島県"
   "37" "香川県"   "38" "愛媛県"   "39" "高知県"   "40" "福岡県"
   "41" "佐賀県"   "42" "長崎県"   "43" "熊本県"   "44" "大分県"
   "45" "宮崎県"   "46" "鹿児島県" "47" "沖縄県"})

(defn valid-todoufuken-bangou?
  "True when s is one of the 47 two-digit 都道府県番号 (\"01\"-\"47\")."
  [s]
  (contains? todoufuken-names (str s)))

(defn todoufuken-name
  "都道府県名 for a 2-digit 都道府県番号 string, or nil when s is not one
  of \"01\"-\"47\"."
  [s]
  (get todoufuken-names (str s)))

;; ---------------------------------------------------------------------
;; 医療機関コード (medical-institution code, 9 digits) -- shape + 都道府県
;; only. See the namespace docstring and the NOTE on parse-iryokikan-bangou
;; for exactly what is and is not covered.
;; ---------------------------------------------------------------------

(defn parse-iryokikan-bangou
  "Split a 9-digit 医療機関コード string into its components: 都道府県番号
  (2) + 点数表番号 (1) + 郡市区番号 (2) + 医療機関番号 (3) + 検証番号 (1).
  Returns nil when s is not exactly 9 decimal digits.

  NOTE (要検証 -- deliberately unimplemented, not guessed at):
  - :iryokikan/tensuhyou-bangou is returned as a raw 1-digit string, not
    decoded into a category keyword. Sources handed to this namespace
    disagree on the digit<->category mapping (one says 医科=1/歯科=2/
    薬局=3, another says 医科=1/歯科=3/薬局=4); without a primary-source
    PDF available in this environment to resolve the discrepancy, this
    namespace does not encode either guess as fact.
  - This namespace does not compute or verify the trailing :iryokikan/
    kenshou-bangou (check digit) at all -- see valid-iryokikan-todoufuken-
    bangou? and validate-iryokikan-bangou-shape, both of which are
    explicitly *shape + 都道府県番号 only*. hokensha-bangou-check-digit's
    alternating-2/1-mod10 algorithm is verified above against 別添２
    第1-5's own worked example (body 0613048 -> check digit 8); no
    equivalent worked example for 医療機関コード is available in this
    codebase, so this namespace does not assert (by implementing it) that
    the same algorithm applies to a differently-shaped body without a
    worked example to test it against."
  [s]
  (when (re-matches #"\d{9}" (str s))
    {:iryokikan/todoufuken-bangou (subs s 0 2)
     :iryokikan/tensuhyou-bangou  (subs s 2 3)
     :iryokikan/gunshiku-bangou   (subs s 3 5)
     :iryokikan/kikan-bangou      (subs s 5 8)
     :iryokikan/kenshou-bangou    (subs s 8 9)}))

(defn valid-iryokikan-todoufuken-bangou?
  "True when s is a 9-digit 医療機関コード whose leading 都道府県番号 is
  one of \"01\"-\"47\". Does not check the 検証番号 -- see the NOTE on
  parse-iryokikan-bangou."
  [s]
  (boolean
    (when-let [{:iryokikan/keys [todoufuken-bangou]} (parse-iryokikan-bangou s)]
      (valid-todoufuken-bangou? todoufuken-bangou))))

(defn validate-iryokikan-bangou-shape
  "Return a *shape-only* validation result for a 医療機関コード, in the
  same {:insurance/valid? ... :insurance/error ...} shape as
  validate-hokensha-bangou -- checking only digit-count and 都道府県番号
  range. A true result means 'shape and prefecture code are plausible',
  not 'this is a checksum-correct 医療機関コード': the 検証番号 is not
  verified (see the NOTE on parse-iryokikan-bangou for why)."
  [s]
  (cond
    (not (re-matches #"\d{9}" (str s)))
    {:insurance/valid? false :insurance/error :not-9-digits}

    (not (valid-iryokikan-todoufuken-bangou? s))
    {:insurance/valid? false :insurance/error :bad-todoufuken-bangou}

    :else
    (merge {:insurance/valid? true} (parse-iryokikan-bangou s))))
