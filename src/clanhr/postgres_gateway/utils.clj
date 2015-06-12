(ns clanhr.postgres-gateway.utils
  "Utilities for handling PG"
  (require [postgres.async :refer :all]
           [clojure.core.async :as async]
           [cheshire.core :as json]
           [result.core :as result]
           [result.core :as result]))

(defn array-column-value
  "Transforms into postgres array column format a sequence of values"
  [coll]
  (cond
    (nil? coll) nil
    (coll? coll) (str "{" (clojure.string/join "," coll)  "}")
    :else (str "{" coll "}")))

(defn like-value
  "Transforms into a postgres like value"
  [raw]
  (if raw
    (str "%" raw "%")
    ""))

(defn in-str-coll-value
  "Transforms into a postgres in collection value"
  [coll]
  (if (coll? coll)
    (let [quoted (map #(str "'" % "'") coll)]
      (clojure.string/join "," quoted))
    (str "'"coll"'")))
