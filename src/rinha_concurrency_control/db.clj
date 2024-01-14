(ns rinha-concurrency-control.db 
  (:require [jdbc.pool.c3p0 :as pool]))

(def db-hostname (or (System/getenv "DB_HOSTNAME") "localhost"))

(def spec
  (pool/make-datasource-spec
   {:classname "org.postgresql.Driver"
    :subprotocol "postgresql"
    :user "admin"
    :password "123"
    :subname (str "//" db-hostname ":5432/rinha?ApplicationName=rinha-web-server")
    :initial-pool-size 10
    :max-pool-size 20}))
