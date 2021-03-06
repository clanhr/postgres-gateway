(ns clanhr.postgres-gateway.core-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! go]]
            [clanhr.postgres-gateway.core :as core]
            [clj-time.core :as t]
            [clanhr.postgres-gateway.config :as config]
            [clanhr.postgres-gateway.component :as pg-component]
            [postgres.async :refer :all]
            [environ.core :refer [env]]
            [result.core :as result]
            [com.stuartsierra.component :as component]))

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
                           model jsonb,
                           email varchar(200),
                           num decimal,
                           updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP)")])))

(defn- db-fixture [f]
  (binding [*db* (config/get-connection)]
    (create-tables *db*)
    (f)))

(use-fixtures :each db-fixture)

(defn- create-user
  "Creates a new user"
  []
  {:name "Bruce"
   :email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")})

(deftest inserting
  (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model {:name "Bruce" :email email :updated-at (t/now)}
        result (<!! (core/save-model! model {:table table
                                             :fields {:email email
                                                      :updated_at (:updated-at model)}}))]
    (is (result/succeeded? result))
    (is (= email (:email result)))
    (is (= (:name model) (:name result)))
    (is (= (:updated-at model) (:updated-at result)))

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

(deftest insert-with-id
  (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model-id (str (java.util.UUID/randomUUID))
        model {:_id model-id
               :name "Bruce"
               :email email
               :updated-at (t/now)}
        result (<!! (core/save-model-with-id! model {:table table
                                                     :fields {:email email
                                                              :updated_at (:updated-at model)}}))]
    (is (result/succeeded? result))
    (is (= model-id (:_id result)))
    (is (= email (:email result)))
    (is (= (:name model) (:name result)))
    (is (= (:updated-at model) (:updated-at result)))

    (testing "get-model"
      (let [result (<!! (core/get-model model-id {:table table}))]
        (is (result/succeeded? result))
        (is (= (:_id result) (:_id model)))
        (is (= (:name result) (:name model)))
        (is (= (:email result) (:email model)))))))

(deftest save-data-test
  (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model-id (java.util.UUID/randomUUID)
        model {:id model-id
               :email email
               :updated_at (t/now)}
        result (<!! (core/save-data! model {:table table}))]
    (is (result/succeeded? result))
    (is (= model-id (:id result)))
    (is (= email (:email result)))
    (is (= (:updated_at model) (:updated_at result)))

    (testing "get-model"
      (let [result (<!! (core/query-data [(str "select * from " table" where id = $1") model-id]
                                         {:table table}))
            data (first (:data result))]
        (is (result/succeeded? result))
        (is (= (:id data) (:id model)))
        (is (= (:email data) (:email model)))))))

(deftest bulk-inserting
  (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model1 {:name "Bruce" :email email}
        model2 {:name "Norris" :email email}
        models [model1 model2]
        results (<!! (core/bulk-save-models! models {:table table
                                                     :fields-fn (fn [model] {:email (:email model)})}))]

    (testing "query"
      (let [result (<!! (core/query [(str "select model from " table " where email = $1 ") email]
                                    {:table table}))
            data (:data result)
            data-model (first data)]
        (is (result/succeeded? result))
        (is (= 2 (count data)))))))

(deftest inserting-with-exception
  (let [result (<!! (core/save-model! {} {:table "does_not_exist"}))]
    (is (result/failed? result))))

(deftest updating-non-existent
  (let [result (<!! (core/save-model! {:_id (java.util.UUID/randomUUID)
                                       :name "Test"
                                       :email "failing@lanhr.com"}
                                      {:table table}))]
    (is (result/failed? result))))

(deftest updating-non-existent-forced
  (let [result (<!! (core/save-model! {:_id (java.util.UUID/randomUUID)
                                       :name "Test"
                                       :email "failing@lanhr.com"}
                                      {:table table
                                       :save-options {:insert-if-not-found true}}))]
    (is (result/succeeded? result))))

