# kotoba-insurance

[![CI](https://github.com/kotoba-lang/insurance/actions/workflows/ci.yml/badge.svg)](https://github.com/kotoba-lang/insurance/actions/workflows/ci.yml)

**Policies, premiums and claims in pure Clojure.** A
[kotoba-lang](https://github.com/kotoba-lang) capability library for the
`cloud-itonami` insurance vertical (ISIC 6511 life insurance, 6512 non-life
insurance, 6520 reinsurance, 6530 pension funding, 6621 risk and damage
evaluation, 6622 insurance agents/brokers, 6629 other insurance auxiliary):
policy records (life/non-life), premium-quote arithmetic, claim records and
underwriting-decision records.

No network, no I/O, and **no real actuarial tables** — an operator supplies
their own licensed rate table; this library only combines a supplied rate
with policy facts. Amounts are plain numbers in the smallest currency unit.
Portable `.cljc` across JVM / ClojureScript / SCI / GraalVM.

## Maturity

| | |
|---|---|
| Role | capability |
| Tests | 131 assertions, all green |
| Operator console (UI/UX) | yes |
| Export (CSV/JSON) | yes |
| Shared CSS design system | yes (css.core/operator-theme) |

## Contract

```clojure
(require '[kotoba.insurance :as ins])

(ins/policy "P1" "Alice" :life 100000 "2026-01-01" "2036-01-01")
(ins/policy-active-on? policy "2026-06-01")           ; => true/false
(ins/premium-quote policy 5 :loading 0.1)             ; rate-per-mille + loading -> premium amount
(ins/claim "C1" "P1" "Alice" "2026-06-01" 5000)
(ins/claim-in-force? claim policy)                    ; loss occurred while coverage was active?
(ins/underwriting-decision "P1" :approve :risk-score 0.2)
```

## Operator console (UI/UX)

A read-only HTML dashboard renders policies, premium quotes and claims for
an operator. Built on [`kotoba-lang/html`](https://github.com/kotoba-lang/html)
(Hiccup→HTML) + [`kotoba-lang/css`](https://github.com/kotoba-lang/css)
(EDN→CSS). Pure data → markup; the console never exposes a write surface (no
`<form>`/`<button>`) — binding, endorsement and claim payout stay behind the
governor.

```clojure
(require '[kotoba.insurance.ui :as ui])

(ui/dashboard
  {:policies [(ins/policy "P1" "Alice" :life 100000 "2026-01-01" "2036-01-01")]
   :claims   [(ins/claim "C1" "P1" "Alice" "2026-06-01" 5000 :status :paid)]})
;; => "<html>...read-only · governor-gated...</html>"
```

## Japan health-insurance identifiers

Format and check-digit validation for the 保険者番号 (health-insurer
number, an 8-digit 法別番号(2) + 都道府県番号(2) + 保険者別番号(3) +
検証番号(1)) and the 医療機関コード (medical-institution code, a 10-digit
都道府県番号(2) + 点数表番号(1) + 郡市区番号(2) + 医療機関(薬局)番号(4) +
検証番号(1)) printed on Japanese health-insurance cards / claims forms, per
MHLW notification 別添２『保険者番号、公費負担者番号、公費負担医療の受給者
番号並びに医療機関コード及び薬局コード設定要領』(primary source verified
2026-07-08: https://www.mhlw.go.jp/content/12400000/001275316.pdf, dated
2024-07-12; archived at
[`kotoba-lang/emr-claims-primary-sources`](https://github.com/kotoba-lang/emr-claims-primary-sources)
alongside a 2008 predecessor notification independently confirming the
same 保険者番号 worked example — see ADR-2607084100). No real 法別番号 or
医療機関 registry, no diagnosis codes, no claims-adjustment logic — pure
shape/check-digit recomputation only.

```clojure
(require '[kotoba.insurance.jp :as jp])

(jp/valid-hokensha-bangou? "06130488")        ; => true  (MHLW worked example)
(jp/parse-hokensha-bangou "06130488")         ; => {:hokensha/houbetsu-bangou "06" ...}
(jp/validate-hokensha-bangou "0613048")       ; => {:insurance/valid? false :insurance/error :not-8-digits}
```

### 都道府県番号 (prefecture code)

The 2-digit 都道府県番号 shared by both 保険者番号 and 医療機関コード is
embedded as a 94-entry constant table: the 47 primary codes ("01"-"47",
JIS X 0401) plus the 47 alternate codes ("51"-"97", each primary code +
50) that 別添２ documents for use once a prefecture's primary-code range of
保険者別番号/医療機関(薬局)番号 numbers is exhausted. Both ranges are
equally valid, currently-issuable codes. Stable, public, decades-old data,
unlike the tens-of-thousands-of-rows 診療行為/薬剤/傷病名 masters that this
codebase deliberately does not embed elsewhere (see the iryo actor's
master-honesty ADR-2606074000).

```clojure
(jp/valid-todoufuken-bangou? "13") ; => true
(jp/todoufuken-name "13")          ; => "東京都"
(jp/todoufuken-name "63")          ; => "東京都" (alternate code, 13 + 50)
```

### 医療機関コード (medical-institution code) — full check-digit validation

A 医療機関コード is 10 digits: 都道府県番号(2) + 点数表番号(1) +
郡市区番号(2) + 医療機関(薬局)番号(4) + 検証番号(1), per 別添２ 第４. The
検証番号 algorithm is the *same* alternating-2/1 fold-mod10 formula as
保険者番号's, applied to the 9-digit body — verified against 別添２ 第４-５'s
own worked example (都道府県番号=34, 点数表番号=1, 郡市区番号=07,
医療機関番号=1236 → 検証番号=2, full code `3410712362`).

点数表番号 decodes to a category per 第４-５(１)「点数表番号は医科１、
歯科３、薬局４とするものとする」— only these three digits are implemented;
secondary sources additionally describe "2" (健診等機関) and "6" (訪問看護)
but the primary source consulted does not document either, so they are
deliberately left unmapped (`tensuhyou-bangou-category` returns `nil`)
rather than guessed at.

医療機関(薬局)番号 numeric ranges are validated per 第４-３: 医科
1,000-2,999 / 歯科 3,000-3,999 / 薬局 4,000-4,999. The ただし書き exclusion
(a 4-digit number whose middle two or last two digits read "90" is a
permanent 欠番/unassigned number) is exposed as a separate predicate,
`iryokikan-kikan-bangou-ketsuban?`, rather than folded into the main
validator.

```clojure
(jp/parse-iryokikan-bangou "3410712362")
;; => {:iryokikan/todoufuken-bangou "34" :iryokikan/tensuhyou-bangou "1"
;;     :iryokikan/gunshiku-bangou "07" :iryokikan/kikan-bangou "1236"
;;     :iryokikan/kenshou-bangou "2"}
(jp/iryokikan-bangou-check-digit "341071236")         ; => 2  (MHLW worked example)
(jp/valid-iryokikan-bangou? "3410712362")             ; => true
(jp/tensuhyou-bangou-category "1")                    ; => :medical
(jp/valid-iryokikan-kikan-bangou-range? "1" "1236")   ; => true (医科 1000-2999)
(jp/validate-iryokikan-bangou "3410712362")
;; => {:insurance/valid? true :iryokikan/category :medical ...}
(jp/validate-iryokikan-bangou "341071236X")
;; => {:insurance/valid? false :insurance/error :not-10-digits}
```

**Known gap, not implemented.** A secondary source (Wikipedia,
「処方箋発行医療機関コード」) describes a legacy exception for some
医療機関コード issued in 埼玉県・千葉県・東京都・神奈川県 before Japan's
1976 unification onto the current 10-digit scheme (descending from a 1967
7-digit scheme), whose 検証番号 is said to be computed over the bare
7-digit 医療機関コード body alone (郡市区番号+医療機関番号+検証番号),
omitting 都道府県番号 and 点数表番号 from the check-digit input entirely.
Neither primary source consulted for this library documents that
exception, so it is **not implemented** here — `iryokikan-bangou-check-digit`
and `valid-iryokikan-bangou?` always use the full 9-digit body. A caller
working with pre-1976 legacy codes from those four prefectures should not
trust this library's check-digit result.

## Export (CSV / JSON)

Audit-grade CSV (RFC-4180 quoting) and JSON (quote/backslash/newline
escaped) for policies and claims.

```clojure
(require '[kotoba.insurance.export :as ex])

(ex/policies->csv policies)
(ex/claims->csv claims)
(ex/claims->json claims)
```

## License

Apache License 2.0.

## Test

```bash
clojure -M:test
```
