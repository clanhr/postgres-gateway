(ns clanhr.postgres-gateway.query-stream
  "Reads a query as a stream of data"
  (require [postgres.async :refer :all]
           [cheshire.core :as json]
           [clojure.core.async :refer [go <!! >!! chan close!]]
           [clanhr.postgres-gateway.config :as config]
           [clanhr.analytics.errors :as errors]
           [clojure.java.jdbc :as j]
           [environ.core :refer [env]]
           [result.core :as result]))

(defn run
  "Opens a stream to a db query and returns a channel that will receive
  all the rows, one by one"
  ([sql]
   (run sql nil))
  ([sql config]
   (let [db-spec (config/db-config-map config)
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
                :row-fn (fn [row] (>!! ch row)))
       (close! ch))
     ch)))
