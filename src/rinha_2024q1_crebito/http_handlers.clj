(ns rinha-2024q1-crebito.http-handlers
  (:require [clojure.string :as string]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [schema.core :as s]))

(def tipos-transacao #{"c" "d"})

;; - 2.147.483.647 é o valor máximo para INTEGER do Postgres
;; - Com créditos demais, pode haver erros de out of range.
;;   (Não validado propositalmente)
(s/defschema Transacao
  {:valor     (s/pred #(and (pos-int? %) (<= % 1000000))) 
   :tipo      (apply s/enum tipos-transacao)
   :descricao (s/pred (fn [d]
                        (and (string? d)
                             (and (<= (count d) 10)
                                  (>= (count d) 1)))))})
(defn extrato!
  [{db-spec :db-spec
    {cliente_id* :id} :route-params
    clientes :cached-clientes}]
  (if-let [{cliente_id :id} (get clientes (Integer/parseInt cliente_id*))]
    (try
      (let [resultado (jdbc/execute!
                       db-spec
                       ["(select valor, 'saldo' as tipo, 'saldo' as descricao, now() as realizada_em
                        from saldos
                        where cliente_id = ?)
                       union all
                       (select valor, tipo, descricao, realizada_em
                        from transacoes
                        where cliente_id = ?
                        order by id desc limit 10)"
                        cliente_id cliente_id])
            saldo-row (first resultado)
            saldo {:total        (:valor saldo-row)
                   :data_extrato (:realizada_em saldo-row)
                   :limite       (:limite (get clientes cliente_id))}
            transacoes (rest resultado)]
        {:status 200
         :body   {:saldo              saldo
                  :ultimas_transacoes transacoes}})
      (catch Exception e
        (if (string/includes? (.getMessage e) "violates foreign key constraint \"fk_clientes_transacoes_id\"")
          {:status 404}
          (throw e))))
    {:status 404}))

(defn creditar!
  [cliente_id clientes valor descricao db-spec]
  (jdbc/with-transaction [conn db-spec]
    (jdbc/execute! conn ["select pg_advisory_xact_lock(?)" cliente_id])
    (jdbc/execute! conn ["insert into transacoes
                          (id, cliente_id, valor, tipo, descricao, realizada_em) 
                          values(default, ?, ?, 'c', ?, now())"
                         cliente_id
                         valor
                         descricao])
    (let [{novo-saldo :saldos/saldo} (jdbc/execute-one! conn ["update saldos
                                                               set valor = valor + ?
                                                               where cliente_id = ?
                                                               returning valor as saldo"
                                                              valor
                                                              cliente_id])
          {limite :limite} (get clientes cliente_id)]
      {:status 200
       :body {:limite limite
              :saldo novo-saldo}})))

(defn debitar!
  [cliente_id clientes valor descricao db-spec]
  (jdbc/with-transaction [conn db-spec]
    (jdbc/execute-one! conn ["select pg_advisory_xact_lock(?)" cliente_id])
    (let [{limite :limite} (get clientes cliente_id)
          {saldo :saldos/saldo} (jdbc/execute-one! conn ["select valor as saldo
                                                          from saldos
                                                          where cliente_id = ?"
                                                         cliente_id])
          ultrapassaria-limite? (< (- saldo valor) (* limite -1))]
      (if ultrapassaria-limite?
        {:status 422
         :body {:erro "limite insuficiente"}}
        (let [{novo-saldo :saldos/saldo} (jdbc/execute-one! conn ["update saldos
                                                                   set valor = valor + ?
                                                                   where cliente_id = ?
                                                                   returning valor as saldo"
                                                                  (* valor -1)
                                                                  cliente_id])]
          (jdbc/execute! conn ["insert into transacoes
                                (id, cliente_id, valor, tipo, descricao, realizada_em)
                                values(default, ?, ?, 'd', ?, now())"
                               cliente_id
                               valor
                               descricao])
          {:status 200
           :body {:limite limite
                  :saldo novo-saldo}})))))

(defn transacionar!
  [{db-spec :db-spec payload :body {cliente_id* :id} :route-params clientes :cached-clientes}]
  (if-let [{cliente_id :id} (get clientes (Integer/parseInt cliente_id*))]
    (try
      (let [{valor     :valor
             tipo      :tipo
             descricao :descricao} (s/validate Transacao payload)]
        (cond
          (= tipo "c") (creditar! cliente_id clientes valor descricao db-spec)
          (= tipo "d") (debitar! cliente_id clientes valor descricao db-spec)))
      (catch Exception e
        (if (= :schema.core/error (:type (ex-data e)))
          {:status 422
           :body   {:erro "manda essa merda direito com 'valor', 'tipo' e 'descricao'"}}
          (throw e))))
    {:status 404}))

(defn clientes!
  [{db-spec :db-spec}]
  (let [clientes (jdbc/execute! db-spec
                                ["select * from clientes"]
                                {:builder-fn rs/as-unqualified-maps})]
    {:status 200
     :body clientes}))

(defn admin-reset-db!
  [{:keys [db-spec]}]
  (jdbc/execute-one! db-spec
                     ["update saldos set valor = 0; truncate table transacoes"])
  {:status 200
   :body {:msg "db reset!"}})
