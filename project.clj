(defproject clanhr/postgres-gateway "0.9.4"
  :description "ClanHR postgres-gateway"
  :url "https://github.com/clanhr/postgres-gateway"

  :license {:name         "The MIT License"
            :url          "file://LICENSE"
            :distribution :repo
            :comments     "Copyright Selfcare All Rights Reserved."}

  :min-lein-version "2.5.0"

  :dependencies [[environ "1.0.0"]
                 [org.clojure/clojure "1.7.0-RC1"]
                 [alaisi/postgres.async "0.6.0"]
                 [cheshire "5.5.0"]
                 [clanhr/result "0.6.0"]
                 [clanhr/analytics "1.1.0"]]

  :plugins [[lein-environ "1.0.0"]
            [lein-ancient "0.6.5"]]

  :source-paths ["src"]
  :test-paths ["test"])

