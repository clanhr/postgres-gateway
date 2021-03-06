(ns clanhr.postgres-gateway.core
  "Async access utilities to postgres"
  (require [clanhr.postgres-gateway.custom-types]
           [clanhr.postgres-gateway.config :as config]
           [clanhr.postgres-gateway.utils :as utils]
           [clanhr.analytics.metrics :as metrics]
           [postgres.async :refer :all]
           [clojure.core.async :as async]
           [clanhr.analytics.errors :as errors]
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

(defn- get-exception-result
  "Gets an exception to wrap the given sql exception"
  [config ex query]
  (try
    (throw (Exception. "SQL ERROR" ex))
    (catch Exception e
      (errors/exception (ex-info "Error in sql"
                                {:query query
                                 :exception (.getMessage ex)
                                 :fields (:fields config)}
                                e)))))

(defmacro build-result
  "Verifies the response and short-circuit's it if it's an error/exception.
  If it's ok, runs the given forms"
  [config query response & body]
  `(if (instance? Throwable ~response)
     (get-exception-result ~config ~response ~query)
     (result/success (do ~@body))))

(defn- track
  "Tracks a query"
  [config sql elapsed]
  (metrics/postgres-request (:env-name config)
                            (:service-name config)
                            (int elapsed)
                            sql))

(defmacro async-go
  "Wraps core.async/go and handles exceptions and tracks elasped time"
  [config query & body]
  `(async/go
     (try
       (let [start# (. System (nanoTime))
             value# (do ~@body)
             elapsed# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
         (track ~config ~query elapsed#)
         value#)
       (catch Throwable e#
         (errors/exception e# {:query ~query})))))

(defn- upsert!
  "Updates or inserts a model"
  [update? model config]
  (let [db (config/get-connection config)
        uuid (:_id model)
        fields (build-fields model config)
        sql-spec {:table (:table config) :returning "id"}]
      (if update?
        (update! db (assoc sql-spec :where ["id = $1" uuid]) fields)
        (insert! db sql-spec fields))))

(defn save-model!
  "Saves a model to the datastore"
  [model config]
  (let [table-name (:table config)
        sql (str "upsert " table-name)]
    (async-go config sql
      (let [to-update? (and (:_id model) (not (:insert config)))
            model-with-id (idify model)
            response (async/<! (upsert! to-update? model-with-id config))]
        (if (or (instance? Throwable response) (= 1 (:updated response)))
          (build-result config sql response model-with-id)
          (if (get-in config [:save-options :insert-if-not-found])
            (let [response (async/<! (upsert! false model-with-id config))]
              (build-result config sql response model-with-id))
            (result/failure (str "Model " table-name " with id '" (:_id model) "' not updated (maybe not found?)"))))))))

(defn save-model-with-id!
  "Saves a model with id to the datastore"
  [model config]
  (save-model! model (assoc config :save-options {:insert-if-not-found true})))

(defn- upsert-data!
  "Updates or inserts data"
  [update? fields config]
  (let [db (config/get-connection config)
        sql-spec {:table (:table config) :returning "id"}
        pk-query (or (:pk-query config) ["id = $1" (:id fields)])]
    (if update?
      (update! db (assoc sql-spec :where pk-query) fields)
      (insert! db sql-spec fields))))

(defn save-data!
  "Saves arbitrary data"
  [data config]
  (let [table-name (:table config)
        sql (str "upsert " table-name)]
    (async-go config sql
      (let [response (async/<! (upsert-data! true data config))]
        (if (or (instance? Throwable response) (= 1 (:updated response)))
          (build-result config sql response data)
          (let [response (async/<! (upsert-data! false data config))]
            (build-result config sql response data)))))))

(defn- prepare-fields-fn
  "If a fields-fn function is provided, it will be called per field
  to resolve the fields for the model"
  [config model]
  (if-let [fields-fn (:fields-fn config)]
    (fields-fn model)))

(defn- model-save-chan
  "Prepares and persists a model"
  [config model]
  (if-let [new-fields (prepare-fields-fn config model)]
    (save-model! model (assoc config :fields new-fields))
    (save-model! model config)))

(defn bulk-save-models!
  "Saves a collection for models.
  TODO: fully async and in a transaction"
  [models config]
  (async-go config (str "bulk upsert " (:table config))
    (let [chans (map (partial model-save-chan config) models)]
      (mapv (fn [chan] (async/<!! chan)) chans))))

(defn- build-query
  "Builds/edits the query with extra information"
  [query config]
  (-> query
      (utils/add-page-logic config)
      (utils/add-in-logic config)))

(defn query
  "Runs a query on the database"
  [raw-query config]
  (let [raw-query (build-query raw-query config)]
    (async-go config (first raw-query)
      (let [db (config/get-connection config)
            response (async/<! (query! db raw-query))]
        (build-result config raw-query response (map #(:model %) response))))))

(defn query-data
  "Runs a query on the database and returns raw data"
  [raw-query config]
  (let [raw-query (build-query raw-query config)]
    (async-go config (first raw-query)
      (let [db (config/get-connection config)
            response (async/<! (query! db raw-query))]
        (build-result config raw-query response response)))))

(defn count-models
  "Utility around count"
  [raw-query config]
  (async-go config (first raw-query)
    (let [db (config/get-connection config)
          response (async/<! (query! db (build-query raw-query config)))]
      (build-result config raw-query response (:count (first response))))))

(defn query-return-number-of-affected-rows
  [raw-query config]
  (async-go config (first raw-query)
    (let [db (config/get-connection config)
          response (async/<! (query! db raw-query))]
      (build-result config raw-query response (:count (first response))))))

(defn delete-models
  "Utility around delete"
  [raw-query config]
  (query-return-number-of-affected-rows raw-query config))

(defn update-models
  "Utility around update"
  [raw-query config]
  (query-return-number-of-affected-rows raw-query config))

(defn query-one
  "Runs a query on the database and returns only one model"
  [raw-query config]
  (async-go config (first raw-query)
    (let [db (config/get-connection config)
          response (async/<! (query! db raw-query))]
      (if (or (instance? Throwable response) (= 1 (count response)))
        (build-result config raw-query response (:model (first response)))
        (result/failure {:data (str "Expected 1 result, but got " (count response))
                         :query raw-query})))))

(defn get-model
  "Gets a model given its id"
  [model-id config]
  (let [table-name (:table config)
        sql (str "select model from " table-name " where id = $1")]
    (async-go config sql
      (let [db (config/get-connection config)
            response (async/<! (query! db [sql model-id]))]
        (if (or (instance? Throwable response) (= 1 (count response)))
          (build-result config sql response (:model (first response)))
          (result/failure (str "Can't find " table-name " with id '" model-id "'")))))))