(deftest getting-non-existent
  (let [result (<!! (core/get-model (java.util.UUID/randomUUID)
                                    {:table table}))]
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

    (testing "count"
      (let [total (<!! (core/count-models [(str "select count(*) from " table)] {:table table}))]
        (is (result/succeeded? total))
        (is (= 10 (:data total)))))

    (testing "pagination with strs"
      (let [page (<!! (core/query [(str "select model from " table)] {:table table :page "1" :per-page "3"}))]
        (is (result/succeeded? page))
        (is (= 3 (count (:data page))))))))

(deftest delete-data
  (let [users (take 10 (repeatedly create-user))
        users-saved (mapv #(<!! (core/save-model! % {:table table
                                                     :service-name "delete-data"
                                                     :fields {:email (:email %)}})) users)]

    (testing "count"
      (let [total (<!! (core/count-models [(str "select count(*) from " table)] {:table table}))]
        (is (result/succeeded? total))
        (is (= 10 (:data total)))))

    (testing "delete all"
      (let [result (<!! (core/delete-models [(str "delete from " table)] {:table table}))]
        (is (result/succeeded? result))))

    (testing "count again"
      (let [total (<!! (core/count-models [(str "select count(*) from " table)] {:table table}))]
        (is (result/succeeded? total))
        (is (= 0 (:data total)))))))

(deftest update-data
  (let [users (take 10 (repeatedly create-user))
        users-saved (mapv #(<!! (core/save-model! % {:table table
                                                     :service-name "update-data"
                                                     :fields {:email (:email %)}})) users)]

    (testing "count"
      (let [total (<!! (core/count-models [(str "select count(*) from " table)] {:table table}))]
        (is (result/succeeded? total))
        (is (= 10 (:data total)))))

    (testing "update all"
      (let [result (<!! (core/update-models [(str "update " table " SET email=$1 where model->>'name'=$2") "noemail@mail.com" "Bruce"] {:table table}))]
        (is (result/succeeded? result)))

      (let [total (<!! (core/count-models [(str "select count(*) from " table " where email=$1") "noemail@mail.com"] {:table table}))]
        (is (result/succeeded? total))
        (is (= 10 (:data total)))))))


(deftest query-in-param
  (let [users [{:name "Bruce" :email "a@a.pt"}
               {:name "Norris" :email "b@b.pt"}]
        users-saved (mapv #(<!! (core/save-model! % {:table table
                                                     :fields {:email (:email %)}})) users)
        all (<!! (core/query [(str "select model from " table
                                   " where email in ($1)") ["a@a.pt" "b@b.pt"]]
                             {:table table :in-param 1}))]
    (is (result/succeeded? all))
    (is (= (count users) (count (:data all))))))

(deftest force-inserting
  (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model {:name "Bruce" :email email :_id (java.util.UUID/randomUUID)}
        result (<!! (core/save-model! model {:table table
                                             :insert true
                                             :fields {:email email}}))]
    (result/succeeded? result)))

(deftest use-transaction-context
  (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model-id (str (java.util.UUID/randomUUID))
        model {:_id model-id
               :name "Bruce"
               :email email
               :updated-at (t/now)}
        pg-conn (-> (pg-component/create) (component/start))
        context {:pg-conn pg-conn}
        result (<!! (config/transaction-run! context
                  (fn [context]
                    (core/save-model-with-id! model
                      (merge context
                             {:table table
                              :fields {:email email
                              :updated_at (:updated-at model)}})))))]

    (is (result/succeeded? result))
    (is (= model-id (:_id result)))))

(deftest query-decimals
  (testing "saving data"
    (let [email (str (str (java.util.UUID/randomUUID)) "@rupeal.com")
        model-id (java.util.UUID/randomUUID)
        model {:id model-id
               :email email
               :num 1.5
               :updated_at (t/now)}
        result (<!! (core/save-data! model {:table table}))]
    (is (result/succeeded? result))
    (testing "get-model"
      (let [result (<!! (core/query-data [(str "select num from " table" where id = $1") model-id]
                                         {:table table}))
            data (first (:data result))]
        (is (result/succeeded? result))
        (is (= 1.5 (:num data))))))))

#_(run-tests)
