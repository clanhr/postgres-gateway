(ns clanhr.postgres-gateway.config-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! go]]
            [clanhr.postgres-gateway.core :as core]
            [clanhr.postgres-gateway.config :as config]
            [com.stuartsierra.component :as component]
            [clanhr.postgres-gateway.component :as pg-component]
            [postgres.async :refer :all]
            [environ.core :refer [env]]
            [result.core :as result]))

(deftest complete-format?-test
  (testing "should be complete format"
    (-> (str "jdbc:postgresql://localhost:5432/database?user=user&password=password&sslmode=require")
        config/complete-format?
        is)
    )

  (testing "shouldn't be complete format"
    (-> (str "postgresql://127.0.0.1:63939/test")
        config/complete-format?
        nil?
        is)
    ))

(deftest complete-format-to-map-test
  (let [hostname "localhost"
        port 5432
        database "databse"
        user "user"
        password "password"
        conn-str (str "jdbc:postgresql://"hostname":"port"/"database"?user="user"&password="password"&sslmode=require")
        data (config/complete-format-to-map conn-str)]
    (is data)
    (is (= hostname (:hostname data)))
    (is (= port (:port data)))
    (is (= database (:database data)))
    (is (= user (:user data)))
    (is (= password (:password data)))))

(deftest ephemeralpg-format?-test
  (testing "should be ephemeralpg format"
    (-> (str "postgresql://127.0.0.1:63939/test")
        config/ephemeralpg-format?
        is)
    )

  (testing "shouldn't be ephemeralpg format"
    (-> (str "jdbc:postgresql://localhost:5432/database?user=user&password=password&sslmode=require")
        config/ephemeralpg-format?
        nil?
        is)
    ))

(deftest ephemeralpg-format-to-map-test
  (let [hostname "127.0.0.1"
        port 5432
        database "database"
        conn-str (str "postgresql://"hostname":"port"/"database)
        data (config/ephemeralpg-format-to-map conn-str)]
    (is data)
    (is (= hostname (:hostname data)))
    (is (= port (:port data)))
    (is (= database (:database data)))
    (is (= "postgres" (:username data)))))

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

(deftest transaction-result-failed-test
  (let [pg-conn (component/start (pg-component/create))
        context {:pg-conn pg-conn}]

    (testing "forcing a rollback"
      (is (result/failed?
            (<!! (config/transaction-run! context
                                          (fn [context]
                                            (go (result/failure))))))))

    (testing "check if the connection is still ok"
      (is (result/succeeded?
            (<!! (config/transaction-run! context
                                          (fn [context]
                                            (core/query-data ["select 1;"] context)))))))

    (component/stop pg-conn)))

#_(run-tests)
