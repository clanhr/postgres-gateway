(ns clanhr.postgres-gateway.core
  "Async access utilities to postgres"
  (require [clanhr.postgres-gateway.custom-types]
           [postgres.async :refer :all]
           [clojure.core.async :as async]
           [cheshire.core :as json]
           [result.core :as result]
           [result.core :as result]))

(def ^:private default-config {:timeout 1000})

(defn- split-query-params
  "Splits somethig like a=1 in {:a 1}"
  [container raw]
  (let [parts (clojure.string/split raw #"=")]
    (assoc container (keyword (first parts)) (last parts))))

(defn jdbc-str-to-map
  "Converts a jdbc string to a map"
  []
  #_(let [parts (re-find #"^jdbc:postgresql://(.+):(\d+)/(.*)\?(.*)" (conn-str))
        query-str (nth parts 4)
        raw-query (clojure.string/split query-str #"&")
        query-parts (reduce split-query-params {} raw-query)]
    (merge query-parts {:hostname (nth parts 1)
                        :username (:user query-parts)
                        :port (Integer/parseInt (nth parts 2))
                        :database (nth parts 3)}))

  )

(def ^:private db-pool (atom nil))

(defn- get-connection
  ([] (get-connection nil))
  ([config]
   (swap! db-pool (fn [pool]
                    (if pool
                       pool
                       (open-db (or (:db-config config) (jdbc-str-to-map))))))))

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
  (let [db (get-connection config)
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

(defn get-model
  "Gets a model given its id"
  [model-id config]
  (async/go
    (let [sql (str "select model from " (:table config) " where id = $1 ")
          db (get-connection config)
          response (async/<! (query! db [sql model-id]))]
      (if (instance? Throwable response)
        (result/exception response)
        (result/success (:model (first response)))))))
