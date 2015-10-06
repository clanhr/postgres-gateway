(ns clanhr.postgres-gateway.connection-provider
  "Protocol for objects that provide connections")

(defprotocol ConnectionProvider
  (get-connection [this] "Gets a working connection"))

(defn valid?
  "True if the given object is a connection-provider"
  [obj]
  (satisfies? ConnectionProvider obj))
