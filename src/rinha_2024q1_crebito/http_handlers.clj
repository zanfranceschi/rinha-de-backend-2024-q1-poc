(ns rinha-2024q1-crebito.http-handlers
  (:require [next.jdbc :as jdbc]
            [rinha-2024q1-crebito.payloads :as payloads]
            [schema.core :as s]
            [rinha-2024q1-crebito.http-handlers :as handlers]))

(defn ^:private creditar!
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

(defn ^:private debitar!
  [cliente_id clientes valor descricao db-spec]
  (jdbc/with-transaction [conn db-spec]
    (jdbc/execute-one! conn ["select pg_advisory_xact_lock(?)" cliente_id])
    (let [{limite :limite} (get clientes cliente_id)
          {saldo :saldos/saldo} (jdbc/execute-one! conn ["select valor as saldo
                                                          from saldos
                                                          where cliente_id = ?"
                                                         cliente_id])
          tem-limite? (>= (- saldo valor) (* limite -1))]
      (if tem-limite?
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
                  :saldo novo-saldo}})
        {:status 422
         :body {:erro "limite insuficiente"}}))))

(defn ^:private extrato!*
  [{db-spec :db-spec
    cliente_id :cliente-id
    clientes :cached-clientes}]
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
              :ultimas_transacoes transacoes}}))

(defn ^:private transacionar!*
  [{db-spec :db-spec
    payload :body
    cliente_id :cliente-id
    clientes :cached-clientes}]
  (if-not (s/check payloads/Transacao payload)
    (let [{valor     :valor
           tipo      :tipo
           descricao :descricao} payload
          tx-fn {"d" debitar!
                 "c" creditar!}]
      ((tx-fn tipo) cliente_id clientes valor descricao db-spec))
    {:status 422
     :body   {:erro "manda essa merda direito com 'valor', 'tipo' e 'descricao'"}}))

(defn find-cliente-handler-wrapper
  [handler]
  (fn [{{cliente_id* :id} :route-params
        clientes :cached-clientes :as request}]
    (if-let [{cliente_id :id} (get clientes (Integer/parseInt cliente_id*))]
      (handler (assoc request :cliente-id cliente_id))
      {:status 404})))

(def transacionar! (find-cliente-handler-wrapper transacionar!*))

(def extrato! (find-cliente-handler-wrapper extrato!*))

(defn admin-reset-db!
  [{:keys [db-spec]}]
  (jdbc/execute-one! db-spec
                     ["update saldos set valor = 0;
                       truncate table transacoes;"])
  {:status 200
   :body {:msg "db reset!"}})
