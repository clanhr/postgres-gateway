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
  (is (= "{\"waza\"}" (utils/array-column-value "waza")))
  (is (= "{\"waza\",\"bi\"}" (utils/array-column-value ["waza" "bi"])))
  (is (= "{\"Kw, Lda.\",\"bi\"}" (utils/array-column-value ["Kw, Lda." "bi"])))
  (is (= "{\"wa,za\",\"wa,bi\"}" (utils/array-column-value ["wa,za" "wa,bi"])))
  (is (= "{\"wa, lda\",\"we,inc\",\"bi\"}" (utils/array-column-value "wa, lda§we,inc§bi")))
  (is (= "{\"wa, lda\",\"bi\",\"wa,inc\"}" (utils/array-column-value "wa, lda§bi§wa,inc")))
  (is (= "{\"wa, lda\"}" (utils/array-column-value "wa, lda§")))
  (is (= "{}" (utils/array-column-value "§"))))

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

(deftest split-string
  (is (= ["tag1", "tag2"] (#'utils/split-string "tag1,tag2")))
  (is (= ["tag1", "tag2"] (#'utils/split-string "tag1§tag2")))
  (is (= ["tag1, Lda.", "tag2"] (#'utils/split-string "tag1, Lda.§tag2"))))

(deftest quote-string
  (is (= "\"tag1\"" (#'utils/quote-string "tag1")))
  (is (= "\"tag1\"" (#'utils/quote-string "\"tag1\""))))
