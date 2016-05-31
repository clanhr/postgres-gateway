(ns clanhr.postgres-gateway.custom-types
  "Async access utilities to postgres"
  (require [postgres.async :refer :all]
           [clojure.core.async :as async]
           [cheshire.core :as json]
           [cheshire.generate :refer [add-encoder encode-str]]
           [clj-time.coerce :as coerce]
           [result.core :as result]
           [result.core :as result]))

(add-encoder
  org.joda.time.DateTime
  (fn [data jsonGenerator]
    (.writeString jsonGenerator (coerce/to-string data))))

(extend-protocol IPgParameter
  clojure.lang.IPersistentMap
  (to-pg-value [value]
    (.getBytes (json/generate-string value)))
  org.joda.time.DateTime
  (to-pg-value [value]
    (.getBytes (coerce/to-string value))))

(defmethod from-pg-value com.github.pgasync.impl.Oid/JSON [oid value]
  (json/parse-string (String. value) true))

(defmethod from-pg-value com.github.pgasync.impl.Oid/JSONB [oid value]
  (json/parse-string (String. value) true))

(defmethod from-pg-value com.github.pgasync.impl.Oid/DATE [oid value]
  (coerce/from-string (String. value)))

(defmethod from-pg-value com.github.pgasync.impl.Oid/NUMERIC [oid value]
  (Double/parseDouble (String. value)))
