(ns rinha-2024q1-crebito.payloads 
  (:require [schema.core :as s]))

;; - 2.147.483.647 é o valor máximo para INTEGER do Postgres
;; - Com créditos demais, pode haver erros de out of range.
;;   (Não validado propositalmente)
(s/defschema Transacao
  {:valor     (s/pred #(and (pos-int? %) (<= % 1000000)))
   :tipo      (s/enum "c" "d")
   :descricao (s/pred (fn [d]
                        (and (string? d)
                             (<= 1 (count d) 10))))})
