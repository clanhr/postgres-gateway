(ns clanhr.postgres-gateway.component
  "Provides the gateway config as a component"
  (require [clanhr.postgres-gateway.config :as config]
           [clanhr.postgres-gateway.connection-provider :as connection-provider]
           [com.stuartsierra.component :as component]))

(defrecord PostgresGatewayComponent [config conn]
  component/Lifecycle

  (start [this]
    (if conn
      this
      (do
        (println "** Starting PostgresGatewayComponent")
        (assoc this :conn (config/create-connection config)))))

  (stop [this]
    (if conn
      (do
        (println "** Stopping PostgresGatewayComponent")
        (config/close-connection! conn)
        (dissoc this :conn))
      this))

  connection-provider/ConnectionProvider

  (get-connection [this]
    (:conn this)))

(defn create
  "Creates a new component with a new connection pool"
  ([]
   (create nil))
  ([config]
   (map->PostgresGatewayComponent {:config config})))

