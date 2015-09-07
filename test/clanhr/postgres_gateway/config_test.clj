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

(deftest simple-localhost-conn
  (let [conn-str "jdbc:postgresql://192.168.59.103:5432/postgres?user=postgres&password=wasabi"
        data (config/jdbc-str-to-map conn-str)]
    (is data)
    (is (= "192.168.59.103" (:hostname data)))
    (is (= 5432 (:port data)))
    (is (= "postgres" (:database data)))
    (is (= "postgres" (:user data)))
    (is (= "wasabi" (:password data)))))

(deftest ssl-mode
  (let [conn-str "jdbc:postgresql://192.168.59.103:5432/postgres?user=postgres&password=wasabi&sslmode=require"
        data (config/jdbc-str-to-map conn-str)]
    (is (= true (:ssl data)))))

#_(run-tests)
