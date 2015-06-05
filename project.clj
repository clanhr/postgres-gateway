(defproject clanhr/postgres-gateway "0.0.1"
  :description "ClanHR postgres-gateway"
  :url "https://github.com/clanhr/postgres-gateway"

  :license {:name         "The MIT License"
            :url          "file://LICENSE"
            :distribution :repo
            :comments     "Copyright Selfcare All Rights Reserved."}

  :min-lein-version "2.5.0"

  :dependencies [[environ "1.0.0"]
                 [org.clojure/clojure "1.7.0-RC1"]
                 [alaisi/postgres.async "0.5.0"]
                 [org.clojure/java.jdbc "0.3.2"]
                 [postgresql "9.1-901.jdbc4"]
                 [cheshire "5.5.0"]
                 [clanhr/result "0.6.0"]
                 [clanhr/analytics "0.5.0"]]

  :plugins [[lein-environ "1.0.0"]
            [lein-ancient "0.6.5"]]

  :source-paths ["src"]
  :test-paths ["test"])

