(ns clanhr.postgres-gateway.config
  "Config settings to connect to PG"
  (require [postgres.async :refer :all]
           [cheshire.core :as json]
           [clojure.core.async :refer [go <!]]
           [clanhr.analytics.errors :as errors]
           [clanhr.postgres-gateway.connection-provider :as connection-provider]
           [environ.core :refer [env]]
           [result.core :as result]))

(def db-pool (atom nil))

(defn- split-query-params
  "Splits somethig like a=1 in {:a 1}"
  [container raw]
  (let [parts (clojure.string/split raw #"=")]
    (assoc container (keyword (first parts)) (last parts))))

(defn jdbc-str-to-map
  "Converts a jdbc string to a map"
  [conn-str]
  (let [parts (re-find #"^jdbc:postgresql://(.+):(\d+)/(.*)\?(.*)" conn-str)
        query-str (nth parts 4)
        raw-query (clojure.string/split query-str #"&")
        query-parts (reduce split-query-params {} raw-query)]
    (merge query-parts {:hostname (nth parts 1)
                        :ssl (= "require" (:sslmode query-parts))
                        :username (:user query-parts)
                        :port (Integer/parseInt (nth parts 2))
                        :database (nth parts 3)})))

(defn- resolve-db-config
  "Gets the database configuration to use"
  [config]
  (cond
    (:db-config config) (:db-config config)
    (:db-conn config) (jdbc-str-to-map ((:db-conn) config))
    :else {:hostname (env :pg-host "localhost")
           :port     (env :pg-port 5432)
           :database (env :pg-database "postgres")
           :username (env :pg-user "postgres")
           :password (env :pg-password "")
           :pool-size 1}))

(defn create-connection
  "Creates a new connection pool"
  ([] (create-connection nil))
  ([config]
   (open-db (resolve-db-config config))))

(defn close-connection!
  "Closes the given connection"
  [conn]
  (close-db! conn))

(defn on-shutdown
  "Shuts down the global connection"
  []
  (println "Shutdown postgres-gateway...")
  (when-let [conn @db-pool]
    (close-connection! conn)))

(defn get-connection
  ([] (get-connection nil))
  ([config]
   (cond
     (connection-provider/valid? config)
       (connection-provider/get-connection config)
     (connection-provider/valid? (:conn config))
       (connection-provider/get-connection (:conn config))
     :else
       (swap! db-pool (fn [pool]
                        (if pool
                           pool
                           (do
                             (create-connection config)
                             (.addShutdownHook (Runtime/getRuntime) (Thread. on-shutdown)))))))))

(defn begin
  [config]
  (let [conn (get-connection config)]
    (begin! conn)))

(defn commit
  [config]
  (let [conn (get-connection config)]
    (commit! conn)))

(defn rollback
  [config]
  (let [conn (get-connection config)]
    (rollback! conn)))

(defn with-transaction!
  "Returns a context in a transaction"
  [context]
  (go
    (let [conn (-> context :pg-conn)
          transaction-conn (<! (begin conn))]
      (assoc context :pg-conn (reify
                                connection-provider/ConnectionProvider
                                (get-connection [this]
                                  transaction-conn))))))

(defn commit-transaction!
  "Commits the transaction on the context"
  [context]
  (commit (:pg-conn context)))

(defn rollback-transaction!
  "Rolls back the transaction on the context"
  [context]
  (rollback (:pg-conn context)))

(defn transaction-run!
  "Runs the given fn in a transaction. Expects f to return a channel
  with a result"
  [context f]
  (go
    (let [context (<! (with-transaction! context))]
      (try
        (let [result (<! (f context))]
          (if (result/succeeded? result)
            (do (<! (commit-transaction! context))
                result)
            (do (<! (rollback-transaction! context))
                result)))
        (catch Exception e
          (<! (rollback-transaction! context))
          (errors/exception e))))))

