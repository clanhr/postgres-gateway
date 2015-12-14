(ns clanhr.postgres-gateway.query-stream
  "Reads a query as a stream of data"
  (require [postgres.async :refer :all]
           [cheshire.core :as json]
           [clojure.core.async :refer [go <!! >!! chan close!]]
           [clanhr.analytics.errors :as errors]
           [clojure.java.jdbc :as j]
           [environ.core :refer [env]]
           [result.core :as result]))

(defn run
  "Opens a stream to a db query and returns a channel that will receive
  batches of roes"
  [sql]
  (let [db-spec "postgresql://192.168.59.103:5432/postgres?user=postgres&password=wasabi"
        ch (chan 10)
        fetch-size 1000
        db-connection (doto ( j/get-connection db-spec) (.setAutoCommit true))
        statement (j/prepare-statement db-connection
                                       sql
                                       :fetch-size fetch-size
                                       :concurrency :read-only)]
    (future
      (j/query db-connection
               [statement]
               :row-fn (fn [row]
                         (>!! ch row)))
      (close! ch))
    ch))
