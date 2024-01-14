(ns rinha-concurrency-control.interceptors
  (:require [clojure.data.json :as json]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.log :as log]
            [rinha-concurrency-control.db :as db]))

(def db-interceptor
  (interceptor/interceptor
   {:name ::db
    :enter (fn [context]
             (assoc-in context [:request :db-spec] db/spec))}))

(def json-interceptor
  (interceptor/interceptor
   {:name ::json-response
    :leave (fn [context]
             (if (or (map? (-> context :response :body)) (seq? (-> context :response :body)))
               (let [response (:response context)
                     new-response (assoc response
                                         :headers {"Content-Type" "application/json"}
                                         :body (json/write-str (:body response)))]
                 (assoc context :response new-response))
               context))}))

(def exception-interceptor
  (interceptor/interceptor
   {:name ::exception-handling
    :error (fn [context error]
             (let [exception-data (ex-data error)
                   exception-type (:exception-type exception-data)]
               (log/error :error error
                          :context context)
               (if (= exception-type :com.fasterxml.jackson.core.JsonParseException)
                 (assoc context :response
                        {:status 400
                         :headers {"Content-Type" "application/json"}
                         :body (json/write-str {:erro "formato json cagado"})})
                 (assoc context :response
                        {:status 500
                         :body {:msg "smt went wrong"
                                :error (.getMessage error)}}))))}))
