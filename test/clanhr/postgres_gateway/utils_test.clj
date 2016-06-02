(ns clanhr.postgres-gateway.utils-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!! go]]
            [clanhr.postgres-gateway.core :as core]
            [clanhr.postgres-gateway.config :as config]
            [clanhr.postgres-gateway.utils :as utils]
            [postgres.async :refer :all]
            [environ.core :refer [env]]
            [result.core :as result]))

(deftest array-column-types
  (is (nil? (utils/array-column-value nil)))
  (is (= "{waza}" (utils/array-column-value "waza")))
  (is (= "{waza,bi}" (utils/array-column-value ["waza" "bi"]))))

(deftest like-value
  (is (= "" (utils/like-value nil)))
  (is (= "%waza%waza%" (utils/like-value "waza waza")))
  (is (= "%waza%" (utils/like-value "waza"))))

(deftest in-coll-value
  (is (= "'waza'" (utils/in-str-coll-value "waza")))
  (is (= "'waza','bi'" (utils/in-str-coll-value ["waza" "bi"]))))

(deftest add-in-logic
  (is (= (utils/add-in-logic ["name in ($1)" ["a" "b"]] {:in-param 1})
         ["name in ($1,$2)" "a" "b"])))

(deftest snake-case-keys
  (is (= {:some_key 1} (utils/->snake-case-keys {:some-key 1}))))

(deftest lisp-case-keys
  (is (= {:some-key 1} (utils/->lisp-case-keys {:some_key 1}))))
