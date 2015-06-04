(ns clanhr.postgres-gateway.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! go]]
            [clanhr.postgres-gateway.core :as core]
            [postgres.async :refer :all]
            [environ.core :refer [env]]
            [result.core :as result]))

(def ^:private ^:dynamic *db*)
(def table "postgres_gateway_tests")
(def db-config {:hostname (env :pg-host "localhost")
                :port     (env :pg-port 5432)
                :database (env :pg-database "postgres")
                :username (env :pg-user "postgres")
                :password (env :pg-password "postgres")
                :pool-size 1})

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
  (binding [*db* (open-db db-config)]
    (try
      (create-tables *db*)
      (f)
      (finally (close-db! *db*)))))

(use-fixtures :each db-fixture)

(deftest inserting
  (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model {:name "Bruce" :email email}
        result (<!! (core/save-model! model {:db-config db-config
                                             :table table
                                             :fields {:email email}}))]
    (is (result/succeeded? result))))

(run-tests)

