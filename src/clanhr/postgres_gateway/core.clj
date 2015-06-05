(ns clanhr.postgres-gateway.core
  "Async access utilities to postgres"
  (require [clanhr.postgres-gateway.custom-types]
           [clanhr.postgres-gateway.config :as config]
           [postgres.async :refer :all]
           [clojure.core.async :as async]
           [cheshire.core :as json]
           [result.core :as result]))

(def ^:private default-config {:timeout 1000})

(defn- idify
  "Adds an id to the model, if none is given"
  [model]
  (if (:_id model)
    model
    (assoc model :_id (java.util.UUID/randomUUID))))

(defn- build-fields
  "Builds fields to be persisted on the datastore"
  [model config]
  (let [fields (or (:fields config) {})]
    (-> fields
        (assoc :id (:_id model))
        (assoc :model model))))

(defn- upsert!
  "Updates or inserts a model"
  [new? model config]
  (let [db (config/get-connection config)
        uuid (:_id model)
        fields (build-fields model config)
        sql-spec {:table (:table config) :returning "id"}]
      (if new?
        (update! db (assoc sql-spec :where ["id = $1" uuid]) fields)
        (insert! db sql-spec fields))))

(defn save-model!
  "Saves a model to the datastore"
  [model config]
  (async/go
    (let [to-create? (:_id model)
          model-with-id (idify model)
          response (async/<! (upsert! to-create? model-with-id config))]
      (if (instance? Throwable response)
        (result/exception response)
        (result/success model-with-id)))))

(defn- convert-int
  "Converts the value to int, if needed"
  [raw]
  (if (string? raw)
    (Integer/parseInt raw)
    raw))

(defn- build-query
  "Builds/edits the query with extra information"
  [query config]
  (let [sql (first query)
        page (convert-int (:page config))
        per-page (convert-int (:per-page config))]
    (if page
      (concat [(str sql
                    " OFFSET " (* (- page 1) per-page)
                    " LIMIT " (or per-page 10))]
              (rest query))
      query)))

(defn query
  "Runs a query on the database"
  [raw-query config]
  (async/go
    (let [db (config/get-connection config)
          response (async/<! (query! db (build-query raw-query config)))]
      (if (instance? Throwable response)
        (result/exception response)
        (result/success (map #(:model %) response))))))

(defn query-one
  "Runs a query on the database and returns only one model"
  [raw-query config]
  (async/go
    (let [db (config/get-connection config)
          response (async/<! (query! db raw-query))]
      (if (instance? Throwable response)
        (result/exception response)
        (result/success (:model (first response)))))))

(defn get-model
  "Gets a model given its id"
  [model-id config]
  (async/go
    (let [sql (str "select model from " (:table config) " where id = $1")
          db (config/get-connection config)
          response (async/<! (query! db [sql model-id]))]
      (if (instance? Throwable response)
        (result/exception response)
        (result/success (:model (first response)))))))
