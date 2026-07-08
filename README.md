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
| Tests | 74 assertions, all green |
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
number) printed on a Japanese health-insurance card — an 8-digit
法別番号(2) + 都道府県番号(2) + 保険者別番号(3) + 検証番号(1) structure,
per MHLW notification 別添２. No real 法別番号 registry, no diagnosis
codes, no claims-adjustment logic — pure shape/check-digit recomputation
only.

```clojure
(require '[kotoba.insurance.jp :as jp])

(jp/valid-hokensha-bangou? "06130488")        ; => true  (MHLW worked example)
(jp/parse-hokensha-bangou "06130488")         ; => {:hokensha/houbetsu-bangou "06" ...}
(jp/validate-hokensha-bangou "0613048")       ; => {:insurance/valid? false :insurance/error :not-8-digits}
```

### 都道府県番号 (prefecture code)

The 2-digit 都道府県番号 (JIS X 0401, "01"-"47") shared by both
保険者番号 and 医療機関コード is embedded as a 47-entry constant table —
stable, public, decades-old data, unlike the tens-of-thousands-of-rows
診療行為/薬剤/傷病名 masters that this codebase deliberately does not
embed elsewhere (see the iryo actor's master-honesty ADR-2606074000).

```clojure
(jp/valid-todoufuken-bangou? "13") ; => true
(jp/todoufuken-name "13")          ; => "東京都"
```

### 医療機関コード (medical-institution code) — shape + 都道府県 only

**Partial support only, by design.** A 医療機関コード is 9 digits:
都道府県番号(2) + 点数表番号(1) + 郡市区番号(2) + 医療機関番号(3) +
検証番号(1), per the same 別添２ notification. This library parses that
shape and validates the 都道府県番号 sub-field, but does **not** compute
or verify the trailing 検証番号 (check digit), and does **not** decode
点数表番号 into a 医科/歯科/薬局 category — both are 要検証: there is no
worked example available in this codebase to test a check-digit
implementation against (unlike 保険者番号's, verified against 別添２
第1-5's own example), and the digit↔category mapping handed down between
sessions has been self-inconsistent (医科=1/歯科=2/薬局=3 vs. 医科=1/
歯科=3/薬局=4). Rather than guess at either, this library only exposes
what it can verify: shape and 都道府県番号 range.

```clojure
(jp/parse-iryokikan-bangou "131234567")
;; => {:iryokikan/todoufuken-bangou "13" :iryokikan/tensuhyou-bangou "1"
;;     :iryokikan/gunshiku-bangou "23" :iryokikan/kikan-bangou "456"
;;     :iryokikan/kenshou-bangou "7"}
(jp/valid-iryokikan-todoufuken-bangou? "131234567")   ; => true (13 = 東京都)
(jp/validate-iryokikan-bangou-shape "13123456")
;; => {:insurance/valid? false :insurance/error :not-9-digits}
```

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
