(ns rinha-concurrency-control.sandbox
  (:require [clojure.java.jdbc :as jdbc]
            [jdbc.pool.c3p0 :as pool]))

;; https://github.com/bostonaholic/clojure.jdbc-c3p0
(def db-spec
  (pool/make-datasource-spec
   {:classname "org.postgresql.Driver"
    :subprotocol "postgresql"
    :user "admin"
    :password "123"
    :subname "//localhost:5432/rinha?ApplicationName=RINHA"
    :initial-pool-size 10
    :min-pool-size 3
    :max-pool-size 12
    :max-connection-idle-lifetime 2000}))

(jdbc/with-db-transaction [conn db-spec]
  (let [cmds (jdbc/db-do-commands conn ["update saldos set valor = 0"
                                        "truncate table transacoes"])]
    cmds))


