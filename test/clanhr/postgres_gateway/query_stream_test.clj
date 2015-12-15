(ns clanhr.postgres-gateway.query-stream-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async :refer [<!! go]]
            [clanhr.postgres-gateway.core :as core]
            [clj-time.core :as t]
            [clanhr.postgres-gateway.config :as config]
            [clanhr.postgres-gateway.query-stream :as query-stream]
            [clanhr.postgres-gateway.component :as pg-component]
            [postgres.async :refer :all]
            [environ.core :refer [env]]
            [result.core :as result]
            [com.stuartsierra.component :as component]))

(def ^:private ^:dynamic *db*)
(def table "postgres_gateway_stream_tests")

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
                           model jsonb,
                           email varchar(200),
                           updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP)")])))

(defn- db-fixture [f]
  (binding [*db* (config/get-connection)]
    (create-tables *db*)
    (f)))

(use-fixtures :each db-fixture)

(deftest empty-stream
  (let [ch (query-stream/run (str "select * from " table " where email = 'waza'"))
        data (wait ch)]
    (is (nil? data))))

(deftest get-1-result-stream
  (core/save-data! {:email "suricata@clanhr.com"} {:table table})
  (let [ch (query-stream/run (str "select * from " table))
        batch (wait ch)]
    (is (not (nil? batch)))))

(defn- take-all
  "Gets all items from a channel"
  ([ch]
   (take-all ch []))
  ([ch coll]
   (if-let [v (<!! ch)]
     (take-all ch (conj coll v))
     coll)))

(deftest get-N-result-stream
  (testing "create a lot"
    (dotimes [n 100]
      (<!! (core/save-data! {:email (str "suricata" n "@clanhr.com")} {:table table})))
    (let [ch (query-stream/run (str "select * from " table))
          batch (take-all ch)]
      (is (not (nil? batch)))
      (is (= 100 (count batch))))))
