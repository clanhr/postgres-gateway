(defproject clanhr/postgres-gateway "1.6.0"
  :description "ClanHR postgres-gateway"
  :url "https://github.com/clanhr/postgres-gateway"

  :license {:name         "The MIT License"
            :url          "file://LICENSE"
            :distribution :repo
            :comments     "Copyright Selfcare All Rights Reserved."}

  :min-lein-version "2.5.0"

  :dependencies [[environ "1.0.1"]
                 [org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/java.jdbc "0.4.2"]
                 [alaisi/postgres.async "0.6.0"]
                 [postgresql "9.3-1102.jdbc41"]
                 [cheshire "5.5.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [clanhr/result "0.10.3"]
                 [clanhr/analytics "1.6.0"]]

  :plugins [[lein-environ "1.0.0"]
            [lein-ancient "0.6.5"]]

  :source-paths ["src"]
  :test-paths ["test"])

