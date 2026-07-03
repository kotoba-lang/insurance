(ns kotoba.insurance.export
  "Operator-facing export for an insurance actor.

  Renders policies, premium quotes and claims to CSV and JSON for audit and
  downstream reporting. Pure data -> text: no network."
  (:require [clojure.string :as str]))

(defn- csv-cell [v]
  (let [s (str (if (nil? v) "" v))]
    (if (re-find #"[\",\n]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [vals] (str/join "," (map csv-cell vals)))

(defn- json-str [v]
  (-> (str (if (nil? v) "" v))
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")))

(defn policies->csv [policies]
  (str/join "\n"
    (cons (csv-row ["policy_id" "holder" "type" "coverage" "currency" "start" "end"])
          (for [p policies]
            (csv-row [(:policy/id p) (or (:policy/holder p) "")
                      (name (or (:policy/type p) :unspecified))
                      (:policy/coverage p) (:policy/currency p)
                      (:policy/start p) (:policy/end p)])))))

(defn claims->csv [claims]
  (str/join "\n"
    (cons (csv-row ["claim_id" "policy" "claimant" "incident_date" "amount_requested" "status"])
          (for [c claims]
            (csv-row [(:claim/id c) (:claim/policy c) (or (:claim/claimant c) "")
                      (:claim/incident-date c) (:claim/amount-requested c)
                      (name (or (:claim/status c) :intake))])))))

(defn policies->json [policies]
  (str "["
       (str/join ","
                 (for [p policies]
                   (str "{\"policy_id\":\"" (json-str (:policy/id p)) "\","
                        "\"holder\":\"" (json-str (:policy/holder p)) "\","
                        "\"type\":\"" (name (or (:policy/type p) :unspecified)) "\","
                        "\"coverage\":" (or (:policy/coverage p) 0) "}")))
       "]"))

(defn claims->json [claims]
  (str "["
       (str/join ","
                 (for [c claims]
                   (str "{\"claim_id\":\"" (json-str (:claim/id c)) "\","
                        "\"policy\":\"" (json-str (:claim/policy c)) "\","
                        "\"amount_requested\":" (or (:claim/amount-requested c) 0) ","
                        "\"status\":\"" (name (or (:claim/status c) :intake)) "\"}")))
       "]"))
