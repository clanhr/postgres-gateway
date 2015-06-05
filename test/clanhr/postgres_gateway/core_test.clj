(ns clanhr.postgres-gateway.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! go]]
            [clanhr.postgres-gateway.core :as core]
            [clanhr.postgres-gateway.config :as config]
            [postgres.async :refer :all]
            [environ.core :refer [env]]
            [result.core :as result]))

(def ^:private ^:dynamic *db*)
(def table "postgres_gateway_tests")

(defn- wait [channel]
  (let [r (<!! channel)]
    (if (instance? Throwable r)
      (throw r))
    r))

(defn- create-tables [db]
  (wait (execute! db ["CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"]))
  (wait (execute! db [(str "drop table if exists " table)]))
  (wait (execute! db [(str "create table " table " (
                           id uuid primary key default uuid_generate_v4(),
                           model json, email varchar(200))")])))

(defn- db-fixture [f]
  (binding [*db* (config/create-connection)]
    (try
      (create-tables *db*)
      (f)
      (finally (close-db! *db*)))))

(use-fixtures :each db-fixture)

(defn- create-user
  "Creates a new user"
  []
  {:name "Bruce"
   :email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")})

(deftest inserting
  (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model {:name "Bruce" :email email}
        result (<!! (core/save-model! model {:table table
                                             :fields {:email email}}))]
    (is (result/succeeded? result))
    (is (= email (:email result)))
    (is (= (:name model) (:name result)))

    (testing "get-model"
      (let [result (<!! (core/get-model (:_id result) {:table table}))]
        (is (result/succeeded? result))
        (is (= (:name result) (:name model)))
        (is (= (:email result) (:email model)))))

    (testing "query-one"
      (let [result (<!! (core/query-one [(str "select model from " table " where email = $1 ") email]
                                        {:table table}))
            data-model result]
        (is (result/succeeded? result))
        (is (= (:name data-model) (:name model)))
        (is (= (:email data-model) (:email model)))))

    (testing "query"
      (let [result (<!! (core/query [(str "select model from " table " where email = $1 ") email]
                                    {:table table}))
            data (:data result)
            data-model (first data)]
        (is (result/succeeded? result))
        (is (= 1 (count data)))
        (is (= (:name data-model) (:name model)))
        (is (= (:email data-model) (:email model)))))))

(deftest inserting-with-exception
  (let [result (<!! (core/save-model! {} {:table "does-not-exist"}))]
    (is (result/failed? result))))

(deftest inserting-and-updating
  (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model {:name "Bruce" :email email}
        result1 (<!! (core/save-model! model {:table table
                                              :fields {:email email}}))
        result2 (<!! (core/save-model! result1 {:table table
                                                :fields {:email email}}))]
    (is (result/succeeded? result1))
    (is (result/succeeded? result2))
    (is (= (:_id result1) (:_id result2)))))

(deftest query-pagination
  (let [users (take 10 (repeatedly create-user))
        users-saved (mapv #(<!! (core/save-model! % {:table table
                                                     :fields {:email (:email %)}})) users)
        all (<!! (core/query [(str "select model from " table)] {:table table}))]
    (is (result/succeeded? all))
    (is (= (count users) (count (:data all))))

    (testing "pagination"
      (let [page (<!! (core/query [(str "select model from " table)] {:table table :page 1 :per-page 3}))]
        (is (result/succeeded? page))
        (is (= 3 (count (:data page))))))

    (testing "pagination with strs"
      (let [page (<!! (core/query [(str "select model from " table)] {:table table :page "1" :per-page "3"}))]
        (is (result/succeeded? page))
        (is (= 3 (count (:data page))))))))

#_(run-tests)
