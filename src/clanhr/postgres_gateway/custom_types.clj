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

(defmethod from-pg-value com.github.pgasync.impl.Oid/JSON [oid value]
  (json/parse-string (String. value) true))

(defmethod from-pg-value com.github.pgasync.impl.Oid/JSONB [oid value]
  (json/parse-string (String. value) true))

(add-encoder
  org.joda.time.DateTime
  (fn [data jsonGenerator]
    (.writeString jsonGenerator (coerce/to-string data))))
