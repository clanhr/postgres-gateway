(ns clanhr.postgres-gateway.component
  "Provides the gateway config as a component"
  (require [clanhr.postgres-gateway.config :as config]
           [clanhr.postgres-gateway.connection-provider :as connection-provider]
           [com.stuartsierra.component :as component]))

(defrecord PostgresGatewayComponent [config conn async-close?]
  component/Lifecycle

  (start [this]
    (if conn
      this
      (do
        (assoc this :conn (config/create-connection config)))))

  (stop [this]
    (if conn
      (do
        (if async-close?
          (future (config/close-connection! conn))
          (config/close-connection! conn))
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
   (create config false))
  ([config async-close?]
   (map->PostgresGatewayComponent {:async-close? async-close?
                                   :config {:db-config config}})))

