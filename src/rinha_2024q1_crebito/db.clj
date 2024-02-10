(ns rinha-2024q1-crebito.db
  (:require [jdbc.pool.c3p0 :as pool]))

(def db-hostname (or (System/getenv "DB_HOSTNAME") "localhost"))
(def db-initial-pool-size (Integer/parseInt (or (System/getenv "DB_INITIAL_POOL_SIZE") "3")))
(def db-max-pool-size (Integer/parseInt (or (System/getenv "DB_MAX_POOL_SIZE") "15")))

(def spec
  (pool/make-datasource-spec
   {:classname "org.postgresql.Driver"
    :subprotocol "postgresql"
    :user "admin"
    :password "123"
    :subname (str "//" db-hostname ":5432/rinha?ApplicationName=rinha-web-server")
    :initial-pool-size db-initial-pool-size
    :max-pool-size db-max-pool-size}))
