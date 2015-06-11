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
