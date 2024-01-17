(ns rinha-2024q1-crebito.service
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [rinha-2024q1-crebito.db :as db]
            [rinha-2024q1-crebito.interceptors :as interceptors]
            [schema.core :as s]))

(defn home-page
  [_request]
  (ring-resp/response "ok"))

;; https://camdez.com/blog/2015/08/27/practical-data-coercion-with-prismatic-schema/

(def tipos-transacao #{"c" "d"})

(s/defschema Transacao
  {:valor     s/Int
   :tipo      (apply s/enum tipos-transacao)
   :descricao (s/pred (fn [d]
                        (and (string? d)
                             (<= (count d) 50))))})

(def clientes (memoize
               (fn []
                 (reduce (fn [acc {:keys [id nome limite]}]
                           (assoc acc id {:id id :nome nome :limite limite})) {}
                         (jdbc/reducible-query db/spec ["select * from clientes"])))))

(defn try-parse-to-int [s]
  (try (Integer/parseInt s)
       (catch Exception _ nil)))

(defn extrato!
  [{db-spec :db-spec {cliente_id* :cliente_id} :path-params}]
  (if-let [{cliente_id :id} (get (clientes) (try-parse-to-int cliente_id*))]
    (try
      (let [resultado (jdbc/query db-spec
                                  ["(select valor, 'saldo' as tipo, 'saldo' as descricao, now() as realizada_em from saldos where cliente_id = ?)
                                       union all
                                      (select valor, tipo, descricao, realizada_em from transacoes where cliente_id = ? order by id desc limit 10)" cliente_id cliente_id])
            saldo-row (first resultado)
            saldo {:total        (:valor saldo-row)
                   :data_extrato (:realizada_em saldo-row)
                   :limite       (:limite (get (clientes) cliente_id))}
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
  [cliente_id valor descricao db-spec]
  (try
    (jdbc/with-db-transaction [conn db-spec]
      (jdbc/query conn ["select pg_advisory_xact_lock(?)" cliente_id])
      (let [{limite :limite} (get (clientes) cliente_id)
            {novo-saldo :saldo} (jdbc/query conn ["update saldos set valor = valor + ? where cliente_id = ? returning valor as saldo" valor cliente_id])]
        (jdbc/insert! conn :transacoes {:cliente_id cliente_id
                                        :valor      valor
                                        :tipo       "crédito"
                                        :descricao  descricao})
        {:status 200
         :body {:limite limite
                :saldo novo-saldo}}))
    (catch Exception e
      (if (string/includes? (.getMessage e) "violates foreign key constraint \"fk_clientes_transacoes_id\"")
        {:status 404}
        (throw e)))))

(defn debitar!
  [cliente_id valor descricao db-spec]
  (try
    (jdbc/with-db-transaction [conn db-spec]
      (jdbc/query conn ["select pg_advisory_xact_lock(?)" cliente_id])
      (let [{limite :limite} (get (clientes) cliente_id)
            {saldo :saldo} (first (jdbc/query conn ["select valor as saldo from saldos where cliente_id = ?" cliente_id]))
            ultrapassaria-limite? (< (- saldo valor) (* limite -1))]
        (if ultrapassaria-limite?
          {:status 422
           :body {:erro "limite insuficiente"}}
          (let [{novo-saldo :saldo} (jdbc/query conn ["update saldos set valor = valor + ? where cliente_id = ? returning valor as saldo" (* valor -1) cliente_id])]
            (jdbc/insert! conn :transacoes {:cliente_id cliente_id
                                            :valor      valor
                                            :tipo       "débito"
                                            :descricao  descricao})
            {:status 200
             :body {:limite limite
                    :saldo novo-saldo}}))))
    (catch Exception e
      (if (string/includes? (.getMessage e) "violates foreign key constraint \"fk_clientes_transacoes_id\"")
        {:status 404}
        (throw e)))))

(defn transacionar!
  [{db-spec :db-spec payload :json-params {cliente_id* :cliente_id} :path-params}]
  (try
    (let [cliente_id (Integer/parseInt cliente_id*)
          {valor     :valor
           tipo      :tipo
           descricao :descricao} (s/validate Transacao payload)]
      (cond
        (or (< valor 1)
            (nil? (some #{tipo} #{"c" "d"}))) {:status 422
                                               :body   {:erro "Valor precisa ser maior que 0 e tipo 'c' ou 'd'."}}
        (= tipo "c") (creditar! cliente_id valor descricao db-spec)
        (= tipo "d") (debitar! cliente_id valor descricao db-spec)))
    (catch Exception e
      (if (= :schema.core/error (:type (ex-data e)))
        {:status 422
         :body   {:erro "manda essa merda direito com valor, tipo e descricao"}}
        (throw e)))))

(defn admin-reset-db
  [{db-spec :db-spec}]
  (jdbc/db-do-commands db-spec ["update saldos set valor = 0"
                                "truncate table transacoes"])
  {:status 200
   :body {:msg "db reset!"}})

(defn teste [_]
  {:status 422
   :body {:msg "ok"
          :limite 10
          :saldo -10}})

(def common-interceptors
  [interceptors/exception-interceptor
   (body-params/body-params)
   interceptors/db-interceptor
   interceptors/json-interceptor])

(def routes #{["/" :get (conj common-interceptors `home-page)]
              ["/clientes/:cliente_id/transacoes" :post (conj common-interceptors `transacionar!)]
              ["/clientes/:cliente_id/extrato" :get (conj common-interceptors `extrato!)]
              ["/admin/db-reset" :post (conj common-interceptors `admin-reset-db)]
              ["/teste" :get (conj common-interceptors `teste)]})

(def http-port (Integer/parseInt (or (System/getenv "HTTP_PORT") "8080")))

(def service {:env :prod
              ::http/routes routes
              ::http/resource-path "/public"
              ::http/type :jetty
              ::http/host "0.0.0.0"
              ::http/port http-port
              ::http/container-options {:h2c? true
                                        :h2? false
                                        :ssl? false}})
