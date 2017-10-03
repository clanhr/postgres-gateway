(ns clanhr.postgres-gateway.utils
  "Utilities for handling PG"
  (require [postgres.async :refer :all]
           [clojure.core.async :as async]
           [cheshire.core :as json]
           [result.core :as result]
           [result.core :as result]))

(defn like-value
  "Transforms into a postgres like value"
  [raw]
  (if raw
    (str "%" (clojure.string/replace raw " " "%") "%")
    ""))

(defn in-str-coll-value
  "Transforms into a postgres in collection value"
  [coll]
  (if (coll? coll)
    (let [quoted (map #(str "'" % "'") coll)]
      (clojure.string/join "," quoted))
    (str "'"coll"'")))

(defn convert-int
  "Converts the value to int, if needed"
  [raw]
  (if (string? raw)
    (Integer/parseInt raw)
    raw))

(defn add-page-logic
  "Injects paging limits"
  [query config]
  (let [sql (first query)
        page (convert-int (:page config))
        per-page (convert-int (:per-page config))]
    (if page
      (concat [(str sql
                    " OFFSET " (* (- page 1) per-page)
                    " LIMIT " (or per-page 10))]
              (rest query))
      query)))

(defn add-in-logic
  "Injects in logic"
  [query config]
  (if-let [param-num (:in-param config)]
    (let [idx param-num
          elems (nth query idx)
          params (map #(str "$"%) (range param-num (+ param-num (count elems))))
          sql (-> (first query)
                  (clojure.string/replace (str "$" param-num) (clojure.string/join "," params)))
          query-with-sql (-> query
                             (assoc 0 sql))]
      (into [] (flatten query-with-sql)))
    query))

(defn- change-keys-case
  "Change the case of the keys"
  [model from-char to-char]
  (when model
    (reduce-kv (fn [m k v]
                (assoc m (keyword (clojure.string/replace (name k) from-char to-char)) v))
      {}
      model)))

(defn ->snake-case-keys
  "Transforms lisp case keys with - in database keys with _"
  [model]
  (change-keys-case model "-" "_"))

(defn ->lisp-case-keys
  "Transforms database keys with _ in clojure keys with -"
  [model]
  (change-keys-case model "_" "-"))

(defn- quote-string
  "Encase a string in quotes, if not already"
  [string]
  (if (clojure.string/starts-with? string "\"")
    string
    (str "\"" string "\"")))

(defn- split-string
  "Splits a string either by section sign (§) or comma (,)"
  [string]
  (if (.contains string "§")
    (clojure.string/split string #"§")
    (clojure.string/split string #",")))

(defn- enclose-string-in-braces
  "Given a string encloses it in curly braces"
  [string]
  (str "{" string "}")) 

(defn- generate-postgres-array
  "Generates a postgres array from a csv string or a vector"
  [input]
  (->>
    (if (= true (coll? input))
      (identity input)
      (split-string input))
    (map quote-string)
    (clojure.string/join ",")
    (enclose-string-in-braces)))

(defn array-column-value
  "Transforms into postgres array column format a sequence of values"
  [coll]
  (cond
    (nil? coll) nil
    :else (generate-postgres-array coll)))
