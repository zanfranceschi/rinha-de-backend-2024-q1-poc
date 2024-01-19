(defproject rinha-2024q1-crebito "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.1"]
                 [org.postgresql/postgresql "42.6.0"]
                 [clojure.jdbc/clojure.jdbc-c3p0 "0.3.4"]
                 [com.github.seancorfield/next.jdbc "1.3.909"]
                 [prismatic/schema "1.4.1"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler rinha-2024q1-crebito.endpoints/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}
   :uberjar {:aot :all}})
