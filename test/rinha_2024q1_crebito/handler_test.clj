(ns rinha-2024q1-crebito.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [rinha-2024q1-crebito.handler :refer :all]))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "Hello World"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
