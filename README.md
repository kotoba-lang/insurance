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
| Tests | 48 assertions, all green |
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
per MHLW notification 別添２. No real 法別番号/都道府県番号 registry, no
医療機関コード, no diagnosis codes, no claims-adjustment logic — pure
shape/check-digit recomputation only.

```clojure
(require '[kotoba.insurance.jp :as jp])

(jp/valid-hokensha-bangou? "06130488")        ; => true  (MHLW worked example)
(jp/parse-hokensha-bangou "06130488")         ; => {:hokensha/houbetsu-bangou "06" ...}
(jp/validate-hokensha-bangou "0613048")       ; => {:insurance/valid? false :insurance/error :not-8-digits}
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
