(ns clanhr.postgres-gateway.custom-types
  "Async access utilities to postgres"
  (require [postgres.async :refer :all]
           [clojure.core.async :as async]
           [cheshire.core :as json]
           [result.core :as result]
           [result.core :as result]))

(extend-protocol IPgParameter
  clojure.lang.IPersistentMap
  (to-pg-value [value]
    (.getBytes (json/generate-string value))))

(extend-protocol IPgParameter
  java.util.UUID
  (to-pg-value [uuid]
    (.getBytes (.toString uuid) "UTF-8")))

(defmethod from-pg-value com.github.pgasync.impl.Oid/JSON [oid value]
  (json/parse-string (String. value) true))

(defmethod from-pg-value com.github.pgasync.impl.Oid/UUID [oid value]
  (java.util.UUID/fromString (String. ^bytes value "UTF-8")))
