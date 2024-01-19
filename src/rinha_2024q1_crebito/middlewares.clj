(ns rinha-2024q1-crebito.middlewares 
  (:require [next.jdbc :as jdbc]
            [rinha-2024q1-crebito.db :as db]))

(def clientes
  (memoize (fn []
             (reduce (fn [acc {:clientes/keys [id nome limite]}]
                       (assoc acc id {:id id :nome nome :limite limite})) {}
                     (jdbc/execute! db/spec ["select * from clientes"])))))

(defn wrap-db
  [handler]
  (fn [request]
    (handler (assoc request :db-spec db/spec))))

(defn wrap-clientes
  [handler]
  (fn [request]
    (handler (assoc request :cached-clientes (clientes)))))
