(ns clanhr.postgres-gateway.component-test
  (:require
    [clojure.test :refer :all]
    [clojure.core.async :refer [<!! go]]
    [clanhr.postgres-gateway.component :as pg-component]
    [clanhr.postgres-gateway.core :as core]
    [clanhr.postgres-gateway.config :as config]
    [result.core :as result]
    [com.stuartsierra.component :as component]))

(deftest start-stop-component
  (-> (pg-component/create)
      (component/start)
      (component/stop)))

(deftest make-query
  (let [conn (-> (pg-component/create) (component/start))
        result (<!! (core/query ["select 1"] conn))]
    (is (not= conn @config/db-pool) "Should not use global conn")
    (is (result/succeeded? result))
    (component/stop conn)))
