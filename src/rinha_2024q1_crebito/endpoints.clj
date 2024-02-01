(ns rinha-2024q1-crebito.endpoints
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.json :as json]
            [rinha-2024q1-crebito.http-handlers :as handlers]
            [rinha-2024q1-crebito.middlewares :as middlewares]))

(defroutes app-routes
  (GET ["/"] _ "ok")
  (GET ["/clientes/:id/extrato" :id #"[0-9]+"] _ handlers/extrato!)
  (POST ["/clientes/:id/transacoes" :id #"[0-9]+"] _ handlers/transacionar!)
  (POST ["/admin/db-reset"] _ handlers/admin-reset-db!)
  (route/not-found "oops hehe"))

(def app
  (wrap-defaults
   (-> app-routes
       middlewares/wrap-db
       middlewares/wrap-clientes
       (json/wrap-json-body {:keywords? true})
       json/wrap-json-response)
   (assoc-in site-defaults [:security :anti-forgery] false)))
