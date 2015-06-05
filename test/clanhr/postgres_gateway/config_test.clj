(ns clanhr.postgres-gateway.config-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! go]]
            [clanhr.postgres-gateway.core :as core]
            [clanhr.postgres-gateway.config :as config]
            [postgres.async :refer :all]
            [environ.core :refer [env]]
            [result.core :as result]))

(deftest jdbc-string-to-map
  (let [hostname "localhost"
        port 5432
        database "databse"
        user "user"
        password "password"
        conn-str (str "jdbc:postgresql://"hostname":"port"/"database"?user="user"&password="password"&sslmode=require")
        data (config/jdbc-str-to-map conn-str)]
    (is data)
    (is (= hostname (:hostname data)))
    (is (= port (:port data)))
    (is (= database (:database data)))
    (is (= user (:user data)))
    (is (= password (:password data)))))

(run-tests)
