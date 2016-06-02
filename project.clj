(defproject clanhr/postgres-gateway "1.9.0"
  :description "ClanHR postgres-gateway"
  :url "https://github.com/clanhr/postgres-gateway"

  :license {:name         "The MIT License"
            :url          "file://LICENSE"
            :distribution :repo
            :comments     "Copyright Selfcare All Rights Reserved."}

  :min-lein-version "2.5.0"


  :dependencies [[environ "1.0.2"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [alaisi/postgres.async "0.7.0"]
                 [cheshire "5.6.1"]
                 [com.stuartsierra/component "0.3.1"]
                 [org.slf4j/slf4j-nop "1.7.21"]
                 [clanhr/result "0.11.0"]
                 [clanhr/analytics "1.9.1"]
                 [postgresql "9.3-1102.jdbc41"]]

  :plugins [[lein-environ "1.0.2"]
            [lein-ancient "0.6.5"]]

  :source-paths ["src"]
  :test-paths ["test"])

