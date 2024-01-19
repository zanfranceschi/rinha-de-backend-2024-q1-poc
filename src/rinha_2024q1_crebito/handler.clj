(ns rinha-2024q1-crebito.handler
  (:require [clojure.string :as string]
            [compojure.core :refer :all]
            [next.jdbc.result-set :as rs]
            [compojure.route :as route]
            [next.jdbc :as jdbc]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.json :as json]
            [rinha-2024q1-crebito.db :as db]
            [schema.core :as s]))


(def tipos-transacao #{"c" "d"})

;; https://camdez.com/blog/2015/08/27/practical-data-coercion-with-prismatic-schema/
(s/defschema Transacao
  {:valor     s/Int
   :tipo      (apply s/enum tipos-transacao)
   :descricao (s/pred (fn [d]
                        (and (string? d)
                             (<= (count d) 10))))})
(def clientes
  (memoize (fn []
             (reduce (fn [acc {:clientes/keys [id nome limite]}]
                       (assoc acc id {:id id :nome nome :limite limite})) {}
                     (jdbc/execute! db/spec ["select * from clientes"])))))

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
  (try
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
                :saldo novo-saldo}}))
    (catch Exception e
      (if (string/includes? (.getMessage e) "violates foreign key constraint \"fk_clientes_transacoes_id\"")
        {:status 404}
        (throw e)))))

(defn debitar!
  [cliente_id clientes valor descricao db-spec]
  (try
    (jdbc/with-transaction [conn db-spec]
      (jdbc/execute-one! conn ["select pg_advisory_xact_lock(?)" cliente_id])
      (let [{limite :limite} (get clientes cliente_id)
            {saldo :saldos/saldo :as x} (jdbc/execute-one! conn ["select valor as saldo
                                                                  from saldos
                                                                  where cliente_id = ?" cliente_id])
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
                    :saldo novo-saldo}}))))
    (catch Exception e
      (if (string/includes? (.getMessage e) "violates foreign key constraint \"fk_clientes_transacoes_id\"")
        {:status 404}
        (throw e)))))

(defn transacionar!
  [{db-spec :db-spec payload :body {cliente_id* :id} :route-params clientes :cached-clientes}]
  (try
    (let [cliente_id (Integer/parseInt cliente_id*)
          {valor     :valor
           tipo      :tipo
           descricao :descricao} (s/validate Transacao payload)]
      (cond
        (or (< valor 1)
            (nil? (some #{tipo} #{"c" "d"}))) {:status 422
                                               :body   {:erro "Valor precisa ser maior que 0 e tipo 'c' ou 'd'."}}
        (= tipo "c") (creditar! cliente_id clientes valor descricao db-spec)
        (= tipo "d") (debitar! cliente_id clientes valor descricao db-spec)))
    (catch Exception e
      (if (= :schema.core/error (:type (ex-data e)))
        {:status 422
         :body   {:erro "manda essa merda direito com valor, tipo e descricao"}}
        (throw e)))))

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

(defroutes app-routes
  (GET "/" _ "ok")
  (GET ["/clientes/:id/extrato" :id #"[0-9]+"] _ extrato!)
  (POST ["/clientes/:id/transacoes" :id #"[0-9]+"] _ transacionar!)
  (GET ["/clientes"] _ clientes!)
  (POST "/admin/db-reset" _ admin-reset-db!)
  (route/not-found "Not Found"))

(defn wrap-db
  [handler]
  (fn [request]
    (handler (assoc request :db-spec db/spec))))

(defn wrap-clientes
  [handler]
  (fn [request]
    (handler (assoc request :cached-clientes (clientes)))))

(def app
  (wrap-defaults
   (-> app-routes
       wrap-db
       wrap-clientes
       (json/wrap-json-body {:keywords? true})
       json/wrap-json-response)
   (assoc-in site-defaults [:security :anti-forgery] false)))
