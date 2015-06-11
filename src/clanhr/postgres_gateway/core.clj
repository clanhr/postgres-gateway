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

(defmacro build-result
  "Verifies the response and short-circuit's it if it's an error/exception.
  If it's ok, runs the given forms"
  [response & body]
  `(if (instance? Throwable ~response)
    (result/exception ~response)
    (result/success (do ~@body))))

(defn- track
  "Tracks a query"
  [config sql elapsed]
  (println (str "PG[" (:service-name config) "] " (int elapsed) "ms - " sql)))

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
         (result/exception e#)))))

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
  (async-go config (str "upsert " (:table config))
    (let [to-update? (:_id model)
          model-with-id (idify model)
          response (async/<! (upsert! to-update? model-with-id config))]
      (if (or (instance? Throwable response) (= 1 (:updated response)))
        (build-result response model-with-id)
        (if (get-in config [:save-options :insert-if-not-found])
          (let [response (async/<! (upsert! false model-with-id config))]
            (build-result response  model-with-id))
          (result/failure "Model not updated (maybe not found?)"))))))

(defn- model-save-chan
  "Prepares and persists a model"
  [config model]
  (let [new-fields (reduce (fn [obj field]
                             (assoc obj field (field model)))
                           {}
                           (:fields config))]
    (save-model! model (assoc config :fields new-fields))))

(defn bulk-save-models!
  "Saves a collection for models.
  TODO: fully async and in a transaction"
  [models config]
  (async-go config (str "bulk upsert " (:table config))
    (let [chans (map (partial model-save-chan config) models)]
      (mapv (fn [chan] (async/<!! chan)) chans))))

(defn convert-int
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
  (let [raw-query (build-query raw-query config)]
    (async-go config (first raw-query)
      (let [db (config/get-connection config)
            response (async/<! (query! db raw-query))]
        (build-result response (map #(:model %) response))))))

(defn count-models
  "Utility around count"
  [raw-query config]
  (async-go config (first raw-query)
    (let [db (config/get-connection config)
          response (async/<! (query! db (build-query raw-query config)))]
      (build-result response (:count (first response))))))

(defn delete-models
  "Utility around delete"
  [raw-query config]
  (async-go config (first raw-query)
    (let [db (config/get-connection config)
          response (async/<! (query! db raw-query))]
      (build-result response (:count (first response))))))

(defn query-one
  "Runs a query on the database and returns only one model"
  [raw-query config]
  (async-go config (first raw-query)
    (let [db (config/get-connection config)
          response (async/<! (query! db raw-query))]
      (if (or (instance? Throwable response) (= 1 (count response)))
        (build-result response (:model (first response)))
        (result/failure "Not found")))))

(defn get-model
  "Gets a model given its id"
  [model-id config]
  (let [sql (str "select model from " (:table config) " where id = $1")]
    (async-go config sql
      (let [db (config/get-connection config)
            response (async/<! (query! db [sql model-id]))]
        (if (or (instance? Throwable response) (= 1 (count response)))
          (build-result response (:model (first response)))
          (result/failure "Not found"))))))
